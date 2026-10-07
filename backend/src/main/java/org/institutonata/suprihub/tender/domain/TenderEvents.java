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
    /** Deslocado ao final do rodízio por recusas injustificadas (Política art. 13, § 4º). */
    public static final String ROTATION_DISPLACED = "tender.rotation.displaced";
    /** Contratação de urgência: a Comissão toma ciência em 2 d.u. (Política art. 13, § 3º). */
    public static final String URGENT_CONTRACTED = "tender.urgency.contracted";

    private TenderEvents() {
    }
}
