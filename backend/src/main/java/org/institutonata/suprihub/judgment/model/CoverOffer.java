package org.institutonata.suprihub.judgment.model;

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
import jakarta.persistence.Version;

import org.institutonata.suprihub.judgment.model.JudgmentEnums.CoverStatus;

/**
 * Convocação para exercer o direito de cobrir (Edital 8.2): a finalista tem
 * {@code cover_deadline_business_days} (1) dia útil para oferecer valor inferior à menor proposta.
 */
@Entity
@Table(name = "cover_offer")
public class CoverOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "judgment_id", nullable = false, updatable = false)
    private Long judgmentId;
    @Column(name = "proposal_id", nullable = false, updatable = false)
    private Long proposalId;
    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;
    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoverStatus status = CoverStatus.OFFERED;

    @Column(nullable = false, updatable = false)
    private BigDecimal ceiling;
    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;
    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;
    @Column(name = "responded_at")
    private Instant respondedAt;
    @Column(name = "offered_value")
    private BigDecimal offeredValue;

    @Version
    private long version;

    protected CoverOffer() {
    }

    public CoverOffer(Long judgmentId, Long proposalId, Long applicationId, int position, BigDecimal ceiling,
            LocalDate dueDate, Instant now) {
        this.judgmentId = judgmentId;
        this.proposalId = proposalId;
        this.applicationId = applicationId;
        this.position = position;
        this.ceiling = ceiling;
        this.dueDate = dueDate;
        this.offeredAt = now;
    }

    /** Pode responder até o fim do dia útil do prazo. */
    public boolean canRespond(LocalDate today) {
        return status == CoverStatus.OFFERED && !today.isAfter(dueDate);
    }

    public void cover(BigDecimal value, Instant now) {
        close(CoverStatus.COVERED, now);
        this.offeredValue = value;
    }

    public void decline(Instant now) {
        close(CoverStatus.DECLINED, now);
    }

    public void expire(Instant now) {
        close(CoverStatus.EXPIRED, now);
    }

    public void cancel(Instant now) {
        close(CoverStatus.CANCELLED, now);
    }

    private void close(CoverStatus to, Instant now) {
        if (status != CoverStatus.OFFERED) {
            throw new IllegalStateException("Convocação já encerrada: " + status);
        }
        this.status = to;
        this.respondedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getJudgmentId() {
        return judgmentId;
    }

    public Long getProposalId() {
        return proposalId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public int getPosition() {
        return position;
    }

    public CoverStatus getStatus() {
        return status;
    }

    public BigDecimal getCeiling() {
        return ceiling;
    }

    public Instant getOfferedAt() {
        return offeredAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public BigDecimal getOfferedValue() {
        return offeredValue;
    }

    @Override
    public String toString() {
        return "CoverOffer#" + id + "[" + status + "]";
    }
}
