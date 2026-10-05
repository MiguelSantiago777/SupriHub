package org.institutonata.suprihub.supplier.domain;

/** Tipos de eventos de domínio do requerimento (routing keys {@code supplier.#}). */
public final class SupplierEvents {

    public static final String AGGREGATE = "supplier_application";

    /** Enviado (ou reenviado) para análise — confirma ao fornecedor e avisa a Comissão. */
    public static final String APPLICATION_SUBMITTED = "supplier.application.submitted";
    public static final String ADJUSTMENT_REQUESTED = "supplier.application.adjustment-requested";
    public static final String APPLICATION_APPROVED = "supplier.application.approved";
    public static final String APPLICATION_REJECTED = "supplier.application.rejected";

    private SupplierEvents() {
    }
}
