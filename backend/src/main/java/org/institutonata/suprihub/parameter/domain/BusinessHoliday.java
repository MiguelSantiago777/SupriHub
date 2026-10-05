package org.institutonata.suprihub.parameter.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_holiday")
public class BusinessHoliday {

    public enum Scope { NACIONAL, ESTADUAL, MUNICIPAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope;

    @Column(name = "optional_point", nullable = false)
    private boolean optionalPoint;

    @Column(name = "municipality_id")
    private Integer municipalityId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BusinessHoliday() {
    }

    public BusinessHoliday(LocalDate date, String name, Scope scope, boolean optionalPoint, Integer municipalityId,
            Instant createdAt) {
        this.date = date;
        this.name = name;
        this.scope = scope;
        this.optionalPoint = optionalPoint;
        this.municipalityId = municipalityId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public Scope getScope() {
        return scope;
    }

    public boolean isOptionalPoint() {
        return optionalPoint;
    }

    public Integer getMunicipalityId() {
        return municipalityId;
    }
}
