package org.institutonata.suprihub.contract.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Revisão do dossiê por amostragem, pelo Controle Interno (Manual item 1; indicador do item 7). */
@Entity
@Table(name = "dossier_review")
public class DossierReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "service_order_id", nullable = false, updatable = false)
    private Long serviceOrderId;
    @Column(nullable = false, updatable = false)
    private boolean complete;
    @Column(updatable = false)
    private String note;
    @Column(name = "reviewed_by", nullable = false, updatable = false)
    private Long reviewedBy;
    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    protected DossierReview() {
    }

    public DossierReview(Long serviceOrderId, boolean complete, String note, Long reviewedBy, Instant now) {
        this.serviceOrderId = serviceOrderId;
        this.complete = complete;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.reviewedBy = reviewedBy;
        this.reviewedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getServiceOrderId() {
        return serviceOrderId;
    }

    public boolean isComplete() {
        return complete;
    }

    public String getNote() {
        return note;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
