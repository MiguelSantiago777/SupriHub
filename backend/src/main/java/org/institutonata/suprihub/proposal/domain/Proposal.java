package org.institutonata.suprihub.proposal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.institutonata.suprihub.proposal.domain.ProposalRules.Mode;

/** Proposta do fornecedor a uma oportunidade (Anexo VII). Uma por fornecedor; pode ser substituída até o prazo. */
@Entity
@Table(name = "proposal")
public class Proposal {

    /** Arquivo da proposta (modelo assinado ou proposta própria). */
    public record File(String name, String contentType, long size, String sha256, String storageKey) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private Long tenderId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "total_value", nullable = false)
    private BigDecimal totalValue;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "proposal_item", joinColumns = @JoinColumn(name = "proposal_id"))
    @MapKeyColumn(name = "tender_item_id")
    @Column(name = "unit_price")
    private Map<Long, BigDecimal> unitPrices = new HashMap<>();

    @Column(name = "execution_deadline")
    private String executionDeadline;
    @Column(name = "validity_days", nullable = false)
    private int validityDays;
    @Column(name = "special_conditions")
    private String specialConditions;
    private String notes;
    @Column(name = "declaration_accepted_at", nullable = false)
    private Instant declarationAcceptedAt;

    @Column(name = "file_name")
    private String fileName;
    @Column(name = "file_type")
    private String fileType;
    @Column(name = "file_size")
    private Long fileSize;
    @Column(name = "file_sha256")
    private String fileSha256;
    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Proposal() {
    }

    public Proposal(Long tenderId, Long applicationId, Instant now) {
        this.tenderId = tenderId;
        this.applicationId = applicationId;
        this.submittedAt = now;
    }

    /**
     * Grava (ou substitui) o conteúdo. {@code file} nulo mantém o arquivo atual; devolve a
     * chave do arquivo substituído, para o chamador apagar.
     */
    public String fill(Mode mode, Map<Long, BigDecimal> unitPrices, BigDecimal total, String executionDeadline,
            int validityDays, String specialConditions, String notes, File file, Instant now) {
        this.mode = mode;
        this.unitPrices.clear();
        this.unitPrices.putAll(unitPrices);
        this.totalValue = total;
        this.executionDeadline = trim(executionDeadline);
        this.validityDays = validityDays;
        this.specialConditions = trim(specialConditions);
        this.notes = trim(notes);
        this.declarationAcceptedAt = now;
        String replaced = null;
        if (mode == Mode.ONLINE) {
            replaced = fileKey;
            clearFile();
        } else if (file != null) {
            replaced = fileKey;
            this.fileName = file.name();
            this.fileType = file.contentType();
            this.fileSize = file.size();
            this.fileSha256 = file.sha256();
            this.fileKey = file.storageKey();
        }
        this.updatedAt = now;
        return replaced;
    }

    private void clearFile() {
        fileName = null;
        fileType = null;
        fileSize = null;
        fileSha256 = null;
        fileKey = null;
    }

    public boolean hasFile() {
        return fileKey != null;
    }

    public File file() {
        return hasFile() ? new File(fileName, fileType, fileSize == null ? 0 : fileSize, fileSha256, fileKey) : null;
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

    public Long getTenderId() {
        return tenderId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Mode getMode() {
        return mode;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public Map<Long, BigDecimal> getUnitPrices() {
        return Map.copyOf(unitPrices);
    }

    public String getExecutionDeadline() {
        return executionDeadline;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public String getSpecialConditions() {
        return specialConditions;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Proposal#" + id;
    }
}
