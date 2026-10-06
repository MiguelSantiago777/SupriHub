package org.institutonata.suprihub.judgment.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Immutable;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Advantage;
import org.institutonata.suprihub.supplier.domain.SupplierType;
import org.springframework.data.domain.Persistable;

/**
 * Linha do mapa comparativo, congelada no julgamento (trigger impede UPDATE/DELETE).
 * {@code position} nulo = proposta desclassificada. Raça/CadÚnico aparecem só como o
 * indicador combinado do desempate, e nunca saem para o fornecedor (Política art. 28).
 */
@Entity
@Immutable
@IdClass(JudgmentEntry.Key.class)
@Table(name = "judgment_entry")
public class JudgmentEntry implements Persistable<JudgmentEntry.Key> {

    public static class Key implements Serializable {
        private Long judgmentId;
        private Long proposalId;

        public Key() {
        }

        public Key(Long judgmentId, Long proposalId) {
            this.judgmentId = judgmentId;
            this.proposalId = proposalId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(judgmentId, k.judgmentId)
                    && Objects.equals(proposalId, k.proposalId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(judgmentId, proposalId);
        }
    }

    /** Dados do fornecedor no momento do julgamento. */
    public record Supplier(Long applicationId, String name, SupplierType type, Territory territory,
            boolean smallBusiness, boolean womanLed, boolean blackWomanOrCadUnico, boolean mei, BigDecimal rating) {
    }

    @Id
    @Column(name = "judgment_id")
    private Long judgmentId;
    @Id
    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;
    private Integer position;
    @Column(name = "supplier_name", nullable = false)
    private String supplierName;
    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_type")
    private SupplierType supplierType;
    @Column(name = "total_value", nullable = false)
    private BigDecimal totalValue;
    @Column(name = "pct_above")
    private BigDecimal pctAbove;
    @Column(name = "small_business", nullable = false)
    private boolean smallBusiness;
    @Enumerated(EnumType.STRING)
    private Territory territory;
    @Column(name = "woman_led", nullable = false)
    private boolean womanLed;
    @Column(name = "black_woman_or_cad_unico", nullable = false)
    private boolean blackWomanOrCadUnico;
    @Column(nullable = false)
    private boolean mei;
    private BigDecimal rating;
    @Column(name = "fictitious_tie", nullable = false)
    private boolean fictitiousTie;
    @Column(name = "local_preference", nullable = false)
    private boolean localPreference;
    @Column(nullable = false)
    private boolean finalist;
    @Column(nullable = false)
    private boolean drawn;
    @Enumerated(EnumType.STRING)
    private Advantage advantage;
    @Column(name = "disqualified_reason")
    private String disqualifiedReason;

    /** Só é gravada uma vez: o save insere direto, sem SELECT/merge. */
    @Transient
    private boolean fresh = true;

    protected JudgmentEntry() {
    }

    @PostLoad
    void loaded() {
        fresh = false;
    }

    @Override
    public Key getId() {
        return new Key(judgmentId, proposalId);
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    private JudgmentEntry(Long judgmentId, Long proposalId, Supplier s, BigDecimal total) {
        this.judgmentId = judgmentId;
        this.proposalId = proposalId;
        this.applicationId = s.applicationId();
        this.supplierName = s.name();
        this.supplierType = s.type();
        this.territory = s.territory();
        this.smallBusiness = s.smallBusiness();
        this.womanLed = s.womanLed();
        this.blackWomanOrCadUnico = s.blackWomanOrCadUnico();
        this.mei = s.mei();
        this.rating = s.rating();
        this.totalValue = total;
    }

    public static JudgmentEntry ranked(Long judgmentId, Long proposalId, Supplier s, BigDecimal total, int position,
            BigDecimal pctAbove, boolean fictitiousTie, boolean localPreference, boolean finalist, boolean drawn,
            Advantage advantage) {
        JudgmentEntry e = new JudgmentEntry(judgmentId, proposalId, s, total);
        e.position = position;
        e.pctAbove = pctAbove;
        e.fictitiousTie = fictitiousTie;
        e.localPreference = localPreference;
        e.finalist = finalist;
        e.drawn = drawn;
        e.advantage = advantage;
        return e;
    }

    public static JudgmentEntry disqualified(Long judgmentId, Long proposalId, Supplier s, BigDecimal total,
            String reason) {
        JudgmentEntry e = new JudgmentEntry(judgmentId, proposalId, s, total);
        e.disqualifiedReason = reason;
        return e;
    }

    public boolean isDisqualified() {
        return position == null;
    }

    public boolean isLowest() {
        return pctAbove != null && pctAbove.signum() == 0;
    }

    public boolean mustCover() {
        return finalist && !isLowest();
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

    public Integer getPosition() {
        return position;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public SupplierType getSupplierType() {
        return supplierType;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public BigDecimal getPctAbove() {
        return pctAbove;
    }

    public boolean isSmallBusiness() {
        return smallBusiness;
    }

    public Territory getTerritory() {
        return territory;
    }

    public boolean isWomanLed() {
        return womanLed;
    }

    public boolean isBlackWomanOrCadUnico() {
        return blackWomanOrCadUnico;
    }

    public boolean isMei() {
        return mei;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public boolean isFictitiousTie() {
        return fictitiousTie;
    }

    public boolean isLocalPreference() {
        return localPreference;
    }

    public boolean isFinalist() {
        return finalist;
    }

    public boolean isDrawn() {
        return drawn;
    }

    public Advantage getAdvantage() {
        return advantage;
    }

    public String getDisqualifiedReason() {
        return disqualifiedReason;
    }

    @Override
    public String toString() {
        return "JudgmentEntry[" + judgmentId + "/" + proposalId + "]";
    }
}
