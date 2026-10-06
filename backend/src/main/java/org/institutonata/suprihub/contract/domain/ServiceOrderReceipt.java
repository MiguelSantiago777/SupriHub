package org.institutonata.suprihub.contract.domain;

import java.math.BigDecimal;
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

import org.institutonata.suprihub.contract.domain.ContractEnums.ReceiptKind;
import org.institutonata.suprihub.contract.domain.ContractRules.Scores;

/** Termo de Recebimento e Avaliação de Desempenho (Anexo IX). */
@Entity
@Table(name = "service_order_receipt")
public class ServiceOrderReceipt {

    /** Dados do termo. */
    public record Data(ReceiptKind kind, LocalDate executionDate, String reservations, Integer correctionDays,
            String rejectReason, Scores scores, String notes, String evidenceKinds) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_order_id", nullable = false, updatable = false)
    private Long serviceOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReceiptKind kind;
    @Column(name = "execution_date", nullable = false, updatable = false)
    private LocalDate executionDate;
    @Column(updatable = false)
    private String reservations;
    @Column(name = "correction_days", updatable = false)
    private Integer correctionDays;
    @Column(name = "reject_reason", updatable = false)
    private String rejectReason;
    @Column(name = "score_deadline", nullable = false, updatable = false)
    private int scoreDeadline;
    @Column(name = "score_quality", nullable = false, updatable = false)
    private int scoreQuality;
    @Column(name = "score_documentation", nullable = false, updatable = false)
    private int scoreDocumentation;
    @Column(name = "score_service", nullable = false, updatable = false)
    private int scoreService;
    @Column(name = "score_safety", nullable = false, updatable = false)
    private int scoreSafety;
    @Column(nullable = false, updatable = false)
    private BigDecimal average;
    @Column(updatable = false)
    private String notes;
    @Column(name = "evidence_kinds", updatable = false)
    private String evidenceKinds;
    @Column(name = "received_by", nullable = false, updatable = false)
    private Long receivedBy;
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
    @Column(name = "supplier_ack_at")
    private Instant supplierAckAt;

    protected ServiceOrderReceipt() {
    }

    public ServiceOrderReceipt(Long serviceOrderId, Data d, Long userId, Instant now) {
        this.serviceOrderId = serviceOrderId;
        this.kind = d.kind();
        this.executionDate = d.executionDate();
        this.reservations = d.kind() == ReceiptKind.WITH_RESERVATIONS ? trim(d.reservations()) : null;
        this.correctionDays = d.kind() == ReceiptKind.WITH_RESERVATIONS ? d.correctionDays() : null;
        this.rejectReason = d.kind() == ReceiptKind.REJECTED ? trim(d.rejectReason()) : null;
        this.scoreDeadline = d.scores().deadline();
        this.scoreQuality = d.scores().quality();
        this.scoreDocumentation = d.scores().documentation();
        this.scoreService = d.scores().service();
        this.scoreSafety = d.scores().safety();
        this.average = d.scores().average();
        this.notes = trim(d.notes());
        this.evidenceKinds = trim(d.evidenceKinds());
        this.receivedBy = userId;
        this.receivedAt = now;
    }

    /** Ciência do fornecedor (Anexo IX). */
    public void acknowledge(Instant now) {
        if (supplierAckAt == null) {
            this.supplierAckAt = now;
        }
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public Long getId() {
        return id;
    }

    public Long getServiceOrderId() {
        return serviceOrderId;
    }

    public ReceiptKind getKind() {
        return kind;
    }

    public LocalDate getExecutionDate() {
        return executionDate;
    }

    public String getReservations() {
        return reservations;
    }

    public Integer getCorrectionDays() {
        return correctionDays;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Scores scores() {
        return new Scores(scoreDeadline, scoreQuality, scoreDocumentation, scoreService, scoreSafety);
    }

    public BigDecimal getAverage() {
        return average;
    }

    public String getNotes() {
        return notes;
    }

    public String getEvidenceKinds() {
        return evidenceKinds;
    }

    public Long getReceivedBy() {
        return receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getSupplierAckAt() {
        return supplierAckAt;
    }

    @Override
    public String toString() {
        return "ServiceOrderReceipt#" + id;
    }
}
