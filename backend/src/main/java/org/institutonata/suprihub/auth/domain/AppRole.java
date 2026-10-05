package org.institutonata.suprihub.auth.domain;

/** Perfis de acesso (o público sem login não tem perfil). Os ids espelham a tabela {@code role}. */
public enum AppRole {
    SUPPLIER(1),
    COMMISSION(2),
    DEMAND_MANAGER(3),
    FINANCE(4),
    INTERNAL_CONTROL(5),
    BOARD(6),
    ADMIN(7);

    private final short id;

    AppRole(int id) {
        this.id = (short) id;
    }

    public short id() {
        return id;
    }

    /** Perfis internos do Instituto (criados pelo ADMIN, não por autocadastro). */
    public boolean isInternal() {
        return this != SUPPLIER;
    }
}
