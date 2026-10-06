package org.institutonata.suprihub.judgment.model;

/** Listas fechadas do julgamento. */
public final class JudgmentEnums {

    private JudgmentEnums() {
    }

    /**
     * <pre>
     *  (julgar) ──▶ AWAITING_COVER ──cobriu / chegou à menor──▶ DECIDED ──homologar──▶ HOMOLOGATED
     *                  └─ recusou ou venceu o prazo: convoca a próxima ─┘     │
     *  qualquer um antes de homologar ──anular──▶ ANNULLED (pode julgar de novo)
     * </pre>
     */
    public enum Status {
        AWAITING_COVER("Aguardando cobertura"),
        DECIDED("Aguardando homologação"),
        HOMOLOGATED("Homologado"),
        ANNULLED("Anulado");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean isOpen() {
            return this == AWAITING_COVER || this == DECIDED;
        }
    }

    public enum CoverStatus {
        OFFERED("Convocado a cobrir"),
        COVERED("Cobriu o menor preço"),
        DECLINED("Não quis cobrir"),
        EXPIRED("Não respondeu no prazo"),
        CANCELLED("Convocação cancelada (julgamento anulado)");

        private final String label;

        CoverStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
