package org.institutonata.suprihub.tender.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.catalog.domain.TerritoryResolver;
import org.institutonata.suprihub.proposal.domain.Proposal;
import org.institutonata.suprihub.proposal.domain.ProposalRules.Mode;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums.Audience;
import org.institutonata.suprihub.tender.domain.TenderEnums.InvitationStatus;
import org.institutonata.suprihub.tender.domain.TenderEnums.Status;
import org.institutonata.suprihub.tender.domain.TenderInvitation;
import org.institutonata.suprihub.tender.domain.TenderItem;

public final class TenderDtos {

    private TenderDtos() {
    }

    // ------------------------------------------------------------------ Comissão

    public record ItemRequest(
            @NotBlank(message = "Descreva o item.") @Size(max = 500) String description,
            @NotBlank(message = "Informe a unidade.") @Size(max = 30) String unit,
            @NotNull(message = "Informe a quantidade.") @DecimalMin(value = "0.01", message = "Quantidade inválida.")
            BigDecimal quantity) {
    }

    public record TenderRequest(
            @NotBlank(message = "Dê um título à oportunidade.") @Size(max = 200) String title,
            @NotBlank(message = "Descreva o que precisa.") @Size(max = 5000) String description,
            @NotBlank(message = "Escolha a linha de fornecimento.") String lineCode,
            Integer executionMunicipalityId,
            @Size(max = 300) String location,
            @Size(max = 300) String schedule,
            @Size(max = 300) String executionDeadline,
            @Size(max = 3000) String requirements,
            @Size(max = 500) String paymentTerms,
            Audience audience,
            @NotNull(message = "Informe o valor estimado.")
            @DecimalMin(value = "0.01", message = "Informe um valor positivo.") BigDecimal estimatedValue,
            Instant proposalDeadline,
            Set<Territory> territories,
            @Valid @Size(max = 50) List<ItemRequest> items,
            long version) {

        Tender.Data toData() {
            return new Tender.Data(title, description, location, schedule, executionDeadline, requirements,
                    paymentTerms, audience, estimatedValue, proposalDeadline,
                    territories == null ? Set.of() : territories,
                    items == null ? List.of() : items.stream()
                            .map(i -> new Tender.Item(i.description(), i.unit(), i.quantity())).toList());
        }
    }

    public record VersionRequest(long version) {
    }

    public record ReasonRequest(@NotBlank(message = "Escreva o motivo.") @Size(max = 1000) String reason,
            long version) {
    }

    public record TerritoriesRequest(@NotNull Set<Territory> territories, long version) {
    }

    public record LineDto(String code, String shortName) {
    }

    public record ItemDto(Long id, int position, String description, String unit, BigDecimal quantity) {
        static ItemDto of(TenderItem i) {
            return new ItemDto(i.getId(), i.getPosition(), i.getDescription(), i.getUnit(), i.getQuantity());
        }
    }

    public record TenderSummary(Long id, String number, Status status, String statusLabel, String title,
            String lineCode, String lineName, Band band, String bandLabel, BigDecimal estimatedValue,
            Instant proposalDeadline, boolean deadlinePassed, long proposalCount, String pendingSupplier,
            LocalDate pendingDueDate, Instant publishedAt, Instant createdAt) {
    }

    public record InvitationDto(Long id, Long applicationId, String supplierName, InvitationStatus status,
            String statusLabel, Instant invitedAt, LocalDate dueDate, Instant respondedAt, String declineReason,
            BigDecimal proposedTotal) {

        static InvitationDto of(TenderInvitation i, SupplierApplication app, Proposal p) {
            return new InvitationDto(i.getId(), i.getApplicationId(), app == null ? null : name(app),
                    i.getStatus(), i.getStatus().label(), i.getInvitedAt(), i.getDueDate(), i.getRespondedAt(),
                    i.getDeclineReason(), p == null ? null : p.getTotalValue());
        }
    }

