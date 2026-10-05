package org.institutonata.suprihub.messaging.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de domínio publicado via transactional outbox.
 *
 * <p>{@code type} segue o padrão {@code <módulo>.<agregado>.<fato-no-passado>}
 * (ex.: {@code auth.user.registered}) e é usado como routing key no RabbitMQ.
 *
 * <p><strong>LGPD:</strong> o payload carrega apenas identificadores e dados não
 * pessoais. Consumidores buscam o restante no banco. Ver {@link EventPayloadGuard}.
 */
public record DomainEvent(
        UUID id,
        String type,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public DomainEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(occurredAt);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static DomainEvent of(String type, String aggregateType, Object aggregateId,
            Map<String, Object> payload, Clock clock) {
        return new DomainEvent(UUID.randomUUID(), type, aggregateType, String.valueOf(aggregateId),
                clock.instant(), payload);
    }
}
