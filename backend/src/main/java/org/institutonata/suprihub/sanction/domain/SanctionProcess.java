package org.institutonata.suprihub.sanction.domain;

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

import org.institutonata.suprihub.sanction.domain.SanctionEnums.BoardOutcome;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Infraction;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Sanction;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Status;
import org.institutonata.suprihub.sanction.domain.SanctionRules.Decision;

/**
 * Processo sancionador (Política art. 26; Anexo XII): notificação → defesa em 5 d.u. →
 * decisão motivada da Comissão em 10 d.u. → recurso à Diretoria em 5 d.u. → decisão final em
 * 10 d.u.
 */
@Entity
@Table(name = "sanction_process")
public class SanctionProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String number;
    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;
    @Column(name = "service_order_id", updatable = false)
    private Long serviceOrderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Infraction infraction;
    @Column(nullable = false, updatable = false)
    private String facts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DEFENSE;
    @Column(name = "interim_block", nullable = false, updatable = false)
    private boolean interimBlock;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private Long openedBy;
    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;
    @Column(name = "defense_due", nullable = false, updatable = false)
    private LocalDate defenseDue;
    @Column(name = "defense_text")
    private String defenseText;
    @Column(name = "defense_at")
    private Instant defenseAt;
    @Column(name = "decision_due")
    private LocalDate decisionDue;

    @Enumerated(EnumType.STRING)
    private Sanction sanction;
    @Column(name = "fine_pct")
    private BigDecimal finePct;
    @Column(name = "suspension_months")
    private Integer suspensionMonths;
    @Column(name = "impediment_years")
    private Integer impedimentYears;
    @Column(name = "decision_reason")
    private String decisionReason;
    @Column(name = "decided_by")
    private Long decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "appeal_due")
    private LocalDate appealDue;
    @Column(name = "appeal_text")
    private String appealText;
    @Column(name = "appealed_at")
    private Instant appealedAt;
    @Column(name = "board_due")
    private LocalDate boardDue;
    @Enumerated(EnumType.STRING)
    @Column(name = "board_outcome")
    private BoardOutcome boardOutcome;
    @Column(name = "board_reason")
    private String boardReason;
    @Column(name = "board_by")
    private Long boardBy;
    @Column(name = "board_at")
    private Instant boardAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_sanction")
    private Sanction finalSanction;
    @Column(name = "final_fine_pct")
    private BigDecimal finalFinePct;
    @Column(name = "final_months")
    private Integer finalMonths;
    @Column(name = "final_years")
    private Integer finalYears;
    @Column(name = "effective_until")
    private LocalDate effectiveUntil;
    @Column(name = "final_at")
    private Instant finalAt;

    @Version
    private long version;

    protected SanctionProcess() {
    }

    public SanctionProcess(String number, Long applicationId, Long serviceOrderId, Infraction infraction, String facts,
            boolean interimBlock, Long openedBy, LocalDate defenseDue, Instant now) {
        this.number = number;
        this.applicationId = applicationId;
        this.serviceOrderId = serviceOrderId;
        this.infraction = infraction;
        this.facts = facts.trim();
        this.interimBlock = interimBlock;
        this.openedBy = openedBy;
        this.defenseDue = defenseDue;
        this.openedAt = now;
    }

    public boolean canDefend(LocalDate today) {
        return status == Status.DEFENSE && !today.isAfter(defenseDue);
    }

    public void defend(String text, LocalDate decisionDue, Instant now) {
        require(status == Status.DEFENSE, "Fora do prazo de defesa");
        this.defenseText = text.trim();
        this.defenseAt = now;
        this.decisionDue = decisionDue;
        this.status = Status.AWAITING_DECISION;
    }

    /** Prazo de defesa vencido sem defesa: segue para decisão. */
    public void closeDefense(LocalDate decisionDue) {
        require(status == Status.DEFENSE, "Fora do prazo de defesa");
        this.decisionDue = decisionDue;
        this.status = Status.AWAITING_DECISION;
    }

    public void decide(Decision d, String reason, Long userId, LocalDate appealDue, Instant now) {
        require(status == Status.AWAITING_DECISION || status == Status.DEFENSE, "Processo já decidido");
        this.sanction = d.sanction();
        this.finePct = d.sanction() == Sanction.FINE ? d.finePct() : null;
        this.suspensionMonths = d.sanction() == Sanction.SUSPENSION ? d.months() : null;
        this.impedimentYears = d.sanction() == Sanction.DEREGISTRATION ? d.years() : null;
        this.decisionReason = reason.trim();
        this.decidedBy = userId;
        this.decidedAt = now;
        this.appealDue = appealDue;
        this.status = Status.APPEAL_WINDOW;
    }

    public boolean canAppeal(LocalDate today) {
        return status == Status.APPEAL_WINDOW && appealDue != null && !today.isAfter(appealDue);
    }

    public void appeal(String text, LocalDate boardDue, Instant now) {
        require(status == Status.APPEAL_WINDOW, "Fora do prazo de recurso");
        this.appealText = text.trim();
        this.appealedAt = now;
        this.boardDue = boardDue;
        this.status = Status.BOARD_REVIEW;
    }

    public void boardDecide(BoardOutcome outcome, Decision reformed, String reason, Long userId, Instant now) {
        require(status == Status.BOARD_REVIEW, "Sem recurso pendente");
        this.boardOutcome = outcome;
        this.boardReason = reason.trim();
        this.boardBy = userId;
        this.boardAt = now;
    }

    /** Encerra com a sanção que vale (da Comissão, ou a da Diretoria). */
    public void finish(Decision d, LocalDate until, Instant now) {
        require(status == Status.APPEAL_WINDOW || status == Status.BOARD_REVIEW, "Processo sem decisão");
        this.finalSanction = d.sanction();
        this.finalFinePct = d.sanction() == Sanction.FINE ? d.finePct() : null;
        this.finalMonths = d.sanction() == Sanction.SUSPENSION ? d.months() : null;
        this.finalYears = d.sanction() == Sanction.DEREGISTRATION ? d.years() : null;
        this.effectiveUntil = until;
        this.finalAt = now;
        this.status = Status.FINAL;
    }

    public Decision commissionDecision() {
        return new Decision(sanction, finePct, suspensionMonths, impedimentYears);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getServiceOrderId() {
        return serviceOrderId;
    }

    public Infraction getInfraction() {
        return infraction;
    }

    public String getFacts() {
        return facts;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isInterimBlock() {
        return interimBlock;
    }

    public Long getOpenedBy() {
        return openedBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public LocalDate getDefenseDue() {
        return defenseDue;
    }

    public String getDefenseText() {
        return defenseText;
    }

    public Instant getDefenseAt() {
        return defenseAt;
    }

    public LocalDate getDecisionDue() {
        return decisionDue;
    }

    public Sanction getSanction() {
        return sanction;
    }

    public BigDecimal getFinePct() {
        return finePct;
    }

    public Integer getSuspensionMonths() {
        return suspensionMonths;
    }

    public Integer getImpedimentYears() {
        return impedimentYears;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public LocalDate getAppealDue() {
        return appealDue;
    }

    public String getAppealText() {
        return appealText;
    }

    public Instant getAppealedAt() {
        return appealedAt;
    }

    public LocalDate getBoardDue() {
        return boardDue;
    }

    public BoardOutcome getBoardOutcome() {
        return boardOutcome;
    }

    public String getBoardReason() {
        return boardReason;
    }

    public Instant getBoardAt() {
        return boardAt;
    }

    public Sanction getFinalSanction() {
        return finalSanction;
    }

    public BigDecimal getFinalFinePct() {
        return finalFinePct;
    }

    public Integer getFinalMonths() {
        return finalMonths;
    }

    public Integer getFinalYears() {
        return finalYears;
    }

    public LocalDate getEffectiveUntil() {
        return effectiveUntil;
    }

    public Instant getFinalAt() {
        return finalAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "SanctionProcess#" + id + "[" + status + "]";
    }
}
