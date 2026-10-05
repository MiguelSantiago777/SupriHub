package org.institutonata.suprihub.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.domain.AuthEvents;
import org.institutonata.suprihub.auth.domain.RefreshToken;
import org.institutonata.suprihub.auth.domain.VerificationToken;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.auth.infra.RefreshTokenRepository;
import org.institutonata.suprihub.auth.infra.RoleRepository;
import org.institutonata.suprihub.auth.infra.VerificationTokenRepository;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.security.CaptchaVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro, confirmação de e-mail, login, refresh (com rotação e detecção de reuso),
 * logout e redefinição de senha. Os e-mails são enviados de forma assíncrona via
 * eventos de domínio (outbox → RabbitMQ → módulo de notificação).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final VerificationTokenRepository verificationTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEvents events;
    private final AuditService audit;
    private final CaptchaVerifier captcha;
    private final SupriHubProperties.Jwt jwtConfig;
    private final Clock clock;
    /** Hash válido usado para igualar o tempo de resposta quando o e-mail não existe. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
            VerificationTokenRepository verificationTokens, PasswordEncoder passwordEncoder, JwtService jwtService,
            DomainEvents events, AuditService audit, CaptchaVerifier captcha, SupriHubProperties props, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.events = events;
        this.audit = audit;
        this.captcha = captcha;
        this.jwtConfig = props.jwt();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(TokenHasher.newToken());
    }

    public record Session(AppUser user, JwtService.AccessToken accessToken, String refreshToken) {
    }

    /**
     * Autocadastro de fornecedor. Resposta sempre igual (não revela se o e-mail existe):
     * se já existir, o titular recebe um aviso por e-mail.
     */
    @Transactional
    public void register(String email, String password, String fullName, String captchaToken, String remoteIp) {
        checkCaptcha(captchaToken, remoteIp);
        Instant now = clock.instant();
        Optional<AppUser> existing = users.findByEmail(email.trim());
        if (existing.isPresent()) {
            events.publish(event(AuthEvents.REGISTRATION_DUPLICATE, existing.get().getId()));
            return;
        }
        AppUser user = new AppUser(email, passwordEncoder.encode(password), fullName, now);
        user.replaceRoles(roles.findByCodeIn(Set.of(AppRole.SUPPLIER)), now);
        users.save(user);
        audit.record("app_user", user.getId(), "REGISTER", null, Map.of("roles", user.roleCodes()));
        events.publish(event(AuthEvents.USER_REGISTERED, user.getId()));
    }

    @Transactional
    public void confirmEmail(String rawToken) {
        VerificationToken token = usableToken(rawToken, VerificationToken.Purpose.EMAIL_CONFIRMATION);
        Instant now = clock.instant();
        AppUser user = users.findById(token.getUserId()).orElseThrow(this::invalidLink);
        token.markUsed(now);
        if (!user.isEmailVerified()) {
            user.confirmEmail(now);
            audit.record("app_user", user.getId(), "EMAIL_CONFIRMED", null, null);
            events.publish(event(AuthEvents.EMAIL_CONFIRMED, user.getId()));
        }
    }

    @Transactional
    public void resendConfirmation(String email) {
        users.findByEmail(email.trim())
                .filter(u -> !u.isEmailVerified() && u.isEnabled())
                .ifPresent(u -> events.publish(event(AuthEvents.EMAIL_CONFIRMATION_REQUESTED, u.getId())));
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, BusinessException.class})
    public Session login(String email, String password, String captchaToken, String remoteIp) {
        checkCaptcha(captchaToken, remoteIp);
        Instant now = clock.instant();
        AppUser user = users.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            throw new BadCredentialsException("invalid");
        }
        if (user.isLocked(now)) {
            throw new BusinessException("ACCOUNT_LOCKED",
                    "Muitas tentativas sem sucesso. Aguarde 15 minutos e tente novamente.", HttpStatus.LOCKED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(now);
            throw new BadCredentialsException("invalid");
        }
        if (!user.isEnabled()) {
            throw new BusinessException("ACCOUNT_DISABLED",
                    "Sua conta está desativada. Fale com a Comissão do Contrata + NATA.", HttpStatus.FORBIDDEN);
        }
        if (!user.isEmailVerified()) {
            throw new BusinessException("EMAIL_NOT_VERIFIED",
                    "Confirme seu e-mail pelo link que enviamos antes de entrar.", HttpStatus.FORBIDDEN);
        }
        user.registerSuccessfulLogin(now);
        return newSession(user, now).session();
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Session refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw sessionExpired();
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .orElseThrow(this::sessionExpired);
        if (current.getRevokedAt() != null && current.getReplacedBy() != null) {
            // Token já rotacionado foi reapresentado: possível roubo. Encerra todas as sessões.
            log.warn("Reuso de refresh token detectado para usuário #{}", current.getUserId());
            refreshTokens.revokeAllForUser(current.getUserId(), now);
            throw sessionExpired();
        }
        if (!current.isActive(now)) {
            throw sessionExpired();
        }
        AppUser user = users.findById(current.getUserId()).orElseThrow(this::sessionExpired);
        if (!user.isEnabled()) {
            refreshTokens.revokeAllForUser(user.getId(), now);
            throw sessionExpired();
        }
        IssuedSession issued = newSession(user, now);
        current.revoke(now, issued.refreshTokenId());
        return issued.session();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .ifPresent(t -> t.revoke(clock.instant(), null));
    }

    @Transactional
    public void requestPasswordReset(String email, String captchaToken, String remoteIp) {
        checkCaptcha(captchaToken, remoteIp);
        users.findByEmail(email.trim()).filter(AppUser::isEnabled)
                .ifPresent(u -> events.publish(event(AuthEvents.PASSWORD_RESET_REQUESTED, u.getId())));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        VerificationToken token = usableToken(rawToken, VerificationToken.Purpose.PASSWORD_RESET);
        Instant now = clock.instant();
        AppUser user = users.findById(token.getUserId()).orElseThrow(this::invalidLink);
        token.markUsed(now);
        user.changePassword(passwordEncoder.encode(newPassword), now);
        // Definir senha pelo link prova a posse do e-mail.
        user.confirmEmail(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        audit.record("app_user", user.getId(), "PASSWORD_RESET", null, null);
    }

    private record IssuedSession(Session session, Long refreshTokenId) {
    }

    private IssuedSession newSession(AppUser user, Instant now) {
        String raw = TokenHasher.newToken();
        RefreshToken saved = refreshTokens.save(new RefreshToken(user.getId(), TokenHasher.sha256(raw),
                now.plus(jwtConfig.refreshTtl()), now));
        return new IssuedSession(new Session(user, jwtService.issue(user), raw), saved.getId());
    }

    private VerificationToken usableToken(String rawToken, VerificationToken.Purpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidLink();
        }
        VerificationToken token = verificationTokens.findByTokenHash(TokenHasher.sha256(rawToken))
                .filter(t -> t.getPurpose() == purpose)
                .orElseThrow(this::invalidLink);
        if (!token.isUsable(clock.instant())) {
            throw invalidLink();
        }
        return token;
    }

    private void checkCaptcha(String token, String ip) {
        if (!captcha.verify(token, ip)) {
            throw new BusinessException("CAPTCHA_FAILED", "Não foi possível validar a verificação de segurança.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private DomainEvent event(String type, Long userId) {
        return DomainEvent.of(type, AuthEvents.AGGREGATE, userId, Map.of("userId", userId), clock);
    }

    private BusinessException invalidLink() {
        return new BusinessException("INVALID_LINK",
                "Este link é inválido ou expirou. Solicite um novo.", HttpStatus.BAD_REQUEST);
    }

    private BusinessException sessionExpired() {
        return new BusinessException("SESSION_EXPIRED", "Sua sessão expirou. Entre novamente.",
                HttpStatus.UNAUTHORIZED);
    }
}
