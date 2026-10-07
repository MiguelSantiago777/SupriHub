package org.institutonata.suprihub.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.stereotype.Service;

/**
 * Revogação de access tokens. O JWT é stateless, então guardamos, por usuário, o instante a
 * partir do qual os tokens anteriores deixam de valer. Só interessam revogações mais novas
 * que a duração do access token; o mapa fica pequeno e é recarregado do banco na subida.
 * Em memória (por instância); para vários nós, trocar por cache distribuído.
 */
@Service
public class SessionRevocations {

    private final AppUserRepository users;
    private final Duration accessTtl;
    private final Clock clock;
    private final Map<Long, Instant> revokedAt = new ConcurrentHashMap<>();

    public SessionRevocations(AppUserRepository users, SupriHubProperties props, Clock clock) {
        this.users = users;
        this.accessTtl = props.jwt().accessTtl();
        this.clock = clock;
    }

    @PostConstruct
    void load() {
        for (Object[] row : users.findSessionRevocationsSince(horizon())) {
            revokedAt.put((Long) row[0], (Instant) row[1]);
        }
    }

    /** Marca no usuário (persistido pela transação do chamador) e passa a recusar os tokens antigos. */
    public void revoke(AppUser user, Instant now) {
        user.revokeSessions(now);
        revokedAt.put(user.getId(), user.getSessionsRevokedAt());
        Instant horizon = horizon();
        revokedAt.values().removeIf(at -> at.isBefore(horizon));
    }

    public boolean isRevoked(long userId, Instant issuedAt) {
        Instant at = revokedAt.get(userId);
        return at != null && (issuedAt == null || issuedAt.isBefore(at));
    }

    private Instant horizon() {
        return clock.instant().minus(accessTtl).minusSeconds(60);
    }
}
