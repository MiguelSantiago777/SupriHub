package org.institutonata.suprihub.supplier.domain;

/** Listas fechadas do Anexo II. */
public final class SupplierEnums {

    private SupplierEnums() {
    }

    /** Autodeclaração de raça/cor (Anexo II, item 5 — D-016). Facultativa. */
    public enum Race { PRETA, PARDA, INDIGENA, BRANCA, AMARELA, NAO_INFORMADA }

    public enum BankAccountType { CHECKING, SAVINGS, PAYMENT }
}
