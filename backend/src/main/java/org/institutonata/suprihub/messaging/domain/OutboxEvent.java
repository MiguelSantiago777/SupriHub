package org.institutonata.suprihub.messaging.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    public enum Status { PENDING, PUBLISHED, FAILED }

    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(DomainEvent event, String payloadJson) {
        this.id = event.id();
        this.aggregateType = event.aggregateType();
        this.aggregateId = event.aggregateId();
        this.eventType = event.type();
        this.payload = payloadJson;
        this.occurredAt = event.occurredAt();
        this.nextAttemptAt = event.occurredAt();
    }

    public void markPublished(Instant now) {
        this.status = Status.PUBLISHED;
        this.publishedAt = now;
        this.lastError = null;
    }

    /** Registra falha com backoff exponencial (2^tentativas s, máx. 1 h). */
    public void markFailedAttempt(String error, Instant now, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 2000));
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
            return;
        }
        Duration backoff = Duration.ofSeconds(1L << Math.min(attempts, 12));
        this.nextAttemptAt = now.plus(backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff);
    }

    /** Reenfileira um evento FAILED (ação manual do ADMIN). */
    public void retry(Instant now) {
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
