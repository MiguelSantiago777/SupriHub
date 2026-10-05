package org.institutonata.suprihub.supplier.domain;

/** Tipo de interessado (Edital 2.1 e colunas da tabela do item 4.1). */
public enum SupplierType {
    MEI("Microempreendedor Individual (MEI)"),
    ME("Microempresa (ME)"),
    EPP("Empresa de Pequeno Porte (EPP)"),
    COOPERATIVE("Cooperativa"),
    INDIVIDUAL("Profissional autônomo (pessoa física)");

    private final String label;

    SupplierType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Pessoa física identifica-se por CPF; as demais por CNPJ. */
    public boolean isIndividual() {
        return this == INDIVIDUAL;
    }
}
