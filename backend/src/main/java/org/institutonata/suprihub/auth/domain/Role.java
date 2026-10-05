package org.institutonata.suprihub.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "role")
public class Role {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private AppRole code;

    @Column(nullable = false)
    private String description;

    protected Role() {
    }

    public Short getId() {
        return id;
    }

    public AppRole getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
