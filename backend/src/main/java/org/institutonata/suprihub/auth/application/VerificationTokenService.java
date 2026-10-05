package org.institutonata.suprihub.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.institutonata.suprihub.auth.domain.VerificationToken;
import org.institutonata.suprihub.auth.infra.VerificationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite tokens de uso único para links enviados por e-mail. O token em claro só existe
 * em memória até virar link no e-mail — nunca vai para o outbox nem para o broker.
 */
@Service
public class VerificationTokenService {

    public static final Duration EMAIL_CONFIRMATION_TTL = Duration.ofHours(48);
    public static final Duration PASSWORD_RESET_TTL = Duration.ofHours(2);
    public static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final VerificationTokenRepository repository;
    private final Clock clock;

    public VerificationTokenService(VerificationTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Invalida tokens abertos do mesmo propósito e devolve um novo token em claro. */
    @Transactional
    public String issue(long userId, VerificationToken.Purpose purpose, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidateOpen(userId, purpose, now);
        String raw = TokenHasher.newToken();
        repository.save(new VerificationToken(userId, TokenHasher.sha256(raw), purpose, now.plus(ttl), now));
        return raw;
    }
}
