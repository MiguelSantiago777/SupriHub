package org.institutonata.suprihub.parameter.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "parameter_history")
public class ParameterHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String key;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value", nullable = false)
    private String newValue;

    private String reason;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ParameterHistory() {
    }

    public ParameterHistory(String key, String oldValue, String newValue, String reason, Long changedBy,
            Instant changedAt) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
