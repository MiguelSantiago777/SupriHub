package org.institutonata.suprihub.supplier.domain;

/**
 * Situação do requerimento (Manual, item 3). Transições:
 * <pre>
 *  DRAFT ──enviar──▶ SUBMITTED ──habilitar──▶ APPROVED
 *                      │  ▲  └──inabilitar──▶ REJECTED ──corrigir e reenviar──▶ SUBMITTED
 *         pedir ajuste ▼  │ reenviar
 *              ADJUSTMENT_REQUESTED
 * </pre>
 */
public enum ApplicationStatus {
    DRAFT("Em preenchimento"),
    SUBMITTED("Em análise"),
    ADJUSTMENT_REQUESTED("Ajuste solicitado"),
    APPROVED("Habilitado"),
    REJECTED("Inabilitado");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** O fornecedor só altera dados e documentos quando o requerimento está com ele. */
    public boolean isEditableBySupplier() {
        return this == DRAFT || this == ADJUSTMENT_REQUESTED || this == REJECTED;
    }
}
