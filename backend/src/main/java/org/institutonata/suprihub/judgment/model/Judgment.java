package org.institutonata.suprihub.judgment.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.institutonata.suprihub.judgment.model.JudgmentEnums.Status;

/**
 * Julgamento de uma oportunidade das Faixas 2 e 3. Guarda os parâmetros vigentes no momento
 * (a ata cita estes) e o resultado; o mapa fica em {@link JudgmentEntry}.
 */
@Entity
@Table(name = "judgment")
public class Judgment {

    /** Parâmetros aplicados. */
    public record Rules(BigDecimal tieMargin, BigDecimal localMargin, boolean localPreferenceForAll,
            boolean coverStrictlyLower, int minValidRequired) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private Long tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "lowest_value", nullable = false, updatable = false)
    private BigDecimal lowestValue;
    @Column(name = "tie_margin", nullable = false, updatable = false)
    private BigDecimal tieMargin;
    @Column(name = "local_margin", nullable = false, updatable = false)
    private BigDecimal localMargin;
    @Column(name = "local_preference_for_all", nullable = false, updatable = false)
    private boolean localPreferenceForAll;
    @Column(name = "cover_strictly_lower", nullable = false, updatable = false)
    private boolean coverStrictlyLower;
    @Column(name = "valid_count", nullable = false, updatable = false)
    private int validCount;
    @Column(name = "min_valid_required", nullable = false, updatable = false)
    private int minValidRequired;
    @Column(name = "below_minimum_justification", updatable = false)
    private String belowMinimumJustification;

    @Column(name = "winner_proposal_id")
    private Long winnerProposalId;
    @Column(name = "awarded_value")
    private BigDecimal awardedValue;
    @Column(name = "winner_reason")
    private String winnerReason;

    @Column(name = "started_by", nullable = false, updatable = false)
    private Long startedBy;
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "homologated_by")
    private Long homologatedBy;
    @Column(name = "homologated_at")
    private Instant homologatedAt;
    @Column(name = "homologation_note")
    private String homologationNote;
    @Column(name = "annulled_by")
    private Long annulledBy;
    @Column(name = "annulled_at")
    private Instant annulledAt;
    @Column(name = "annul_reason")
    private String annulReason;
    @Column(name = "ata_key")
    private String ataKey;
    @Column(name = "ata_sha256")
    private String ataSha256;

    @Version
    private long version;

    protected Judgment() {
    }

    public Judgment(Long tenderId, BigDecimal lowestValue, Rules rules, int validCount, String justification,
            Long startedBy, Instant now) {
        this.tenderId = tenderId;
        this.status = Status.AWAITING_COVER;
        this.lowestValue = lowestValue;
        this.tieMargin = rules.tieMargin();
        this.localMargin = rules.localMargin();
        this.localPreferenceForAll = rules.localPreferenceForAll();
        this.coverStrictlyLower = rules.coverStrictlyLower();
        this.minValidRequired = rules.minValidRequired();
        this.validCount = validCount;
        this.belowMinimumJustification = justification == null || justification.isBlank() ? null
                : justification.trim();
        this.startedBy = startedBy;
        this.startedAt = now;
    }

    public void decide(Long proposalId, BigDecimal value, String reason, Instant now) {
        if (status != Status.AWAITING_COVER) {
            throw new IllegalStateException("Julgamento " + status + " já tem resultado");
        }
        this.status = Status.DECIDED;
        this.winnerProposalId = proposalId;
        this.awardedValue = value;
        this.winnerReason = reason;
        this.decidedAt = now;
    }

    public void homologate(Long userId, String note, String ataKey, String ataSha256, Instant now) {
        if (status != Status.DECIDED) {
            throw new IllegalStateException("Só julgamento com resultado pode ser homologado");
        }
        this.status = Status.HOMOLOGATED;
        this.homologatedBy = userId;
        this.homologationNote = note == null || note.isBlank() ? null : note.trim();
        this.homologatedAt = now;
        this.ataKey = ataKey;
        this.ataSha256 = ataSha256;
    }

    public void annul(Long userId, String reason, Instant now) {
        if (!status.isOpen()) {
            throw new IllegalStateException("Julgamento " + status + " não pode ser anulado");
        }
        this.status = Status.ANNULLED;
        this.annulledBy = userId;
        this.annulReason = reason.trim();
        this.annulledAt = now;
    }

    public Rules rules() {
        return new Rules(tieMargin, localMargin, localPreferenceForAll, coverStrictlyLower, minValidRequired);
    }

    public Long getId() {
        return id;
    }

    public Long getTenderId() {
        return tenderId;
    }

    public Status getStatus() {
        return status;
    }

    public BigDecimal getLowestValue() {
        return lowestValue;
    }

    public int getValidCount() {
        return validCount;
    }

    public String getBelowMinimumJustification() {
        return belowMinimumJustification;
    }

    public Long getWinnerProposalId() {
        return winnerProposalId;
    }

    public BigDecimal getAwardedValue() {
        return awardedValue;
    }

    public String getWinnerReason() {
        return winnerReason;
    }

    public Long getStartedBy() {
        return startedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Long getHomologatedBy() {
        return homologatedBy;
    }

    public Instant getHomologatedAt() {
        return homologatedAt;
    }

    public String getHomologationNote() {
        return homologationNote;
    }

    public Long getAnnulledBy() {
        return annulledBy;
    }

    public Instant getAnnulledAt() {
        return annulledAt;
    }

    public String getAnnulReason() {
        return annulReason;
    }

    public String getAtaKey() {
        return ataKey;
    }

    public String getAtaSha256() {
        return ataSha256;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Judgment#" + id + "[" + status + "]";
    }
}
