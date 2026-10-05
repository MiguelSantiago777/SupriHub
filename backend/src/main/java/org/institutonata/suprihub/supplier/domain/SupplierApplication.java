package org.institutonata.suprihub.supplier.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.institutonata.suprihub.catalog.domain.Municipality;
import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.shared.validation.DocumentNumbers;
import org.institutonata.suprihub.supplier.domain.SupplierEnums.BankAccountType;
import org.institutonata.suprihub.supplier.domain.SupplierEnums.Race;

/**
 * Requerimento de credenciamento (Anexo II) e o resultado da análise de habilitação.
 * As transições de situação ficam aqui; prazos e protocolo chegam já calculados.
 */
@Entity
@Table(name = "supplier_application")
public class SupplierApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    private String protocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_type")
    private SupplierType supplierType;

    @Column(name = "legal_name")
    private String legalName;
    @Column(name = "trade_name")
    private String tradeName;
    @Column(name = "document_number")
    private String documentNumber;
    @Column(name = "birth_date")
    private LocalDate birthDate;
    private String street;
    @Column(name = "address_number")
    private String addressNumber;
    @Column(name = "address_complement")
    private String addressComplement;
    private String district;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "municipality_id")
    private Municipality municipality;

    private String cep;
    private String phone;
    @Column(name = "contact_email")
    private String contactEmail;
    private String website;
    @Column(name = "representative_name")
    private String representativeName;
    @Column(name = "representative_cpf")
    private String representativeCpf;
    @Column(name = "representative_role")
    private String representativeRole;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "supplier_application_line", joinColumns = @JoinColumn(name = "application_id"),
            inverseJoinColumns = @JoinColumn(name = "supply_line_id"))
    private Set<SupplyLine> lines = new HashSet<>();

    @Column(name = "lines_description")
    private String linesDescription;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "supplier_application_territory", joinColumns = @JoinColumn(name = "application_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "territory")
    private Set<Territory> territories = EnumSet.noneOf(Territory.class);

    @Column(name = "territories_note")
    private String territoriesNote;

    @Column(name = "bank_name")
    private String bankName;
    @Column(name = "bank_branch")
    private String bankBranch;
    @Column(name = "bank_account")
    private String bankAccount;
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_account_type")
    private BankAccountType bankAccountType;
    @Column(name = "pix_key")
    private String pixKey;
    @Column(name = "account_holder")
    private String accountHolder;

    @Column(name = "woman_led", nullable = false)
    private boolean womanLed;
    @Column(name = "publish_woman_led", nullable = false)
    private boolean publishWomanLed;
    @Enumerated(EnumType.STRING)
    private Race race;
    @Column(name = "cad_unico", nullable = false)
    private boolean cadUnico;
    private String nis;

    @Column(name = "declarations_accepted_at")
    private Instant declarationsAcceptedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "analysis_due_date")
    private LocalDate analysisDueDate;
    @Column(name = "adjustment_message")
    private String adjustmentMessage;
    @Column(name = "adjustment_due_date")
    private LocalDate adjustmentDueDate;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decided_by")
    private Long decidedBy;
    @Column(name = "decision_reason")
    private String decisionReason;
    @Column(name = "accreditation_valid_until")
    private LocalDate accreditationValidUntil;
    @Column(name = "appeal_deadline")
    private LocalDate appealDeadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected SupplierApplication() {
    }

    public SupplierApplication(Long userId, String legalName, String contactEmail, Instant now) {
        this.userId = userId;
        this.legalName = legalName;
        this.contactEmail = contactEmail;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------ edição

    /** Grava o rascunho. Só com o requerimento nas mãos do fornecedor. */
    public void update(ApplicationData d, Municipality municipality, Set<SupplyLine> lines, Instant now) {
        requireEditable();
        this.supplierType = d.supplierType();
        this.legalName = trim(d.legalName());
        this.tradeName = trim(d.tradeName());
        this.documentNumber = digits(d.documentNumber());
        this.birthDate = d.supplierType() == SupplierType.INDIVIDUAL ? d.birthDate() : null;
        this.street = trim(d.street());
        this.addressNumber = trim(d.addressNumber());
        this.addressComplement = trim(d.addressComplement());
        this.district = trim(d.district());
        this.municipality = municipality;
        this.cep = digits(d.cep());
        this.phone = digits(d.phone());
        this.contactEmail = trim(d.contactEmail());
        this.website = trim(d.website());
        this.representativeName = trim(d.representativeName());
        this.representativeCpf = digits(d.representativeCpf());
        this.representativeRole = trim(d.representativeRole());
        this.lines.clear();
        this.lines.addAll(lines);
        this.linesDescription = trim(d.linesDescription());
        this.territories.clear();
        this.territories.addAll(d.territories());
        this.territoriesNote = trim(d.territoriesNote());
        this.bankName = trim(d.bankName());
        this.bankBranch = trim(d.bankBranch());
        this.bankAccount = trim(d.bankAccount());
        this.bankAccountType = d.bankAccountType();
        this.pixKey = trim(d.pixKey());
        this.accountHolder = trim(d.accountHolder());
        this.womanLed = d.womanLed();
        this.publishWomanLed = d.womanLed() && d.publishWomanLed();
        this.race = d.race();
        this.cadUnico = d.cadUnico();
        this.nis = d.cadUnico() ? digits(d.nis()) : null;
        if (d.declarationsAccepted() && declarationsAcceptedAt == null) {
            this.declarationsAcceptedAt = now;
        } else if (!d.declarationsAccepted()) {
            this.declarationsAcceptedAt = null;
        }
        this.updatedAt = now;
    }

    public void requireEditable() {
        if (!status.isEditableBySupplier()) {
            throw new IllegalStateException("Requerimento " + status + " não pode ser alterado pelo fornecedor");
        }
    }

    // ------------------------------------------------------------- transições

    /** Envio (ou reenvio) para análise. O protocolo é atribuído só no primeiro envio. */
    public void submit(String newProtocol, LocalDate analysisDue, Instant now) {
        requireEditable();
        if (protocol == null) {
            protocol = newProtocol;
        }
        status = ApplicationStatus.SUBMITTED;
        submittedAt = now;
        analysisDueDate = analysisDue;
        adjustmentDueDate = null;
        decidedAt = null;
        decidedBy = null;
        decisionReason = null;
        appealDeadline = null;
        updatedAt = now;
    }

    public void requestAdjustment(String message, LocalDate dueDate, Instant now) {
        requireUnderAnalysis();
        status = ApplicationStatus.ADJUSTMENT_REQUESTED;
        adjustmentMessage = message.trim();
        adjustmentDueDate = dueDate;
        updatedAt = now;
    }

    public void approve(long reviewerId, String notes, LocalDate validUntil, Instant now) {
        requireUnderAnalysis();
        status = ApplicationStatus.APPROVED;
        decidedAt = now;
        decidedBy = reviewerId;
        decisionReason = trim(notes);
        accreditationValidUntil = validUntil;
        updatedAt = now;
    }

    public void reject(long reviewerId, String reason, LocalDate appealDeadline, Instant now) {
        requireUnderAnalysis();
        status = ApplicationStatus.REJECTED;
        decidedAt = now;
        decidedBy = reviewerId;
        decisionReason = reason.trim();
        this.appealDeadline = appealDeadline;
        updatedAt = now;
    }

    /** Recurso provido (reconsideração da Comissão ou decisão da Diretoria): passa a habilitado. */
    public void approveOnAppeal(long deciderId, String reason, LocalDate validUntil, Instant now) {
        if (status != ApplicationStatus.REJECTED) {
            throw new IllegalStateException("Recurso só reverte inabilitação; situação: " + status);
        }
        status = ApplicationStatus.APPROVED;
        decidedAt = now;
        decidedBy = deciderId;
        decisionReason = reason.trim();
        accreditationValidUntil = validUntil;
        appealDeadline = null;
        updatedAt = now;
    }

    /** Ainda dentro do prazo de recurso (inclusive o último dia). */
    public boolean canAppeal(LocalDate today) {
        return status == ApplicationStatus.REJECTED && appealDeadline != null && !today.isAfter(appealDeadline);
    }

    private void requireUnderAnalysis() {
        if (status != ApplicationStatus.SUBMITTED) {
            throw new IllegalStateException("Só requerimentos em análise recebem decisão; situação: " + status);
        }
    }

    // ---------------------------------------------------------------- consultas

    public List<SupplyLine> sortedLines() {
        return lines.stream().sorted(Comparator.comparingInt(SupplyLine::getDisplayOrder)).toList();
    }

    public boolean declarationsAccepted() {
        return declarationsAcceptedAt != null;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String digits(String s) {
        if (s == null) {
            return null;
        }
        String d = DocumentNumbers.digits(s);
        return d.isEmpty() ? null : d;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getProtocol() {
        return protocol;
    }

    public SupplierType getSupplierType() {
        return supplierType;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getStreet() {
        return street;
    }

    public String getAddressNumber() {
        return addressNumber;
    }

    public String getAddressComplement() {
        return addressComplement;
    }

    public String getDistrict() {
        return district;
    }

    public Municipality getMunicipality() {
        return municipality;
    }

    public String getCep() {
        return cep;
    }

    public String getPhone() {
        return phone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getWebsite() {
        return website;
    }

    public String getRepresentativeName() {
        return representativeName;
    }

    public String getRepresentativeCpf() {
        return representativeCpf;
    }

    public String getRepresentativeRole() {
        return representativeRole;
    }

    public Set<SupplyLine> getLines() {
        return Set.copyOf(lines);
    }

    public String getLinesDescription() {
        return linesDescription;
    }

    public Set<Territory> getTerritories() {
        return territories.isEmpty() ? Set.of() : EnumSet.copyOf(territories);
    }

    public String getTerritoriesNote() {
        return territoriesNote;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankBranch() {
        return bankBranch;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public BankAccountType getBankAccountType() {
        return bankAccountType;
    }

    public String getPixKey() {
        return pixKey;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public boolean isWomanLed() {
        return womanLed;
    }

    public Race getRace() {
        return race;
    }

    public boolean isPublishWomanLed() {
        return publishWomanLed;
    }

    public LocalDate getAppealDeadline() {
        return appealDeadline;
    }

    public boolean isCadUnico() {
        return cadUnico;
    }

    public String getNis() {
        return nis;
    }

    public Instant getDeclarationsAcceptedAt() {
        return declarationsAcceptedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public LocalDate getAnalysisDueDate() {
        return analysisDueDate;
    }

    public String getAdjustmentMessage() {
        return adjustmentMessage;
    }

    public LocalDate getAdjustmentDueDate() {
        return adjustmentDueDate;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public LocalDate getAccreditationValidUntil() {
        return accreditationValidUntil;
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
        return "SupplierApplication#" + id; // nunca expor dados pessoais em logs
    }
}
