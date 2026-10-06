package org.institutonata.suprihub.contract.domain;

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

import org.institutonata.suprihub.contract.domain.ContractEnums.PriceSource;

/** Preço praticado ou cotado de um item (aba Banco de Preços da Planilha). */
@Entity
@Table(name = "price_bank_entry")
public class PriceBankEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supply_line_id", nullable = false, updatable = false)
    private Short supplyLineId;
    @Column(name = "item_description", nullable = false, updatable = false)
    private String itemDescription;
    @Column(nullable = false, updatable = false)
    private String unit;
    @Column(name = "unit_price", nullable = false, updatable = false)
    private BigDecimal unitPrice;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PriceSource source;
    @Column(updatable = false)
    private String reference;
    @Column(name = "application_id", updatable = false)
    private Long applicationId;
    @Column(name = "service_order_id", updatable = false)
    private Long serviceOrderId;
    @Column(name = "observed_on", nullable = false, updatable = false)
    private LocalDate observedOn;
    @Column(name = "created_by", updatable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PriceBankEntry() {
    }

    public PriceBankEntry(Short supplyLineId, String itemDescription, String unit, BigDecimal unitPrice,
            PriceSource source, String reference, Long applicationId, Long serviceOrderId, LocalDate observedOn,
            Long createdBy, Instant now) {
        this.supplyLineId = supplyLineId;
        this.itemDescription = itemDescription.trim();
        this.unit = unit.trim();
        this.unitPrice = unitPrice;
        this.source = source;
        this.reference = reference;
        this.applicationId = applicationId;
        this.serviceOrderId = serviceOrderId;
        this.observedOn = observedOn;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Short getSupplyLineId() {
        return supplyLineId;
    }

    public String getItemDescription() {
        return itemDescription;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public PriceSource getSource() {
        return source;
    }

    public String getReference() {
        return reference;
    }

    public Long getServiceOrderId() {
        return serviceOrderId;
    }

    public LocalDate getObservedOn() {
        return observedOn;
    }
}
