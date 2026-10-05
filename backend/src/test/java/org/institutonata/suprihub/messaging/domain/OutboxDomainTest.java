package org.institutonata.suprihub.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OutboxDomainTest {

    private final Instant now = Instant.parse("2026-10-05T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void falhasUsamBackoffExponencialEViramFailedNoLimite() {
        OutboxEvent e = new OutboxEvent(DomainEvent.of("auth.user.registered", "app_user", 1L,
                Map.of("userId", 1L), clock), "{\"userId\":1}");

        e.markFailedAttempt("broker fora", now, 3);
        assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(e.getNextAttemptAt()).isEqualTo(now.plusSeconds(2));

        e.markFailedAttempt("broker fora", now, 3);
        assertThat(e.getNextAttemptAt()).isEqualTo(now.plusSeconds(4));

        e.markFailedAttempt("broker fora", now, 3);
        assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);

        e.retry(now);
        assertThat(e.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(e.getAttempts()).isZero();
    }

    @Test
    void backoffLimitadoAUmaHora() {
        OutboxEvent e = new OutboxEvent(DomainEvent.of("x.y.z", "agg", 1, Map.of(), clock), "{}");
        for (int i = 0; i < 20; i++) {
            e.markFailedAttempt("erro", now, 100);
        }
        assertThat(Duration.between(now, e.getNextAttemptAt())).isLessThanOrEqualTo(Duration.ofHours(1));
    }

    @Test
    void payloadNaoPodeCarregarDadosPessoais() {
        assertThatThrownBy(() -> EventPayloadGuard.check("t", Map.of("email", "a@b.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventPayloadGuard.check("t", Map.of("cpfTitular", "1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventPayloadGuard.check("t", Map.of("raçaCor", "preta")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventPayloadGuard.check("t", Map.of("telefone", "21")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatNoException().isThrownBy(() -> EventPayloadGuard.check("t",
                Map.of("userId", 1, "supplierId", 2, "status", "ATIVO")));
    }
}
