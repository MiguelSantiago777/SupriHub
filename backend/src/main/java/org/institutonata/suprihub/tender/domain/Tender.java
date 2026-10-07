package org.institutonata.suprihub.tender.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.institutonata.suprihub.catalog.domain.Municipality;
import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.tender.domain.TenderEnums.Audience;
import org.institutonata.suprihub.tender.domain.TenderEnums.Status;

/**
 * Oportunidade (DFD-NATA, parte 5 — Anexo VI). Faixas 2 e 3 recebem propostas de todos os
 * elegíveis até o prazo; a Faixa 1 segue o rodízio ({@link TenderInvitation}).
 */
@Entity
@Table(name = "tender")
public class Tender {

    /** Dados editáveis do rascunho. */
    public record Data(String title, String description, String location, String schedule,
            String executionDeadline, String requirements, String paymentTerms, Audience audience,
            BigDecimal estimatedValue, Instant proposalDeadline, Set<Territory> territories, List<Item> items) {
    }

    public record Item(String description, String unit, BigDecimal quantity) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "supply_line_id")
    private SupplyLine supplyLine;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "execution_municipality_id")
    private Municipality executionMunicipality;

    private String location;
    private String schedule;
    @Column(name = "execution_deadline")
    private String executionDeadline;
    private String requirements;
    @Column(name = "payment_terms")
    private String paymentTerms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience = Audience.ALL;

    @Column(name = "estimated_value", nullable = false)
    private BigDecimal estimatedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Band band;

    @Column(name = "proposal_deadline")
    private Instant proposalDeadline;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "cancel_reason")
    private String cancelReason;

    // Contratação de urgência (Política art. 13, § 3º; Manual 4.3)
    @Column(nullable = false)
    private boolean urgent;
    @Column(name = "urgency_justification")
    private String urgencyJustification;
    @Column(name = "urgency_ack_due")
    private LocalDate urgencyAckDue;
    @Column(name = "urgency_acknowledged_at")
    private Instant urgencyAcknowledgedAt;
    @Column(name = "urgency_acknowledged_by")
    private Long urgencyAcknowledgedBy;
    @Column(name = "urgency_ack_note")
    private String urgencyAckNote;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tender_territory", joinColumns = @JoinColumn(name = "tender_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "territory")
    private Set<Territory> territories = EnumSet.noneOf(Territory.class);

    @OneToMany(mappedBy = "tender", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position")
    private List<TenderItem> items = new ArrayList<>();

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Tender() {
    }

    public Tender(Long createdBy, Instant now) {
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Grava o rascunho; a faixa vem calculada pelo {@link BandPolicy}. */
    public void update(Data d, SupplyLine line, Municipality executionMunicipality, Band band, Instant now) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Só rascunhos podem ser alterados");
        }
        this.title = d.title().trim();
        this.description = d.description().trim();
        this.supplyLine = line;
        this.executionMunicipality = executionMunicipality;
        this.location = trim(d.location());
        this.schedule = trim(d.schedule());
        this.executionDeadline = trim(d.executionDeadline());
        this.requirements = trim(d.requirements());
        this.paymentTerms = trim(d.paymentTerms());
        this.audience = d.audience() == null ? Audience.ALL : d.audience();
        this.estimatedValue = d.estimatedValue();
        this.band = band;
        this.proposalDeadline = band == Band.FAIXA_1 ? null : d.proposalDeadline();
        this.territories.clear();
        this.territories.addAll(d.territories());
        this.items.clear();
        int pos = 1;
        for (Item i : d.items()) {
            this.items.add(new TenderItem(this, pos++, i.description(), i.unit(), i.quantity()));
        }
        this.updatedAt = now;
    }

    public void publish(String number, Instant now) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Oportunidade já publicada");
        }
        this.number = number;
        this.status = Status.OPEN;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        if (status == Status.CANCELLED || status == Status.AWARDED) {
            throw new IllegalStateException("Oportunidade " + status + " não pode ser cancelada");
        }
        this.status = Status.CANCELLED;
        this.cancelReason = reason.trim();
        this.updatedAt = now;
    }

    /** Faixas 2 e 3: prazo encerrado, a Comissão abre o julgamento (de novo, se o anterior foi anulado). */
    public void startJudgment(Instant now) {
        if ((status != Status.OPEN && status != Status.JUDGING) || band == Band.FAIXA_1 || proposalDeadline == null
                || now.isBefore(proposalDeadline)) {
            throw new IllegalStateException("Só oportunidade das Faixas 2/3 com prazo encerrado vai a julgamento");
        }
        this.status = Status.JUDGING;
        this.updatedAt = now;
    }

    /** Faixa 1: seleção confirmada pela Comissão. Faixas 2 e 3: julgamento homologado. */
    public void award(Instant now) {
        if (band == Band.FAIXA_1 ? status != Status.OPEN : status != Status.JUDGING) {
            throw new IllegalStateException("Oportunidade " + status + " não pode ter seleção confirmada");
        }
        this.status = Status.AWARDED;
        this.updatedAt = now;
    }

    /** Recurso provido contra o julgamento homologado: volta a "Em julgamento" (D-049). */
    public void reopenForJudgment(Instant now) {
        if (status != Status.AWARDED || band == Band.FAIXA_1 || urgent) {
            throw new IllegalStateException("Só oportunidade julgada (Faixas 2/3) pode ser reaberta");
        }
        this.status = Status.JUDGING;
        this.updatedAt = now;
    }

    /**
     * Urgência (Política art. 13, § 3º): o gestor contrata direto um credenciado da linha, sem
     * rodízio nem cotação, com a justificativa no DFD. Numera e adjudica na hora; a Comissão
     * toma ciência até {@code ackDue}.
     */
    public void contractUrgently(String number, String justification, LocalDate ackDue, Instant now) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Só um DFD novo vira contratação de urgência");
        }
        this.number = number;
        this.urgent = true;
        this.urgencyJustification = justification.trim();
        this.urgencyAckDue = ackDue;
        this.status = Status.AWARDED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void acknowledgeUrgency(Long userId, String note, Instant now) {
        if (!urgent || urgencyAcknowledgedAt != null) {
            throw new IllegalStateException("Urgência inexistente ou já com ciência");
        }
        this.urgencyAcknowledgedAt = now;
        this.urgencyAcknowledgedBy = userId;
        this.urgencyAckNote = note == null || note.isBlank() ? null : note.trim();
        this.updatedAt = now;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public String getUrgencyJustification() {
        return urgencyJustification;
    }

    public LocalDate getUrgencyAckDue() {
        return urgencyAckDue;
    }

    public Instant getUrgencyAcknowledgedAt() {
        return urgencyAcknowledgedAt;
    }

    public String getUrgencyAckNote() {
        return urgencyAckNote;
    }

    /** Ampliação de território no rodízio quando faltam credenciados (Manual 4.3). */
    public void expandTerritories(Set<Territory> added, Instant now) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Só oportunidade aberta pode ampliar território");
        }
        this.territories.addAll(added);
        this.updatedAt = now;
    }

    /** Faixas 2 e 3: recebe propostas até o prazo. */
    public boolean acceptsOpenProposals(Instant now) {
        return status == Status.OPEN && band != Band.FAIXA_1 && proposalDeadline != null
                && now.isBefore(proposalDeadline);
    }

    /** As propostas só se abrem à Comissão depois do prazo (sigilo, Manual 4.2 passo 5). */
    public boolean proposalsSealed(Instant now) {
        return band != Band.FAIXA_1 && (proposalDeadline == null || now.isBefore(proposalDeadline));
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

    public Status getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public SupplyLine getSupplyLine() {
        return supplyLine;
    }

    public Municipality getExecutionMunicipality() {
        return executionMunicipality;
    }

    public String getLocation() {
        return location;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getExecutionDeadline() {
        return executionDeadline;
    }

    public String getRequirements() {
        return requirements;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public Audience getAudience() {
        return audience;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public Band getBand() {
        return band;
    }

    public Instant getProposalDeadline() {
        return proposalDeadline;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Set<Territory> getTerritories() {
        return territories.isEmpty() ? Set.of() : EnumSet.copyOf(territories);
    }

    public List<TenderItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Tender#" + id + "[" + status + "]";
    }
}
