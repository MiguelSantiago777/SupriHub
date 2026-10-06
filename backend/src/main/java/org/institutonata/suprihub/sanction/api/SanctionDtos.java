package org.institutonata.suprihub.sanction.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.institutonata.suprihub.sanction.application.SanctionService.View;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.BoardOutcome;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.FileKind;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Infraction;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Sanction;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Status;
import org.institutonata.suprihub.sanction.domain.SanctionFile;
import org.institutonata.suprihub.sanction.domain.SanctionProcess;
import org.institutonata.suprihub.sanction.domain.SanctionRules.Decision;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;

public final class SanctionDtos {

    private SanctionDtos() {
    }

    public record OpenRequest(@NotNull long applicationId, Long serviceOrderId,
            @NotNull(message = "Escolha a infração.") Infraction infraction,
            @Size(max = 5000) String facts, boolean interimBlock) {
    }

    public record DecisionRequest(Sanction sanction, BigDecimal finePct, Integer months, Integer years,
            @Size(max = 5000) String reason, long version) {

        Decision toDecision() {
            return new Decision(sanction, finePct, months, years);
        }
    }

    public record BoardRequest(BoardOutcome outcome, Sanction sanction, BigDecimal finePct, Integer months,
            Integer years, @Size(max = 5000) String reason, long version) {

        Decision reformed() {
            return sanction == null ? null : new Decision(sanction, finePct, months, years);
        }
    }

    public record TextRequest(@Size(max = 5000) String text) {
    }

    public record InfractionDto(Infraction code, String label, String reference) {
    }

    public record FileDto(Long id, FileKind kind, String kindLabel, String name, long size, Instant uploadedAt) {
        static FileDto of(SanctionFile f) {
            return new FileDto(f.getId(), f.getKind(), f.getKind().label(), f.getFileName(), f.getFileSize(),
                    f.getUploadedAt());
        }
    }

    public record Summary(Long id, String number, Status status, String statusLabel, String infractionLabel,
            Long applicationId, String supplierName, Instant openedAt, LocalDate nextDue, String finalSanctionLabel,
            LocalDate effectiveUntil) {

        static Summary of(SanctionProcess p, SupplierApplication app) {
            return new Summary(p.getId(), p.getNumber(), p.getStatus(), p.getStatus().label(),
                    p.getInfraction().label(), p.getApplicationId(), app == null ? null : name(app), p.getOpenedAt(),
                    SanctionDtos.nextDue(p), p.getFinalSanction() == null ? null : p.getFinalSanction().label(),
                    p.getEffectiveUntil());
        }
    }

    public record Detail(Long id, String number, Status status, String statusLabel, Infraction infraction,
            String infractionLabel, String infractionReference, String facts, boolean interimBlock,
            Long applicationId, String supplierName, Long serviceOrderId, String serviceOrderNumber,
            java.math.BigDecimal orderPenaltyPct, Instant openedAt, LocalDate defenseDue, String defenseText,
            Instant defenseAt, LocalDate decisionDue, Sanction sanction, String sanctionLabel, BigDecimal finePct,
            Integer suspensionMonths, Integer impedimentYears, String decisionReason, Instant decidedAt,
            LocalDate appealDue, String appealText, Instant appealedAt, LocalDate boardDue, BoardOutcome boardOutcome,
            String boardOutcomeLabel, String boardReason, Instant boardAt, Sanction finalSanction,
            String finalSanctionLabel, BigDecimal finalFinePct, Integer finalMonths, Integer finalYears,
            LocalDate effectiveUntil, Instant finalAt, List<FileDto> files, List<Summary> previous,
            boolean recidivist, boolean canDefend, boolean canAppeal, long version) {

        static Detail of(View v, LocalDate today) {
            SanctionProcess p = v.process();
            SupplierApplication app = v.supplier();
            return new Detail(p.getId(), p.getNumber(), p.getStatus(), p.getStatus().label(), p.getInfraction(),
                    p.getInfraction().label(), p.getInfraction().reference(), p.getFacts(), p.isInterimBlock(),
                    p.getApplicationId(), app == null ? null : name(app), p.getServiceOrderId(),
                    v.order() == null ? null : v.order().getNumber(),
                    v.order() == null ? null : v.order().getPenaltyPct(), p.getOpenedAt(), p.getDefenseDue(),
                    p.getDefenseText(), p.getDefenseAt(), p.getDecisionDue(), p.getSanction(),
                    p.getSanction() == null ? null : p.getSanction().label(), p.getFinePct(), p.getSuspensionMonths(),
                    p.getImpedimentYears(), p.getDecisionReason(), p.getDecidedAt(), p.getAppealDue(),
                    p.getAppealText(), p.getAppealedAt(), p.getBoardDue(), p.getBoardOutcome(),
                    p.getBoardOutcome() == null ? null : p.getBoardOutcome().label(), p.getBoardReason(),
                    p.getBoardAt(), p.getFinalSanction(),
                    p.getFinalSanction() == null ? null : p.getFinalSanction().label(), p.getFinalFinePct(),
                    p.getFinalMonths(), p.getFinalYears(), p.getEffectiveUntil(), p.getFinalAt(),
                    v.files().stream().map(FileDto::of).toList(),
                    v.previous().stream().map(x -> Summary.of(x, app)).toList(), v.recidivist(),
                    p.canDefend(today), p.canAppeal(today) && p.getSanction() != Sanction.NONE, p.getVersion());
        }
    }

    static LocalDate nextDue(SanctionProcess p) {
        return switch (p.getStatus()) {
            case DEFENSE -> p.getDefenseDue();
            case AWAITING_DECISION -> p.getDecisionDue();
            case APPEAL_WINDOW -> p.getAppealDue();
            case BOARD_REVIEW -> p.getBoardDue();
            case FINAL -> null;
        };
    }

    static String name(SupplierApplication a) {
        String legal = a.getLegalName() == null ? "Fornecedor #" + a.getId() : a.getLegalName();
        return a.getTradeName() != null ? a.getTradeName() + " (" + legal + ")" : legal;
    }
}
