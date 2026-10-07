package org.institutonata.suprihub.contract.domain;

/** Eventos da contratação (routing keys {@code contract.#}). Payload só com ids. */
public final class ContractEvents {

    public static final String AGGREGATE = "service_order";

    /** Oportunidade adjudicada: a OF/OS em preparação aguarda a equipe. */
    public static final String CREATED = "contract.order.created";
    /** OF/OS emitida: o fornecedor tem 2 dias úteis para assinar. */
    public static final String ISSUED = "contract.order.issued";
    /** Prazo de assinatura vencido. */
    public static final String EXPIRED = "contract.order.expired";
    /** Termo de Recebimento registrado — pede a ciência do fornecedor. */
    public static final String RECEIVED = "contract.order.received";
    /** Nota fiscal enviada — avisa o Financeiro. */
    public static final String INVOICED = "contract.order.invoiced";
    /** Pagamento registrado — avisa o fornecedor. */
    public static final String PAID = "contract.order.paid";
    /** Sinal pago (Edital 10.4) — avisa o fornecedor. */
    public static final String UPFRONT_PAID = "contract.order.upfront-paid";
    /** Edital 9.5: avaliações insuficientes — a Comissão avalia abrir processo de suspensão. */
    public static final String LOW_RATING_ALERT = "contract.supplier.low-rating";

    private ContractEvents() {
    }
}
