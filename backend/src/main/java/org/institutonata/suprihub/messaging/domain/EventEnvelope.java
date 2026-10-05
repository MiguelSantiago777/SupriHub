package org.institutonata.suprihub.messaging.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Formato da mensagem recebida pelos consumidores (espelha {@link DomainEvent}). */
public record EventEnvelope(
        UUID id,
        String type,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public String payloadString(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : v.toString();
    }

    public Long payloadLong(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : Long.valueOf(v.toString());
    }
}
