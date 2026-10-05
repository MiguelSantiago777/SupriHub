package org.institutonata.suprihub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "municipality")
public class Municipality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ibge_code", nullable = false, unique = true)
    private String ibgeCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(name = "metropolitan_region", nullable = false)
    private boolean metropolitanRegion;

    protected Municipality() {
    }

    public MunicipalityRef toRef() {
        return new MunicipalityRef(ibgeCode, uf, metropolitanRegion);
    }

    public Integer getId() {
        return id;
    }

    public String getIbgeCode() {
        return ibgeCode;
    }

    public String getName() {
        return name;
    }

    public String getUf() {
        return uf;
    }

    public boolean isMetropolitanRegion() {
        return metropolitanRegion;
    }
}
