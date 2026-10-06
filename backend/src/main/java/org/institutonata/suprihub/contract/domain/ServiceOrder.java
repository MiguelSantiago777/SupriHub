package org.institutonata.suprihub.contract.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.institutonata.suprihub.contract.domain.ContractEnums.Kind;
import org.institutonata.suprihub.contract.domain.ContractEnums.PaymentMethod;
import org.institutonata.suprihub.contract.domain.ContractEnums.ReceiptKind;
import org.institutonata.suprihub.contract.domain.ContractEnums.Status;

/**
 * Ordem de Fornecimento ou de Serviço (Anexo VIII), criada quando a oportunidade é
 * adjudicada. Acompanha a execução até o pagamento (Manual 4.2, passos 7 a 11).
 */
@Entity
@Table(name = "service_order")
public class ServiceOrder {

    /** Item da ordem, copiado da oportunidade com o preço da proposta (quando houver). */
    @Embeddable
    public static class Item {
        private short position;
        private String description;
        private String unit;
        private BigDecimal quantity;
        @Column(name = "unit_price")
        private BigDecimal unitPrice;

        protected Item() {
        }

        public Item(int position, String description, String unit, BigDecimal quantity, BigDecimal unitPrice) {
            this.position = (short) position;
            this.description = description;
            this.unit = unit;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public int getPosition() {
            return position;
        }

        public String getDescription() {
            return description;
        }

        public String getUnit() {
            return unit;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }
    }

    /** Campos que a equipe completa antes de emitir. */
    public record Terms(Kind kind, String project, String objectDescription, String schedule, String location,
            int paymentDays, BigDecimal upfrontValue, BigDecimal penaltyPct, String notes) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private Long tenderId;
    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;
    @Column(name = "proposal_id", updatable = false)
    private Long proposalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind = Kind.SUPPLY;

    @Column(name = "total_value", nullable = false, updatable = false)
    private BigDecimal totalValue;

    private String project;
    @Column(name = "object_description", nullable = false)
    private String objectDescription;
    private String schedule;
    private String location;
    @Column(name = "payment_days", nullable = false)
    private int paymentDays;
    @Column(name = "upfront_value")
    private BigDecimal upfrontValue;
    @Column(name = "penalty_pct")
    private BigDecimal penaltyPct;
    private String notes;

    @Column(name = "acceptance_based", nullable = false, updatable = false)
    private boolean acceptanceBased;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_order_item", joinColumns = @JoinColumn(name = "service_order_id"))
    @OrderBy("position")
    private List<Item> items = new ArrayList<>();

    @Column(name = "issued_at")
    private Instant issuedAt;
    @Column(name = "issued_by")
    private Long issuedBy;
    @Column(name = "signature_due_date")
    private LocalDate signatureDueDate;
    @Column(name = "signed_at")
    private Instant signedAt;
    @Column(name = "signed_via")
    private String signedVia;
    @Column(name = "signed_by")
    private Long signedBy;
    @Column(name = "signature_ip")
    private String signatureIp;

    @Column(name = "received_at")
    private Instant receivedAt;
    @Column(name = "payment_due_date")
    private LocalDate paymentDueDate;
    private BigDecimal rating;
    @Column(name = "rated_at")
    private Instant ratedAt;

    @Column(name = "invoice_number")
    private String invoiceNumber;
    @Column(name = "invoice_date")
    private LocalDate invoiceDate;
    @Column(name = "invoice_value")
    private BigDecimal invoiceValue;
    @Column(name = "invoiced_at")
    private Instant invoicedAt;

    @Column(name = "paid_on")
    private LocalDate paidOn;
    @Column(name = "paid_value")
    private BigDecimal paidValue;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;
    @Column(name = "paid_recorded_by")
    private Long paidRecordedBy;

    @Column(name = "cancel_reason")
    private String cancelReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ServiceOrder() {
    }

    public ServiceOrder(Long tenderId, Long applicationId, Long proposalId, BigDecimal totalValue,
            boolean acceptanceBased, Terms terms, List<Item> items, Instant now) {
        this.tenderId = tenderId;
        this.applicationId = applicationId;
        this.proposalId = proposalId;
        this.totalValue = totalValue;
        this.acceptanceBased = acceptanceBased;
        this.items.addAll(items);
        this.createdAt = now;
        applyTerms(terms, now);
    }

    public void update(Terms terms, Instant now) {
        require(status == Status.DRAFT || status == Status.EXPIRED, "Só a ordem em preparação pode ser alterada");
        applyTerms(terms, now);
    }

    private void applyTerms(Terms t, Instant now) {
        this.kind = t.kind() == null ? Kind.SUPPLY : t.kind();
        this.project = trim(t.project());
        this.objectDescription = t.objectDescription().trim();
        this.schedule = trim(t.schedule());
        this.location = trim(t.location());
        this.paymentDays = t.paymentDays();
        this.upfrontValue = t.upfrontValue() == null || t.upfrontValue().signum() == 0 ? null : t.upfrontValue();
        this.penaltyPct = t.penaltyPct();
        this.notes = trim(t.notes());
        this.updatedAt = now;
    }

