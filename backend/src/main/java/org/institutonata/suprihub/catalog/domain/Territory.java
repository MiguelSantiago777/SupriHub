package org.institutonata.suprihub.catalog.domain;

/**
 * Território prioritário (Política art. 3º, XII; Edital 8.3). {@code level} menor = mais prioritário.
 * {@link #OUT_OF_STATE} (nível 4) só participa da Faixa 3 ou por ampliação justificada.
 */
public enum Territory {
    T1(1, "Mesquita e município de execução"),
    T2(2, "Região Metropolitana do Rio de Janeiro"),
    T3(3, "Demais municípios do Estado do Rio de Janeiro"),
    OUT_OF_STATE(4, "Fora do Estado do Rio de Janeiro");

    private final int level;
    private final String label;

    Territory(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int level() {
        return level;
    }

    public String label() {
        return label;
    }

    /** {@code true} se este território tem prioridade sobre {@code other}. */
    public boolean outranks(Territory other) {
        return level < other.level;
    }
}
