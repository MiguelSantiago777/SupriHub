package org.institutonata.suprihub.supplier.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.shared.privacy.DocumentMasker;
import org.institutonata.suprihub.supplier.domain.Appeal;
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness.Issue;
import org.institutonata.suprihub.supplier.domain.ApplicationData;
import org.institutonata.suprihub.supplier.domain.ApplicationEvent;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.institutonata.suprihub.supplier.domain.SupplierEnums.BankAccountType;
import org.institutonata.suprihub.supplier.domain.SupplierEnums.Race;
import org.institutonata.suprihub.supplier.domain.SupplierType;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    /** Rascunho do Anexo II. Só limites de tamanho aqui; a completude é conferida no envio. */
    public record ApplicationRequest(
            SupplierType supplierType,
            @Size(max = 200) String legalName,
            @Size(max = 200) String tradeName,
            @Size(max = 20) String documentNumber,
            LocalDate birthDate,
            @Size(max = 200) String street,
            @Size(max = 20) String addressNumber,
            @Size(max = 100) String addressComplement,
            @Size(max = 100) String district,
            Integer municipalityId,
            @Size(max = 10) String cep,
            @Size(max = 20) String phone,
            @Size(max = 254) String contactEmail,
            @Size(max = 300) String website,
            @Size(max = 200) String representativeName,
            @Size(max = 14) String representativeCpf,
            @Size(max = 100) String representativeRole,
            @Size(max = 20) Set<String> lineCodes,
            @Size(max = 3000, message = "Use no máximo 3.000 caracteres.") String linesDescription,
            Set<Territory> territories,
            @Size(max = 500) String territoriesNote,
            @Size(max = 100) String bankName,
            @Size(max = 10) String bankBranch,
            @Size(max = 20) String bankAccount,
            BankAccountType bankAccountType,
            @Size(max = 140) String pixKey,
            @Size(max = 200) String accountHolder,
            boolean womanLed,
            boolean publishWomanLed,
            Race race,
            boolean cadUnico,
            @Size(max = 11) String nis,
            boolean declarationsAccepted,
            long version) {

        ApplicationData toData() {
            return new ApplicationData(supplierType, legalName, tradeName, documentNumber, birthDate, street,
                    addressNumber, addressComplement, district, municipalityId, cep, phone, contactEmail, website,
                    representativeName, representativeCpf, representativeRole, lineCodes, linesDescription,
                    territories, territoriesNote, bankName, bankBranch, bankAccount, bankAccountType, pixKey,
                    accountHolder, womanLed, publishWomanLed, race, cadUnico, nis, declarationsAccepted);
        }

        @Override
        public String toString() {
            return "ApplicationRequest[***]";
        }
    }

    public record VersionRequest(long version) {
    }

    public record DecisionRequest(@Size(max = 3000) String message, long version) {
    }

    public record RequiredMessageRequest(
            @NotBlank(message = "Escreva a mensagem para o fornecedor.") @Size(max = 3000) String message,
            long version) {
    }

    public record DocumentDto(Long id, DocumentType type, String label, String filename, String contentType,
            long sizeBytes, Instant uploadedAt) {
        static DocumentDto of(SupplierDocument d) {
            return new DocumentDto(d.getId(), d.getType(), d.getType().label(), d.getOriginalFilename(),
                    d.getContentType(), d.getSizeBytes(), d.getUploadedAt());
        }
    }

    /** Item do checklist de documentos para o tipo de negócio escolhido. */
    public record ChecklistItem(DocumentType type, String label, DocumentType.Requirement requirement,
            DocumentDto document) {
    }

    public record HistoryDto(ApplicationStatus status, String statusLabel, String message, String actor,
            Instant occurredAt) {
        static HistoryDto of(ApplicationEvent e, SupplierApplication app) {
            String actor = e.getActorId() == null ? "Sistema"
                    : e.getActorId().equals(app.getUserId()) ? "Fornecedor" : "Comissão de Credenciamento";
            return new HistoryDto(e.getStatus(), e.getStatus().label(), e.getMessage(), actor, e.getOccurredAt());
        }
    }

    public record IssueDto(String field, String message) {
        static IssueDto of(Issue i) {
            return new IssueDto(i.field(), i.message());
        }
    }

    public record LineDto(String code, String shortName, boolean allowsIndividual) {
        static LineDto of(SupplyLine l) {
            return new LineDto(l.getCode(), l.getShortName(), l.isAllowsIndividual());
        }
    }

    /** Requerimento completo (fornecedor dono e equipe interna). */
    public record ApplicationDto(
            Long id,
            ApplicationStatus status,
            String statusLabel,
            String protocol,
            SupplierType supplierType,
            String supplierTypeLabel,
            String legalName,
            String tradeName,
            String documentNumber,
            LocalDate birthDate,
            String street,
            String addressNumber,
            String addressComplement,
            String district,
            Integer municipalityId,
            String municipalityName,
            String cep,
            String phone,
            String contactEmail,
            String website,
            String representativeName,
            String representativeCpf,
            String representativeRole,
            List<LineDto> lines,
            String linesDescription,
            Set<Territory> territories,
            String territoriesNote,
            String bankName,
            String bankBranch,
            String bankAccount,
            BankAccountType bankAccountType,
            String pixKey,
            String accountHolder,
            boolean womanLed,
            boolean publishWomanLed,
            Race race,
            boolean cadUnico,
            String nis,
            boolean declarationsAccepted,
            Instant declarationsAcceptedAt,
            Instant submittedAt,
            LocalDate analysisDueDate,
            String adjustmentMessage,
            LocalDate adjustmentDueDate,
            Instant decidedAt,
            String decisionReason,
            LocalDate accreditationValidUntil,
            LocalDate appealDeadline,
            boolean canAppeal,
            AppealDto appeal,
            boolean editable,
            List<ChecklistItem> checklist,
            List<HistoryDto> history,
            List<IssueDto> pendingItems,
            long version) {

        /**
         * @param appeal recurso mais recente (ou {@code null})
         * @param today  data de referência para o prazo de recurso
         */
        static ApplicationDto of(SupplierApplication a, List<SupplierDocument> docs, List<ApplicationEvent> events,
                List<Issue> pending, Appeal appeal, LocalDate today) {
            boolean openAppeal = appeal != null && appeal.getStatus().isOpen();
            boolean appealedThisDecision = appeal != null && a.getDecidedAt() != null
                    && !appeal.getFiledAt().isBefore(a.getDecidedAt());
            Map<DocumentType, SupplierDocument> byType = new java.util.EnumMap<>(DocumentType.class);
            docs.forEach(d -> byType.put(d.getType(), d));
            List<ChecklistItem> checklist = Arrays.stream(DocumentType.values())
                    .filter(t -> a.getSupplierType() == null ? byType.containsKey(t) : t.appliesTo(a.getSupplierType())
                            || byType.containsKey(t))
                    .map(t -> new ChecklistItem(t, t.label(),
                            a.getSupplierType() == null ? DocumentType.Requirement.OPTIONAL
                                    : t.requirementFor(a.getSupplierType()),
                            byType.containsKey(t) ? DocumentDto.of(byType.get(t)) : null))
                    .toList();
            return new ApplicationDto(a.getId(), a.getStatus(), a.getStatus().label(), a.getProtocol(),
                    a.getSupplierType(), a.getSupplierType() == null ? null : a.getSupplierType().label(),
                    a.getLegalName(), a.getTradeName(), a.getDocumentNumber(), a.getBirthDate(), a.getStreet(),
                    a.getAddressNumber(), a.getAddressComplement(), a.getDistrict(),
                    a.getMunicipality() == null ? null : a.getMunicipality().getId(),
                    a.getMunicipality() == null ? null : a.getMunicipality().getName(),
                    a.getCep(), a.getPhone(), a.getContactEmail(), a.getWebsite(), a.getRepresentativeName(),
                    a.getRepresentativeCpf(), a.getRepresentativeRole(),
                    a.sortedLines().stream().map(LineDto::of).toList(), a.getLinesDescription(),
                    a.getTerritories(), a.getTerritoriesNote(), a.getBankName(), a.getBankBranch(),
                    a.getBankAccount(), a.getBankAccountType(), a.getPixKey(), a.getAccountHolder(),
                    a.isWomanLed(), a.isPublishWomanLed(), a.getRace(), a.isCadUnico(), a.getNis(),
                    a.declarationsAccepted(),
                    a.getDeclarationsAcceptedAt(), a.getSubmittedAt(), a.getAnalysisDueDate(),
                    a.getAdjustmentMessage(), a.getAdjustmentDueDate(), a.getDecidedAt(), a.getDecisionReason(),
                    a.getAccreditationValidUntil(), a.getAppealDeadline(),
                    a.canAppeal(today) && !appealedThisDecision && !openAppeal,
                    appeal == null ? null : AppealDto.of(appeal),
                    a.getStatus().isEditableBySupplier() && !openAppeal, checklist,
                    events.stream().map(e -> HistoryDto.of(e, a)).toList(),
                    pending.stream().map(IssueDto::of).toList(), a.getVersion());
        }

        @Override
        public String toString() {
            return "ApplicationDto#" + id;
        }
    }

    /** Linha da fila de análise. Documento mascarado (a íntegra fica no detalhe). */
    public record ApplicationSummary(
            Long id,
            String protocol,
            ApplicationStatus status,
            String legalName,
            String tradeName,
            SupplierType supplierType,
            String supplierTypeLabel,
            String documentMasked,
            String municipalityName,
            List<String> lineCodes,
            boolean womanLed,
            Instant submittedAt,
            LocalDate analysisDueDate,
            LocalDate adjustmentDueDate,
            LocalDate accreditationValidUntil,
            boolean overdue) {

        static ApplicationSummary of(SupplierApplication a, LocalDate today) {
            LocalDate due = a.getStatus() == ApplicationStatus.SUBMITTED ? a.getAnalysisDueDate()
                    : a.getStatus() == ApplicationStatus.ADJUSTMENT_REQUESTED ? a.getAdjustmentDueDate() : null;
            return new ApplicationSummary(a.getId(), a.getProtocol(), a.getStatus(), a.getLegalName(),
                    a.getTradeName(), a.getSupplierType(),
                    a.getSupplierType() == null ? null : a.getSupplierType().label(),
                    a.getDocumentNumber() == null ? null : DocumentMasker.mask(a.getDocumentNumber()),
                    a.getMunicipality() == null ? null : a.getMunicipality().getName(),
                    a.sortedLines().stream().map(SupplyLine::getCode).toList(), a.isWomanLed(), a.getSubmittedAt(),
                    a.getAnalysisDueDate(), a.getAdjustmentDueDate(), a.getAccreditationValidUntil(),
                    due != null && due.isBefore(today));
        }
    }

    // ------------------------------------------------------------------ recurso

    public record AttachmentDto(String filename, String contentType, long sizeBytes) {
    }

    public record AppealDto(
            Long id,
            Appeal.Status status,
            String statusLabel,
            String reasons,
            String request,
            AttachmentDto attachment,
            Instant filedAt,
            LocalDate reconsiderationDue,
            String commissionReason,
            Instant commissionDecidedAt,
            LocalDate boardDue,
            String boardReason,
            Instant boardDecidedAt,
            long version) {

        static AppealDto of(Appeal a) {
            Appeal.Attachment at = a.attachment();
            return new AppealDto(a.getId(), a.getStatus(), a.getStatus().label(), a.getReasons(), a.getRequest(),
                    at == null ? null : new AttachmentDto(at.filename(), at.contentType(), at.sizeBytes()),
                    a.getFiledAt(), a.getReconsiderationDue(), a.getCommissionReason(), a.getCommissionDecidedAt(),
                    a.getBoardDue(), a.getBoardReason(), a.getBoardDecidedAt(), a.getVersion());
        }
    }

    public record AppealSummary(
            Long id,
            Appeal.Status status,
            String statusLabel,
            Long applicationId,
            String protocol,
            String legalName,
            String supplierTypeLabel,
            Instant filedAt,
            LocalDate dueDate,
            boolean overdue) {

        static AppealSummary of(Appeal a, SupplierApplication app, LocalDate today) {
            LocalDate due = a.getStatus() == Appeal.Status.FILED ? a.getReconsiderationDue()
                    : a.getStatus() == Appeal.Status.FORWARDED ? a.getBoardDue() : null;
            return new AppealSummary(a.getId(), a.getStatus(), a.getStatus().label(), app.getId(),
                    app.getProtocol(), app.getLegalName(),
                    app.getSupplierType() == null ? null : app.getSupplierType().label(), a.getFiledAt(), due,
                    due != null && due.isBefore(today));
        }
    }

    public record AppealDetail(AppealDto appeal, ApplicationDto application) {
    }

    public record AppealDecisionRequest(
            @NotBlank(message = "Escreva a motivação.") @Size(max = 3000) String reason,
            long version) {
    }

    public record BoardDecisionRequest(
            boolean granted,
            @NotBlank(message = "Escreva a motivação.") @Size(max = 3000) String reason,
            long version) {
    }

    // ---------------------------------------------------- cadastro de credenciados

    /**
     * Credenciado vigente, para a equipe interna (D-027): inclui contato para a Comissão e os
     * gestores acionarem o fornecedor. Documento mascarado; dados bancários, raça/cor e
     * CadÚnico ficam só no detalhe do requerimento.
     */
    public record AccreditedSupplier(
            Long id,
            String name,
            String legalName,
            String supplierTypeLabel,
            String documentMasked,
            String municipalityName,
            String phone,
            String contactEmail,
            List<LineDto> lines,
            Set<Territory> territories,
            boolean womanLed,
            LocalDate validUntil) {

        static AccreditedSupplier of(SupplierApplication a) {
            String name = a.getTradeName() != null ? a.getTradeName() : a.getLegalName();
            return new AccreditedSupplier(a.getId(), name, a.getLegalName(),
                    a.getSupplierType() == null ? null : a.getSupplierType().label(),
                    a.getDocumentNumber() == null ? null : DocumentMasker.mask(a.getDocumentNumber()),
                    a.getMunicipality() == null ? null : a.getMunicipality().getName(), a.getPhone(),
                    a.getContactEmail(), a.sortedLines().stream().map(LineDto::of).toList(), a.getTerritories(),
                    a.isWomanLed(), a.getAccreditationValidUntil());
        }

        @Override
        public String toString() {
            return "AccreditedSupplier#" + id;
        }
    }
}
