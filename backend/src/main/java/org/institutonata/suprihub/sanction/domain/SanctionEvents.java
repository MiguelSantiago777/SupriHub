package org.institutonata.suprihub.sanction.domain;

/** Eventos do processo sancionador (routing keys {@code sanction.#}). Payload só com ids. */
public final class SanctionEvents {

    public static final String AGGREGATE = "sanction_process";

    /** Notificação escrita ao fornecedor (abre o prazo de defesa). */
    public static final String OPENED = "sanction.process.opened";
    /** Defesa enviada — avisa a Comissão. */
    public static final String DEFENDED = "sanction.process.defended";
    /** Decisão da Comissão — abre o prazo de recurso. */
    public static final String DECIDED = "sanction.process.decided";
    /** Recurso à Diretoria. */
    public static final String APPEALED = "sanction.process.appealed";
    /** Encerrado: sanção definitiva (ou arquivamento). */
    public static final String FINAL = "sanction.process.final";

    private SanctionEvents() {
    }
}
