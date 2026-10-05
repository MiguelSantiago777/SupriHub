package org.institutonata.suprihub.audit.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro de auditoria — imutável (trigger no banco bloqueia UPDATE/DELETE). */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(nullable = false)
    private String action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_label")
    private String actorLabel;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
    private String afterJson;

    private String details;

    protected AuditLog() {
    }

    public AuditLog(String entityType, String entityId, String action, Long actorId, String actorLabel,
            Instant occurredAt, String beforeJson, String afterJson, String details) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.occurredAt = occurredAt;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public String getDetails() {
        return details;
    }
}
