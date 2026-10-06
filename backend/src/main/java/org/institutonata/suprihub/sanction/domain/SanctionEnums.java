package org.institutonata.suprihub.sanction.domain;

/** Listas fechadas do processo sancionador (Anexo XII; Política art. 24 a 26). */
public final class SanctionEnums {

    private SanctionEnums() {
    }

    /** Infrações do Anexo XII, com a sanção de referência. */
    public enum Infraction {
        BAND1_REFUSALS("Recusa injustificada de duas oportunidades consecutivas na Faixa 1",
                "Deslocamento ao final do rodízio; advertência na reincidência"),
        ORDER_REFUSAL("Não manter a proposta ou recusar a formalização da Ordem quando selecionado",
                "Advertência; suspensão de 3 a 6 meses na reincidência"),
        DELAY("Atraso injustificado na execução ou entrega", "Advertência; multa de 2% a 5% quando prevista"),
        PARTIAL_NON_PERFORMANCE("Inexecução parcial", "Multa de 5% a 10% quando prevista; suspensão de 6 a 12 meses"),
        TOTAL_NON_PERFORMANCE("Inexecução total", "Multa de 10% quando prevista; suspensão de 12 a 24 meses"),
        FALSE_DOCUMENTS("Documentação falsa ou usufruto indevido de tratamento favorecido",
                "Descredenciamento; impedimento por até 3 anos; comunicação às autoridades"),
        FRAUD("Fraude, conluio, comportamento inidôneo, ato lesivo (Lei nº 12.846/2013)",
                "Descredenciamento; impedimento por 3 anos; comunicação ao parceiro e às autoridades"),
        DATA_OR_SAFETY("Violação de dados pessoais ou descumprimento de normas de segurança e acessibilidade",
                "Advertência a suspensão de até 12 meses, conforme a gravidade"),
        LOW_RATINGS("Duas avaliações com média inferior a 3 em 12 meses", "Suspensão de 6 meses, com plano de melhoria");

        private final String label;
        private final String reference;

        Infraction(String label, String reference) {
            this.label = label;
            this.reference = reference;
        }

        public String label() {
            return label;
        }

        public String reference() {
            return reference;
        }
    }

    /** Sanções do art. 25 (e "nenhuma", quando o processo é arquivado). */
    public enum Sanction {
        NONE("Arquivamento — sem sanção"),
        WARNING("Advertência escrita"),
        FINE("Multa"),
        SUSPENSION("Suspensão do cadastro"),
        DEREGISTRATION("Descredenciamento com impedimento");

        private final String label;

        Sanction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Suspensão e descredenciamento tiram o fornecedor das oportunidades. */
        public boolean blocks() {
            return this == SUSPENSION || this == DEREGISTRATION;
        }
    }

    /**
     * <pre>
     *  DEFENSE ─defesa ou prazo─▶ AWAITING_DECISION ─decisão─▶ APPEAL_WINDOW ─prazo─▶ FINAL
     *                                                              └ recurso ▶ BOARD_REVIEW ─decisão─▶ FINAL
     * </pre>
     */
    public enum Status {
        DEFENSE("Aguardando defesa"),
        AWAITING_DECISION("Em decisão pela Comissão"),
        APPEAL_WINDOW("Decidido — prazo de recurso"),
        BOARD_REVIEW("Recurso na Diretoria"),
        FINAL("Encerrado");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum BoardOutcome {
        UPHELD("Mantida a decisão da Comissão"),
        REFORMED("Sanção reformada"),
        ANNULLED("Decisão anulada — sem sanção");

        private final String label;

        BoardOutcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum FileKind {
        EVIDENCE("Prova juntada pela Comissão"),
        DEFENSE("Anexo da defesa"),
        APPEAL("Anexo do recurso");

        private final String label;

        FileKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
