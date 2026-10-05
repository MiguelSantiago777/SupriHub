package org.institutonata.suprihub.catalog.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Linha de fornecimento do Catálogo (Anexo I). */
@Entity
@Table(name = "supply_line")
public class SupplyLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name", nullable = false)
    private String shortName;

    @Column(nullable = false)
    private String scope;

    @Column(name = "cnae_reference", nullable = false)
    private String cnaeReference;

    @Column(name = "allows_individual", nullable = false)
    private boolean allowsIndividual;

    @Column(name = "allows_medium_large", nullable = false)
    private boolean allowsMediumLarge;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected SupplyLine() {
    }

    public void update(String name, String shortName, String scope, String cnaeReference,
            boolean allowsIndividual, boolean allowsMediumLarge, boolean active, Instant now) {
        this.name = name;
        this.shortName = shortName;
        this.scope = scope;
        this.cnaeReference = cnaeReference;
        this.allowsIndividual = allowsIndividual;
        this.allowsMediumLarge = allowsMediumLarge;
        this.active = active;
        this.updatedAt = now;
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getScope() {
        return scope;
    }

    public String getCnaeReference() {
        return cnaeReference;
    }

    public boolean isAllowsIndividual() {
        return allowsIndividual;
    }

    public boolean isAllowsMediumLarge() {
        return allowsMediumLarge;
    }

    public boolean isActive() {
        return active;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public long getVersion() {
        return version;
    }
}
