package org.institutonata.suprihub.parameter.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "parameter")
public class Parameter {

    @Id
    private String key;

    @Column(nullable = false)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private ValueType valueType;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String description;

    private String source;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Parameter() {
    }

    public void changeValue(String newValue, Long userId, Instant now) {
        this.value = valueType.normalize(newValue);
        this.updatedBy = userId;
        this.updatedAt = now;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getSource() {
        return source;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
