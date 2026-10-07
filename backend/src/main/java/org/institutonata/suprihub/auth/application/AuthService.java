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
    private final LoginThrottle throttle;
    private final SessionRevocations revocations;
    private final SupriHubProperties.Jwt jwtConfig;
    private final Clock clock;
    /** Hash válido usado para igualar o tempo de resposta quando o e-mail não existe. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
            VerificationTokenRepository verificationTokens, PasswordEncoder passwordEncoder, JwtService jwtService,
            DomainEvents events, AuditService audit, CaptchaVerifier captcha, LoginThrottle throttle,
            SessionRevocations revocations, SupriHubProperties props, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.events = events;
        this.audit = audit;
        this.captcha = captcha;
        this.throttle = throttle;
        this.revocations = revocations;
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
        // Bloqueio por IP + e-mail: mesma resposta exista a conta ou não.
        if (throttle.isBlocked(email, remoteIp, now)) {
            throw new BusinessException("ACCOUNT_LOCKED",
                    "Muitas tentativas sem sucesso. Aguarde 15 minutos e tente novamente.", HttpStatus.LOCKED);
        }
        AppUser user = users.findByEmail(email.trim()).orElse(null);
        // Conta bloqueada (tentativas vindas de vários IPs): a senha nem é conferida e a resposta é
        // a mesma de senha errada, para não revelar a conta nem confirmar um palpite certo.
        if (user == null || user.isLocked(now)) {
            passwordEncoder.matches(password, dummyHash);
            throttle.registerFailure(email, remoteIp, now);
            throw new BadCredentialsException("invalid");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(now);
            throttle.registerFailure(email, remoteIp, now);
            throw new BadCredentialsException("invalid");
        }
        throttle.reset(email, remoteIp);
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
        RefreshToken current = refreshTokens.findByTokenHashForUpdate(TokenHasher.sha256(rawRefreshToken))
                .orElseThrow(this::sessionExpired);
        if (current.isRotated()) {
            if (current.getRevokedAt().plus(jwtConfig.refreshReuseGrace()).isAfter(now)) {
                // Rotacionado há instantes: duas abas renovando ao mesmo tempo com o mesmo cookie.
                // Emite outra sessão em vez de tratar como roubo.
                return newSession(activeUser(current.getUserId(), now), now).session();
            }
            // Token já rotacionado foi reapresentado: possível roubo. Encerra todas as sessões.
            log.warn("Reuso de refresh token detectado para usuário #{}", current.getUserId());
            endAllSessions(current.getUserId(), now);
            throw sessionExpired();
        }
        if (!current.isActive(now)) {
            throw sessionExpired();
        }
        AppUser user = activeUser(current.getUserId(), now);
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
        revocations.revoke(user, now);
        audit.record("app_user", user.getId(), "PASSWORD_RESET", null, null);
    }

    private AppUser activeUser(Long userId, Instant now) {
        AppUser user = users.findById(userId).orElseThrow(this::sessionExpired);
        if (!user.isEnabled()) {
            refreshTokens.revokeAllForUser(user.getId(), now);
            throw sessionExpired();
        }
        return user;
    }

    /** Encerra refresh tokens e access tokens já emitidos. */
    private void endAllSessions(Long userId, Instant now) {
        refreshTokens.revokeAllForUser(userId, now);
        users.findById(userId).ifPresent(u -> revocations.revoke(u, now));
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