    /** Linha do mapa de propostas (só depois do prazo). */
    public record ProposalRow(Long id, Long applicationId, String supplierName, String supplierTypeLabel,
            boolean womanLed, Territory seatTerritory, Mode mode, String modeLabel, BigDecimal totalValue,
            Map<Long, BigDecimal> unitPrices, String executionDeadline, int validityDays, String specialConditions,
            String notes, String fileName, Instant submittedAt, String disqualifiedReason) {

        static ProposalRow of(Proposal p, SupplierApplication app, Tender t) {
            Territory seat = app == null || app.getMunicipality() == null ? null
                    : TerritoryResolver.resolve(app.getMunicipality().toRef(), t.getExecutionMunicipality() == null
                            ? null : t.getExecutionMunicipality().toRef()).territory();
            return new ProposalRow(p.getId(), p.getApplicationId(), app == null ? null : name(app),
                    app == null || app.getSupplierType() == null ? null : app.getSupplierType().label(),
                    app != null && app.isWomanLed(), seat, p.getMode(), p.getMode().label(), p.getTotalValue(),
                    p.getUnitPrices(), p.getExecutionDeadline(), p.getValidityDays(), p.getSpecialConditions(),
                    p.getNotes(), p.hasFile() ? p.file().name() : null, p.getSubmittedAt(),
                    p.getDisqualifiedReason());
        }
    }

    public record TenderDetail(Long id, String number, Status status, String statusLabel, String title,
            String description, LineDto line, Integer executionMunicipalityId, String executionMunicipalityName,
            String location, String schedule, String executionDeadline, String requirements, String paymentTerms,
            Audience audience, String audienceLabel, BigDecimal estimatedValue, Band band, String bandLabel,
            String selectionMode, Instant proposalDeadline, boolean deadlinePassed, Instant publishedAt,
            String cancelReason, Set<Territory> territories, List<ItemDto> items, List<InvitationDto> invitations,
            boolean proposalsSealed, long proposalCount, List<ProposalRow> proposals, int eligibleCount,
            long version) {
    }

    // ---------------------------------------------------------------- Fornecedor

    public record ProposalRequest(
            @NotNull(message = "Escolha como vai enviar a proposta.") Mode mode,
            Map<Long, BigDecimal> unitPrices,
            BigDecimal totalValue,
            @Size(max = 300) String executionDeadline,
            @Min(value = 1, message = "Informe a validade.") @Max(365) int validityDays,
            @Size(max = 2000) String specialConditions,
            @Size(max = 2000) String notes,
            boolean declarationAccepted) {

        @Override
        public String toString() {
            return "ProposalRequest[" + mode + "]";
        }
    }

    public record DeclineRequest(@Size(max = 500) String reason) {
    }

    public record MyInvitation(InvitationStatus status, String statusLabel, LocalDate dueDate) {
        static MyInvitation of(TenderInvitation i) {
            return i == null ? null : new MyInvitation(i.getStatus(), i.getStatus().label(), i.getDueDate());
        }
    }

    public record MyProposal(Long id, Mode mode, String modeLabel, BigDecimal totalValue,
            Map<Long, BigDecimal> unitPrices, String executionDeadline, int validityDays, String specialConditions,
            String notes, String fileName, Instant submittedAt, Instant updatedAt) {

        static MyProposal of(Proposal p) {
            return p == null ? null : new MyProposal(p.getId(), p.getMode(), p.getMode().label(), p.getTotalValue(),
                    p.getUnitPrices(), p.getExecutionDeadline(), p.getValidityDays(), p.getSpecialConditions(),
                    p.getNotes(), p.hasFile() ? p.file().name() : null, p.getSubmittedAt(), p.getUpdatedAt());
        }
    }

    /** Oportunidade vista pelo fornecedor (sem o valor estimado do Instituto). */
    public record Opportunity(Long id, String number, Status status, String title, String description,
            LineDto line, String executionMunicipalityName, String location, String schedule,
            String executionDeadline, String requirements, String paymentTerms, String audienceLabel, Band band,
            String bandLabel, String selectionMode, Instant proposalDeadline, boolean deadlinePassed,
            Set<Territory> territories, List<ItemDto> items, MyInvitation invitation, MyProposal proposal,
            boolean canSubmit, boolean canWithdraw, int minValidityDays) {
    }

    static String name(SupplierApplication a) {
        return a.getTradeName() != null ? a.getTradeName() + " (" + a.getLegalName() + ")" : a.getLegalName();
    }
}
