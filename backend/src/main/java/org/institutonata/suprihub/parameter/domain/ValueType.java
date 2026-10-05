package org.institutonata.suprihub.parameter.domain;

import java.math.BigDecimal;

public enum ValueType {
    DECIMAL, INTEGER, BOOLEAN, TEXT;

    /** Valida o texto informado para o tipo; devolve a forma normalizada. */
    public String normalize(String raw) {
        String v = raw == null ? "" : raw.trim();
        try {
            return switch (this) {
                case DECIMAL -> new BigDecimal(v.replace(',', '.')).toPlainString();
                case INTEGER -> String.valueOf(Integer.parseInt(v));
                case BOOLEAN -> {
                    if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException();
                    }
                    yield v.toLowerCase();
                }
                case TEXT -> {
                    if (v.isEmpty()) {
                        throw new IllegalArgumentException();
                    }
                    yield v;
                }
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valor inválido para o tipo " + this + ": " + raw);
        }
    }
}
