package org.institutonata.suprihub.tender.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Item da oportunidade: o que comprar, unidade e quantidade (DFD, parte 2). */
@Entity
@Table(name = "tender_item")
public class TenderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tender_id")
    private Tender tender;

    @Column(nullable = false)
    private short position;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private BigDecimal quantity;

    protected TenderItem() {
    }

    TenderItem(Tender tender, int position, String description, String unit, BigDecimal quantity) {
        this.tender = tender;
        this.position = (short) position;
        this.description = description.trim();
        this.unit = unit.trim();
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
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
}
