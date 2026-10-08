package org.institutonata.suprihub.auth.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.institutonata.suprihub.auth.domain.LoginChallenge;
import org.institutonata.suprihub.auth.infra.LoginChallengeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera o código de 6 dígitos da verificação em duas etapas. Como os links de e-mail, o código
 * em claro só existe em memória até virar e-mail — nunca vai para o outbox nem para o broker.
 */
@Service
public class LoginCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoginChallengeRepository challenges;
    private final Clock clock;

    public LoginCodeService(LoginChallengeRepository challenges, Clock clock) {
        this.challenges = challenges;
        this.clock = clock;
    }

    /** Novo código para o login pendente; vazio se ele já foi usado ou expirou. */
    @Transactional
    public Optional<String> issue(long challengeId) {
        Instant now = clock.instant();
        return challenges.findById(challengeId).filter(c -> c.isUsable(now)).map(c -> {
            String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
            c.newCode(hash(c.getId(), code), now);
            return code;
        });
    }

    static String hash(long challengeId, String code) {
        return TokenHasher.sha256(challengeId + ":" + code);
    }
}
