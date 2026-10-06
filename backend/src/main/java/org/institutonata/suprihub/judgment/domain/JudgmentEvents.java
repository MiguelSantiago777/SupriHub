package org.institutonata.suprihub.judgment.domain;

/** Eventos do julgamento (routing keys {@code judgment.#}). Payload só com ids. */
public final class JudgmentEvents {

    public static final String AGGREGATE = "judgment";

    /** Finalista convocada a exercer o direito de cobrir (Edital 8.2). */
    public static final String COVER_OFFERED = "judgment.cover.offered";
    /** Resultado definido — Comissão (Faixa 2) ou Diretoria (Faixa 3) homologa. */
    public static final String DECIDED = "judgment.result.decided";
    /** Homologado — avisa todos os proponentes. */
    public static final String HOMOLOGATED = "judgment.result.homologated";

    private JudgmentEvents() {
    }
}
