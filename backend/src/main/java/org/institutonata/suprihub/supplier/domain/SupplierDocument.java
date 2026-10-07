package org.institutonata.suprihub.supplier.domain;

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

/** Arquivo de habilitação enviado pelo fornecedor (um por tipo; reenviar substitui). */
@Entity
@Table(name = "supplier_document")
public class SupplierDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, updatable = false)
    private DocumentType type;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** Validade da certidão (só nos tipos {@link DocumentType#isCertificate()}). */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    /** Conferência da Comissão no sítio oficial (Manual item 3, passo 3). */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    /** Aviso "RENOVAR" já enviado para este arquivo. */
    @Column(name = "alert_sent_on")
    private LocalDate alertSentOn;

    protected SupplierDocument() {
    }

    public SupplierDocument(Long applicationId, DocumentType type, String originalFilename, String contentType,
            long sizeBytes, String sha256, String storageKey, Instant uploadedAt) {
        this.applicationId = applicationId;
        this.type = type;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedAt = uploadedAt;
    }

    /**
     * Informa (ou corrige) a validade da certidão. Com {@code verifierId}, registra também a
     * conferência da Comissão; uma correção do fornecedor desfaz a conferência anterior.
     */
    public void setValidity(LocalDate validUntil, Long verifierId, Instant now) {
        if (!type.isCertificate()) {
            throw new IllegalStateException(type + " não tem validade");
        }
        if (!validUntil.equals(this.validUntil)) {
            this.alertSentOn = null;
        }
        this.validUntil = validUntil;
        this.verifiedBy = verifierId;
        this.verifiedAt = verifierId == null ? null : now;
    }

    public void markAlerted(LocalDate today) {
        this.alertSentOn = today;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Long getVerifiedBy() {
        return verifiedBy;
    }

    public LocalDate getAlertSentOn() {
        return alertSentOn;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public DocumentType getType() {
        return type;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    @Override
    public String toString() {
        return "SupplierDocument#" + id + "[" + type + "]";
    }
}
