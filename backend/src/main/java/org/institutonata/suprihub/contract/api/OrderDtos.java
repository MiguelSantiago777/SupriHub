package org.institutonata.suprihub.contract.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.institutonata.suprihub.contract.application.ServiceOrderService.View;
import org.institutonata.suprihub.contract.domain.ContractEnums.FileKind;
import org.institutonata.suprihub.contract.domain.ContractEnums.Kind;
import org.institutonata.suprihub.contract.domain.ContractEnums.PaymentMethod;
import org.institutonata.suprihub.contract.domain.ContractEnums.ReceiptKind;
import org.institutonata.suprihub.contract.domain.ContractEnums.Status;
import org.institutonata.suprihub.contract.domain.ContractRules.Scores;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.contract.domain.ServiceOrderFile;
import org.institutonata.suprihub.contract.domain.ServiceOrderReceipt;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums.Audience;

public final class OrderDtos {

    private OrderDtos() {
    }

    // -------------------------------------------------------------- requisições

    public record TermsRequest(Kind kind, @Size(max = 300) String project,
            @NotBlank(message = "Descreva o objeto.") @Size(max = 3000) String objectDescription,
            @Size(max = 300) String schedule, @Size(max = 300) String location,
            @Min(value = 1, message = "Informe o prazo de pagamento.") int paymentDays,
            @DecimalMin(value = "0", message = "Valor inválido.") BigDecimal upfrontValue,
            BigDecimal penaltyPct, @Size(max = 2000) String notes, long version) {

        ServiceOrder.Terms toTerms() {
            return new ServiceOrder.Terms(kind, project, objectDescription, schedule, location, paymentDays,
                    upfrontValue, penaltyPct, notes);
        }
    }

    public record VersionRequest(long version) {
    }

    public record ReasonRequest(@NotBlank(message = "Escreva o motivo.") @Size(max = 1000) String reason,
            long version) {
    }

    public record ReceiptRequest(@NotNull(message = "Escolha o tipo de recebimento.") ReceiptKind kind,
            @NotNull(message = "Informe a data da execução/entrega.") LocalDate executionDate,
            @Size(max = 2000) String reservations, @Min(1) @Max(365) Integer correctionDays,
            @Size(max = 2000) String rejectReason,
            int scoreDeadline, int scoreQuality, int scoreDocumentation, int scoreService, int scoreSafety,
            @Size(max = 2000) String notes, @Size(max = 200) String evidenceKinds) {

        ServiceOrderReceipt.Data toData() {
            return new ServiceOrderReceipt.Data(kind, executionDate, reservations, correctionDays, rejectReason,
                    new Scores(scoreDeadline, scoreQuality, scoreDocumentation, scoreService, scoreSafety), notes,
                    evidenceKinds);
        }
    }

    public record InvoiceRequest(@Size(max = 60) String number, LocalDate date, BigDecimal value) {
    }

    public record PaymentRequest(LocalDate paidOn, BigDecimal value, PaymentMethod method, long version) {
    }

    public record SignRequest(boolean declarationAccepted) {
    }

    // ------------------------------------------------------------------ resposta

    public record ItemDto(int position, String description, String unit, BigDecimal quantity, BigDecimal unitPrice) {
        static ItemDto of(ServiceOrder.Item i) {
            return new ItemDto(i.getPosition(), i.getDescription(), i.getUnit(), i.getQuantity(), i.getUnitPrice());
        }
    }

    public record ReceiptDto(Long id, ReceiptKind kind, String kindLabel, LocalDate executionDate,
            String reservations, Integer correctionDays, String rejectReason, int scoreDeadline, int scoreQuality,
            int scoreDocumentation, int scoreService, int scoreSafety, BigDecimal average, String notes,
            String evidenceKinds, Instant receivedAt, Instant supplierAckAt) {

        static ReceiptDto of(ServiceOrderReceipt r) {
            Scores s = r.scores();
            return new ReceiptDto(r.getId(), r.getKind(), r.getKind().label(), r.getExecutionDate(),
                    r.getReservations(), r.getCorrectionDays(), r.getRejectReason(), s.deadline(), s.quality(),
                    s.documentation(), s.service(), s.safety(), r.getAverage(), r.getNotes(), r.getEvidenceKinds(),
                    r.getReceivedAt(), r.getSupplierAckAt());
        }
    }

    public record FileDto(Long id, FileKind kind, String kindLabel, String name, long size, Instant uploadedAt) {
        static FileDto of(ServiceOrderFile f) {
            return new FileDto(f.getId(), f.getKind(), f.getKind().label(), f.getFileName(), f.getFileSize(),
                    f.getUploadedAt());
        }
    }

    /** Oportunidade da ordem; na urgência, traz a justificativa e a ciência da Comissão. */
    public record TenderRef(Long id, String number, String title, Band band, String bandLabel, String lineCode,
            String lineName, boolean urgent, String urgencyJustification, LocalDate urgencyAckDue,
            Instant urgencyAcknowledgedAt, String urgencyAckNote) {
        static TenderRef of(Tender t) {
            return new TenderRef(t.getId(), t.getNumber(), t.getTitle(), t.getBand(), t.getBand().label(),
                    t.getSupplyLine().getCode(), t.getSupplyLine().getShortName(), t.isUrgent(),
                    t.getUrgencyJustification(), t.getUrgencyAckDue(), t.getUrgencyAcknowledgedAt(),
                    t.getUrgencyAckNote());
        }
    }

