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

    // Manutenção do cadastro (Política art. 21 a 23)
    /** Certidão perto do vencimento ("RENOVAR"). Carrega {@code documentIds}. */
    public static final String CERTIFICATE_EXPIRING = "supplier.certificate.expiring";
    /** Certidão vencida: notifica o fornecedor (5 d.u.) e a Comissão (consulta direta). */
    public static final String CERTIFICATE_EXPIRED = "supplier.certificate.expired";
    public static final String INACTIVATED = "supplier.maintenance.inactivated";
    public static final String REACTIVATED = "supplier.maintenance.reactivated";
    /** 30 dias irregular: a Comissão avalia o descredenciamento (D-014). */
    public static final String DEREGISTRATION_ALERT = "supplier.maintenance.deregistration-alert";
    /** Dados cadastrais ou documentos atualizados pelo credenciado. Carrega {@code changes}. */
    public static final String REGISTRATION_UPDATED = "supplier.registration.updated";
    public static final String RENEWAL_DUE = "supplier.renewal.due";
    public static final String RENEWAL_REQUESTED = "supplier.renewal.requested";
    public static final String RENEWED = "supplier.renewal.renewed";
    public static final String RENEWAL_RETURNED = "supplier.renewal.returned";

    private SupplierEvents() {
    }
}
