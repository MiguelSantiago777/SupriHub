package org.institutonata.suprihub.shared.ratelimit;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita requisições por IP nos endpoints públicos e de autenticação.
 * Em memória (por instância); para vários nós, trocar por Bucket4j + Redis/JDBC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final SupriHubProperties.RateLimit config;
    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_TRACKED_CLIENTS;
                }
            });

    public RateLimitFilter(SupriHubProperties props) {
        this.config = props.rateLimit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!config.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith("/api/public/") || path.startsWith("/api/auth/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = request.getRequestURI().startsWith("/api/auth/");
        int perMinute = auth ? config.authPerMinute() : config.publicPerMinute();
        String key = (auth ? "auth:" : "pub:") + request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute)
                        .refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"https://suprihub.institutonata.org.br/problemas/rate-limit","title":"Muitas tentativas",\
                "status":429,"detail":"Muitas tentativas em pouco tempo. Aguarde %d segundos e tente novamente.",\
                "code":"RATE_LIMITED"}""".formatted(retryAfter));
    }
}
