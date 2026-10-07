package org.institutonata.suprihub.contract.domain;

/** Listas fechadas da Ordem de Fornecimento/Serviço. */
public final class ContractEnums {

    private ContractEnums() {
    }

    /**
     * <pre>
     *  DRAFT ──emitir──▶ ISSUED ──assinar──▶ SIGNED ──receber──▶ RECEIVED ──NF──▶ INVOICED ──pagar──▶ PAID
     *    │  (Faixa 1: aceite = assinatura)  │ prazo vencido         │ com ressalvas ⟲
     *    │                                  ▼                       └ recusado ▶ REJECTED
     *    └──────────── cancelar ─────── EXPIRED ──reemitir──▶ ISSUED
     * </pre>
     */
    public enum Status {
        DRAFT("Em preparação"),
        ISSUED("Aguardando assinatura do fornecedor"),
        EXPIRED("Não assinada no prazo"),
        SIGNED("Em execução"),
        RECEIVED_WITH_RESERVATIONS("Recebida com ressalvas"),
        RECEIVED("Recebida — aguardando nota fiscal"),
        REJECTED("Objeto recusado"),
        INVOICED("Nota fiscal recebida — aguardando pagamento"),
        PAID("Paga"),
        CANCELLED("Cancelada");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean canReceive() {
            return this == SIGNED || this == RECEIVED_WITH_RESERVATIONS;
        }

        public boolean isClosed() {
            return this == PAID || this == CANCELLED || this == REJECTED;
        }
    }

    public enum Kind {
        SUPPLY("Ordem de Fornecimento"),
        SERVICE("Ordem de Serviço");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum ReceiptKind {
        DEFINITIVE("Definitivo — conforme a Ordem"),
        WITH_RESERVATIONS("Com ressalvas — correções pendentes"),
        REJECTED("Recusado");

        private final String label;

        ReceiptKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** O termo definitivo ou o de recusa encerram a avaliação da contratação. */
        public boolean isFinal() {
            return this != WITH_RESERVATIONS;
        }
    }

    public enum FileKind {
        EVIDENCE("Evidência do recebimento"),
        INVOICE("Nota fiscal / RPA"),
        PAYMENT_PROOF("Comprovante de pagamento"),
        UPFRONT_PROOF("Comprovante do sinal");

        private final String label;

        FileKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum PaymentMethod {
        PIX("Pix"),
        TRANSFER("Transferência bancária");

        private final String label;

        PaymentMethod(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum PriceSource {
        CONTRACT("Contratação"),
        QUOTATION("Cotação"),
        PRICE_PANEL("Painel de Preços"),
        RESEARCH("Pesquisa de mercado");

        private final String label;

        PriceSource(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
