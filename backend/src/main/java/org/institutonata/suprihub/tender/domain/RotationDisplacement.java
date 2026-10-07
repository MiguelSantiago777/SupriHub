package org.institutonata.suprihub.tender.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Deslocamento ao final do rodízio por recusas injustificadas consecutivas (Política art. 13,
 * § 4º; Anexo XII). Conta na fila como se fosse uma seleção nesta data.
 */
@Entity
@Table(name = "rotation_displacement")
public class RotationDisplacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private Long tenderId;

    @Column(name = "displaced_at", nullable = false, updatable = false)
    private Instant displacedAt;

    @Column(nullable = false, updatable = false)
    private String reason;

    /** Já tinha sido deslocado antes: a reincidência enseja advertência (Anexo XII). */
    @Column(nullable = false, updatable = false)
    private boolean recidivism;

    protected RotationDisplacement() {
    }

    public RotationDisplacement(Long applicationId, Long tenderId, Instant displacedAt, String reason,
            boolean recidivism) {
        this.applicationId = applicationId;
        this.tenderId = tenderId;
        this.displacedAt = displacedAt;
        this.reason = reason;
        this.recidivism = recidivism;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getTenderId() {
        return tenderId;
    }

    public Instant getDisplacedAt() {
        return displacedAt;
    }

    public String getReason() {
        return reason;
    }

    public boolean isRecidivism() {
        return recidivism;
    }
}