    /** Contratação de urgência (Política art. 13, § 3º): DFD + credenciado escolhido + justificativa. */
    public record UrgentRequest(
            @NotBlank(message = "Dê um título.") @Size(max = 200) String title,
            @NotBlank(message = "Descreva o que precisa.") @Size(max = 5000) String description,
            @NotBlank(message = "Escolha a linha de fornecimento.") String lineCode,
            Integer executionMunicipalityId,
            @Size(max = 300) String location,
            @Size(max = 300) String schedule,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "Informe um valor positivo.")
            BigDecimal value,
            @NotNull(message = "Escolha o credenciado.") Long applicationId,
            @NotBlank(message = "Justifique a urgência.") @Size(max = 3000) String justification,
            @NotNull @Size(min = 1, max = 50, message = "Inclua pelo menos um item.") List<@Valid UrgentItem> items) {

        Tender.Data toData() {
            return new Tender.Data(title, description, location, schedule, null, null, null, Audience.ALL, value,
                    null, Set.of(), items.stream().map(i -> new Tender.Item(i.description(), i.unit(), i.quantity()))
                            .toList());
        }
    }

    public record UrgentItem(@NotBlank(message = "Descreva o item.") @Size(max = 500) String description,
            @NotBlank(message = "Informe a unidade.") @Size(max = 30) String unit,
            @NotNull @DecimalMin(value = "0.01", message = "Quantidade inválida.") BigDecimal quantity) {
    }

    public record AcknowledgeRequest(@Size(max = 3000) String note) {
    }

    /** Urgência aguardando a ciência da Comissão. */
    public record UrgencySummary(Long tenderId, String number, String title, String lineCode, BigDecimal value,
            String justification, LocalDate ackDue, boolean overdue, Long orderId) {
    }

    public record SupplierRef(Long applicationId, String name, String typeLabel, boolean regular) {
        static SupplierRef of(SupplierApplication a, LocalDate today) {
            if (a == null) {
                return null;
            }
            boolean regular = a.isActiveOn(today);
            return new SupplierRef(a.getId(), OrderDtos.name(a), a.getSupplierType() == null ? null : a.getSupplierType().label(),
                    regular);
        }
    }

    public record OrderSummary(Long id, String number, Status status, String statusLabel, String kindLabel,
            Long tenderId, String tenderNumber, String tenderTitle, String supplierName, BigDecimal totalValue,
            LocalDate signatureDueDate, LocalDate paymentDueDate, BigDecimal rating, Instant updatedAt) {
    }

    public record OrderDetail(Long id, String number, Status status, String statusLabel, Kind kind,
            String kindLabel, TenderRef tender, SupplierRef supplier, BigDecimal totalValue, String project,
            String objectDescription, String schedule, String location, int paymentDays, BigDecimal upfrontValue,
            BigDecimal penaltyPct, String notes, boolean acceptanceBased, List<ItemDto> items, Instant issuedAt,
            LocalDate signatureDueDate, Instant signedAt, String signedVia, Instant receivedAt,
            LocalDate paymentDueDate, BigDecimal rating, String invoiceNumber, LocalDate invoiceDate,
            BigDecimal invoiceValue, Instant invoicedAt, LocalDate paidOn, BigDecimal paidValue,
            PaymentMethod paymentMethod, String paymentMethodLabel, String cancelReason, List<ReceiptDto> receipts,
            List<FileDto> files, boolean canSign, LocalDate upfrontPaidOn, BigDecimal upfrontPaidValue,
            String upfrontPaymentMethodLabel, boolean canPayUpfront, BigDecimal remainingValue,
            Long replacesOrderId, long version) {

        static OrderDetail of(View v, LocalDate today) {
            ServiceOrder o = v.order();
            return new OrderDetail(o.getId(), o.getNumber(), o.getStatus(), o.getStatus().label(), o.getKind(),
                    o.getKind().label(), TenderRef.of(v.tender()), SupplierRef.of(v.supplier(), today),
                    o.getTotalValue(), o.getProject(), o.getObjectDescription(), o.getSchedule(), o.getLocation(),
                    o.getPaymentDays(), o.getUpfrontValue(), o.getPenaltyPct(), o.getNotes(), o.isAcceptanceBased(),
                    o.getItems().stream().map(ItemDto::of).toList(), o.getIssuedAt(), o.getSignatureDueDate(),
                    o.getSignedAt(), o.getSignedVia(), o.getReceivedAt(), o.getPaymentDueDate(), o.getRating(),
                    o.getInvoiceNumber(), o.getInvoiceDate(), o.getInvoiceValue(), o.getInvoicedAt(), o.getPaidOn(),
                    o.getPaidValue(), o.getPaymentMethod(),
                    o.getPaymentMethod() == null ? null : o.getPaymentMethod().label(), o.getCancelReason(),
                    v.receipts().stream().map(ReceiptDto::of).toList(), v.files().stream().map(FileDto::of).toList(),
                    o.canSign(today), o.getUpfrontPaidOn(), o.getUpfrontPaidValue(),
                    o.getUpfrontPaymentMethod() == null ? null : o.getUpfrontPaymentMethod().label(),
                    o.canPayUpfront(), o.remainingValue(), o.getReplacesOrderId(), o.getVersion());
        }
    }

    static String name(SupplierApplication a) {
        String legal = a.getLegalName() == null ? "Fornecedor #" + a.getId() : a.getLegalName();
        return a.getTradeName() != null ? a.getTradeName() + " (" + legal + ")" : legal;
    }
}