    /**
     * Emite: na Faixa 1 o aceite eletrônico já é a assinatura (Edital 9.2); nas demais, o
     * fornecedor assina até {@code dueDate}.
     */
    public void issue(String number, Long userId, LocalDate dueDate, Instant acceptedAt, Instant now) {
        require(status == Status.DRAFT || status == Status.EXPIRED, "Ordem já emitida");
        if (this.number == null) {
            this.number = number;
        }
        this.issuedAt = now;
        this.issuedBy = userId;
        if (acceptanceBased) {
            this.status = Status.SIGNED;
            this.signedVia = "ACCEPTANCE";
            this.signedAt = acceptedAt == null ? now : acceptedAt;
            this.signatureDueDate = null;
        } else {
            this.status = Status.ISSUED;
            this.signatureDueDate = dueDate;
        }
        this.updatedAt = now;
    }

    public boolean canSign(LocalDate today) {
        return status == Status.ISSUED && signatureDueDate != null && !today.isAfter(signatureDueDate);
    }

    public void sign(Long userId, String ip, Instant now) {
        require(status == Status.ISSUED, "Ordem não está aguardando assinatura");
        this.status = Status.SIGNED;
        this.signedVia = "ELECTRONIC";
        this.signedAt = now;
        this.signedBy = userId;
        this.signatureIp = ip;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        require(status == Status.ISSUED, "Ordem não está aguardando assinatura");
        this.status = Status.EXPIRED;
        this.updatedAt = now;
    }

    /** Termo de Recebimento: o final (definitivo ou recusa) fixa a nota da contratação. */
    public void receive(ReceiptKind kind, BigDecimal average, LocalDate paymentDue, Instant now) {
        require(status.canReceive(), "Ordem " + status.label() + " não pode ser recebida");
        switch (kind) {
            case DEFINITIVE -> {
                this.status = Status.RECEIVED;
                this.receivedAt = now;
                this.paymentDueDate = paymentDue;
            }
            case WITH_RESERVATIONS -> this.status = Status.RECEIVED_WITH_RESERVATIONS;
            case REJECTED -> this.status = Status.REJECTED;
        }
        if (kind.isFinal()) {
            this.rating = average;
            this.ratedAt = now;
        }
        this.updatedAt = now;
    }

    public void invoice(String number, LocalDate date, BigDecimal value, Instant now) {
        require(status == Status.RECEIVED || status == Status.INVOICED, "A nota fiscal vem depois do recebimento definitivo");
        this.invoiceNumber = number.trim();
        this.invoiceDate = date;
        this.invoiceValue = value;
        this.invoicedAt = now;
        this.status = Status.INVOICED;
        this.updatedAt = now;
    }

    public void pay(LocalDate paidOn, BigDecimal value, PaymentMethod method, Long userId, Instant now) {
        require(status == Status.INVOICED, "O pagamento exige a nota fiscal ou o RPA");
        this.paidOn = paidOn;
        this.paidValue = value;
        this.paymentMethod = method;
        this.paidRecordedBy = userId;
        this.status = Status.PAID;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        require(status == Status.DRAFT || status == Status.ISSUED || status == Status.EXPIRED
                || status == Status.SIGNED, "Ordem " + status.label() + " não pode ser cancelada");
        this.status = Status.CANCELLED;
        this.cancelReason = reason.trim();
        this.updatedAt = now;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
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

    public String getNumber() {
        return number;
    }

    public Long getTenderId() {
        return tenderId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getProposalId() {
        return proposalId;
    }

    public Status getStatus() {
        return status;
    }

    public Kind getKind() {
        return kind;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public String getProject() {
        return project;
    }

    public String getObjectDescription() {
        return objectDescription;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getLocation() {
        return location;
    }

    public int getPaymentDays() {
        return paymentDays;
    }

    public BigDecimal getUpfrontValue() {
        return upfrontValue;
    }

    public BigDecimal getPenaltyPct() {
        return penaltyPct;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isAcceptanceBased() {
        return acceptanceBased;
    }

    public List<Item> getItems() {
        return List.copyOf(items);
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public LocalDate getSignatureDueDate() {
        return signatureDueDate;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public String getSignedVia() {
        return signedVia;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public LocalDate getPaymentDueDate() {
        return paymentDueDate;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public Instant getRatedAt() {
        return ratedAt;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public BigDecimal getInvoiceValue() {
        return invoiceValue;
    }

    public Instant getInvoicedAt() {
        return invoicedAt;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public BigDecimal getPaidValue() {
        return paidValue;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "ServiceOrder#" + id + "[" + status + "]";
    }
}
