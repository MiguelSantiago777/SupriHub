package org.institutonata.suprihub.tender.domain;

/** Faixa de valor e modo de seleção (Política art. 13; Edital 7.1). */
public enum Band {
    FAIXA_1("Faixa 1", "Rodízio/sorteio entre credenciados"),
    FAIXA_2("Faixa 2", "Cotação entre credenciados"),
    FAIXA_3("Faixa 3", "Chamada ampla");

    private final String label;
    private final String selectionMode;

    Band(String label, String selectionMode) {
        this.label = label;
        this.selectionMode = selectionMode;
    }

    public String label() {
        return label;
    }

    public String selectionMode() {
        return selectionMode;
    }
}
