package org.institutonata.suprihub.supplier.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Passo do andamento do requerimento, exibido ao fornecedor e à Comissão. */
@Entity
@Table(name = "supplier_application_event")
public class ApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ApplicationStatus status;

    @Column(updatable = false)
    private String message;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ApplicationEvent() {
    }

    public ApplicationEvent(Long applicationId, ApplicationStatus status, String message, Long actorId,
            Instant occurredAt) {
        this.applicationId = applicationId;
        this.status = status;
        this.message = message;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Long getActorId() {
        return actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
