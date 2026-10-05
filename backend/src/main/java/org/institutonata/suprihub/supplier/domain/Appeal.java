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
import jakarta.persistence.Version;

/**
 * Recurso contra a inabilitação (Edital 5.2; Anexo X). A Comissão não nega o recurso:
 * reconsidera (provido) ou encaminha à Diretoria, com motivação; a Diretoria decide.
 * <pre>
 *  FILED ──reconsiderar──▶ GRANTED
 *    └──encaminhar──▶ FORWARDED ──prover──▶ GRANTED
 *                         └──negar──▶ DENIED
 * </pre>
 */
@Entity
@Table(name = "application_appeal")
public class Appeal {

    public enum Status {
        FILED("Na Comissão"),
        FORWARDED("Na Diretoria"),
        GRANTED("Provido"),
        DENIED("Não provido");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean isOpen() {
            return this == FILED || this == FORWARDED;
        }
    }

    /** Anexo enviado com o recurso (opcional). */
    public record Attachment(String filename, String contentType, long sizeBytes, String sha256,
            String storageKey) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.FILED;

    @Column(nullable = false, updatable = false)
    private String reasons;

    @Column(nullable = false, updatable = false)
    private String request;

    @Column(name = "attachment_filename", updatable = false)
    private String attachmentFilename;
    @Column(name = "attachment_type", updatable = false)
    private String attachmentType;
    @Column(name = "attachment_size", updatable = false)
    private Long attachmentSize;
    @Column(name = "attachment_sha256", updatable = false)
    private String attachmentSha256;
    @Column(name = "attachment_key", updatable = false)
    private String attachmentKey;

    @Column(name = "filed_at", nullable = false, updatable = false)
    private Instant filedAt;
    @Column(name = "filed_by", nullable = false, updatable = false)
    private Long filedBy;
    @Column(name = "reconsideration_due", nullable = false, updatable = false)
    private LocalDate reconsiderationDue;

    @Column(name = "commission_reason")
    private String commissionReason;
    @Column(name = "commission_decided_at")
    private Instant commissionDecidedAt;
    @Column(name = "commission_decided_by")
    private Long commissionDecidedBy;

    @Column(name = "board_due")
    private LocalDate boardDue;
    @Column(name = "board_reason")
    private String boardReason;
    @Column(name = "board_decided_at")
    private Instant boardDecidedAt;
    @Column(name = "board_decided_by")
    private Long boardDecidedBy;

    @Version
    private long version;

    protected Appeal() {
    }

    public Appeal(Long applicationId, Long filedBy, String reasons, String request, Attachment attachment,
            LocalDate reconsiderationDue, Instant now) {
        this.applicationId = applicationId;
        this.filedBy = filedBy;
        this.reasons = reasons.trim();
        this.request = request.trim();
        if (attachment != null) {
            this.attachmentFilename = attachment.filename();
            this.attachmentType = attachment.contentType();
            this.attachmentSize = attachment.sizeBytes();
            this.attachmentSha256 = attachment.sha256();
            this.attachmentKey = attachment.storageKey();
        }
        this.reconsiderationDue = reconsiderationDue;
        this.filedAt = now;
    }

    /** Comissão reconsidera a inabilitação: recurso provido. */
    public void reconsider(long memberId, String reason, Instant now) {
        require(Status.FILED);
        status = Status.GRANTED;
        commissionReason = reason.trim();
        commissionDecidedAt = now;
        commissionDecidedBy = memberId;
    }

    /** Comissão mantém a decisão e encaminha à Diretoria, com sua motivação. */
    public void forward(long memberId, String reason, LocalDate boardDue, Instant now) {
        require(Status.FILED);
        status = Status.FORWARDED;
        commissionReason = reason.trim();
        commissionDecidedAt = now;
        commissionDecidedBy = memberId;
        this.boardDue = boardDue;
    }

    public void boardDecision(long boardMemberId, boolean grant, String reason, Instant now) {
        require(Status.FORWARDED);
        status = grant ? Status.GRANTED : Status.DENIED;
        boardReason = reason.trim();
        boardDecidedAt = now;
        boardDecidedBy = boardMemberId;
    }

    private void require(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Recurso " + status + " não admite esta decisão");
        }
    }

    public boolean hasAttachment() {
        return attachmentKey != null;
    }

    public Attachment attachment() {
        return hasAttachment() ? new Attachment(attachmentFilename, attachmentType,
                attachmentSize == null ? 0 : attachmentSize, attachmentSha256, attachmentKey) : null;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Status getStatus() {
        return status;
    }

    public String getReasons() {
        return reasons;
    }

    public String getRequest() {
        return request;
    }

    public Instant getFiledAt() {
        return filedAt;
    }

    public Long getFiledBy() {
        return filedBy;
    }

    public LocalDate getReconsiderationDue() {
        return reconsiderationDue;
    }

    public String getCommissionReason() {
        return commissionReason;
    }

    public Instant getCommissionDecidedAt() {
        return commissionDecidedAt;
    }

    public LocalDate getBoardDue() {
        return boardDue;
    }

    public String getBoardReason() {
        return boardReason;
    }

    public Instant getBoardDecidedAt() {
        return boardDecidedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Appeal#" + id + "[" + status + "]";
    }
}
