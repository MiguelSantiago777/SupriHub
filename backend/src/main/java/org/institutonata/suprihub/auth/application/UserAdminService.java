package org.institutonata.suprihub.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.domain.AuthEvents;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.auth.infra.RefreshTokenRepository;
import org.institutonata.suprihub.auth.infra.RoleRepository;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de usuários pelo ADMIN: convite de usuários internos, perfis e ativação. */
@Service
public class UserAdminService {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final SessionRevocations revocations;
    private final PasswordEncoder passwordEncoder;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;

    public UserAdminService(AppUserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
            SessionRevocations revocations, PasswordEncoder passwordEncoder, DomainEvents events, AuditService audit,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.revocations = revocations;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AppUser> search(AppRole role, String q, Pageable pageable) {
        String term = q == null ? "" : q.trim();
        return role == null ? users.search(term, pageable) : users.searchByRole(role, term, pageable);
    }

    @Transactional(readOnly = true)
    public AppUser get(long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Usuário"));
    }

    /**
     * Cria usuário interno com senha aleatória inutilizável; ele recebe por e-mail um link
     * para definir a própria senha (o ADMIN nunca conhece a senha).
     */
    @Transactional
    public AppUser invite(String email, String fullName, Set<AppRole> roleCodes) {
        if (users.findByEmail(email.trim()).isPresent()) {
            throw new BusinessException("EMAIL_IN_USE", "Já existe um usuário com este e-mail.", HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        AppUser user = new AppUser(email, passwordEncoder.encode(TokenHasher.newToken()), fullName, now);
        user.replaceRoles(roles.findByCodeIn(roleCodes), now);
        users.save(user);
        audit.record("app_user", user.getId(), "INVITE", null, Map.of("roles", user.roleCodes()));
        events.publish(DomainEvent.of(AuthEvents.USER_INVITED, AuthEvents.AGGREGATE, user.getId(),
                Map.of("userId", user.getId()), clock));
        return user;
    }

    @Transactional
    public AppUser update(long id, String fullName, Set<AppRole> roleCodes, boolean enabled, long version) {
        AppUser user = get(id);
        if (user.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Este usuário foi alterado por outra pessoa. Recarregue a página.", HttpStatus.CONFLICT);
        }
        if (CurrentUser.id().filter(me -> me == id).isPresent()
                && (!enabled || !roleCodes.contains(AppRole.ADMIN))) {
            throw new BusinessException("SELF_LOCKOUT",
                    "Você não pode remover o próprio perfil de administrador nem desativar a própria conta.");
        }
        Map<String, Object> before = Map.of("fullName", user.getFullName(), "roles", user.roleCodes(),
                "enabled", user.isEnabled());
        Instant now = clock.instant();
        boolean rolesChanged = !user.roleCodes().equals(roleCodes);
        user.rename(fullName, now);
        user.replaceRoles(roles.findByCodeIn(roleCodes), now);
        user.setEnabled(enabled, now);
        if (!enabled) {
            refreshTokens.revokeAllForUser(id, now);
        }
        // O access token carrega os perfis: recusa os já emitidos. Se a conta continua ativa,
        // o frontend renova a sessão e recebe um token com os perfis novos.
        if (!enabled || rolesChanged) {
            revocations.revoke(user, now);
        }
        audit.record("app_user", id, "UPDATE", before, Map.of("fullName", user.getFullName(),
                "roles", user.roleCodes(), "enabled", enabled));
        return user;
    }
}
