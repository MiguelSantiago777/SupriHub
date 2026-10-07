package org.institutonata.suprihub.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Limite de senhas erradas por par (e-mail, IP): após {@value #MAX_FAILURES} erros, aquele IP
 * fica 15 minutos sem poder tentar aquela conta. Vale também para e-mails inexistentes, para
 * não revelar quais contas existem. Em memória (por instância), como o rate limit.
 */
@Component
public class LoginThrottle {

    public static final int MAX_FAILURES = 5;
    public static final Duration BLOCK = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;

    private record Attempts(int failures, Instant lastFailure, Instant blockedUntil) {
    }

    private final Map<String, Attempts> entries = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Attempts> eldest) {
                    return size() > MAX_TRACKED;
                }
            });

    public boolean isBlocked(String email, String ip, Instant now) {
        Attempts e = entries.get(key(email, ip));
        return e != null && e.blockedUntil() != null && e.blockedUntil().isAfter(now);
    }

    public void registerFailure(String email, String ip, Instant now) {
        entries.compute(key(email, ip), (k, e) -> {
            int failures = e == null || e.lastFailure().isBefore(now.minus(BLOCK)) ? 1 : e.failures() + 1;
            return failures >= MAX_FAILURES ? new Attempts(0, now, now.plus(BLOCK))
                    : new Attempts(failures, now, null);
        });
    }

    public void reset(String email, String ip) {
        entries.remove(key(email, ip));
    }

    /** Usado nos testes. */
    public void clear() {
        entries.clear();
    }

    private static String key(String email, String ip) {
        return email.trim().toLowerCase(Locale.ROOT) + "|" + ip;
    }
}
