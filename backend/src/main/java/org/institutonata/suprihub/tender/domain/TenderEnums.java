package org.institutonata.suprihub.tender.domain;

/** Listas fechadas das oportunidades. */
public final class TenderEnums {

    private TenderEnums() {
    }

    public enum Status {
        DRAFT("Rascunho"),
        OPEN("Aberto"),
        AWARDED("Fornecedor selecionado"),
        CANCELLED("Cancelado");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A quem a oportunidade é dirigida (DFD, parte 5). Todos os credenciados são pequenos negócios (D-031). */
    public enum Audience {
        ALL("Todos os credenciados da linha"),
        WOMEN("Exclusiva a negócios liderados por mulheres");

        private final String label;

        Audience(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Situação do convite do rodízio (Faixa 1). */
    public enum InvitationStatus {
        INVITED("Convidado — aguardando resposta"),
        ACCEPTED("Aceitou — preço em conferência"),
        CONFIRMED("Selecionado"),
        DECLINED("Recusou"),
        EXPIRED("Não respondeu no prazo"),
        PRICE_REJECTED("Preço não aceito pela Comissão");

        private final String label;

        InvitationStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean isActive() {
            return this == INVITED || this == ACCEPTED;
        }
    }
}
