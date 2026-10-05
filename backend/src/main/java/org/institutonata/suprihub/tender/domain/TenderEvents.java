package org.institutonata.suprihub.tender.domain;

/** Eventos das oportunidades e propostas (routing keys {@code tender.#}). */
public final class TenderEvents {

    public static final String AGGREGATE = "tender";

    /** Faixas 2 e 3 publicadas — avisa os fornecedores elegíveis. */
    public static final String PUBLISHED = "tender.opportunity.published";
    /** Faixa 1 — convite do rodízio ao próximo da fila. */
    public static final String INVITATION_CREATED = "tender.invitation.created";
    /** Proposta enviada ou substituída — confirma ao fornecedor. */
    public static final String PROPOSAL_SUBMITTED = "tender.proposal.submitted";

    private TenderEvents() {
    }
}
