package org.institutonata.suprihub.supplier.domain;

/** Tipos de eventos de domínio do requerimento (routing keys {@code supplier.#}). */
public final class SupplierEvents {

    public static final String AGGREGATE = "supplier_application";

    /** Enviado (ou reenviado) para análise — confirma ao fornecedor e avisa a Comissão. */
    public static final String APPLICATION_SUBMITTED = "supplier.application.submitted";
    public static final String ADJUSTMENT_REQUESTED = "supplier.application.adjustment-requested";
    public static final String APPLICATION_APPROVED = "supplier.application.approved";
    public static final String APPLICATION_REJECTED = "supplier.application.rejected";

    public static final String APPEAL_AGGREGATE = "application_appeal";
    /** Recurso apresentado — confirma ao fornecedor e avisa a Comissão. */
    public static final String APPEAL_FILED = "supplier.appeal.filed";
    /** Encaminhado à Diretoria — avisa a Diretoria e o fornecedor. */
    public static final String APPEAL_FORWARDED = "supplier.appeal.forwarded";
    public static final String APPEAL_GRANTED = "supplier.appeal.granted";
    public static final String APPEAL_DENIED = "supplier.appeal.denied";

    private SupplierEvents() {
    }
}
