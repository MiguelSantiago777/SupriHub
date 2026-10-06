package org.institutonata.suprihub.contract.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.institutonata.suprihub.tender.domain.Band;

/**
 * Checklist do Dossiê da Contratação (Anexo XIII; Política art. 20). Regra pura: recebe o que
 * o sistema sabe da contratação e as confirmações manuais, e diz o que está OK, pendente ou
 * não se aplica.
 */
public final class DossierChecklist {

    private DossierChecklist() {
    }

    public enum State { OK, PENDING, NOT_APPLICABLE }

    /** Fatos da contratação conhecidos pelo sistema. */
    public record Facts(Band band, boolean published, int invitations, boolean proposalReceived,
            boolean minimumProposalsOrJustified, boolean judged, boolean ataHomologated, boolean issuedWhileRegular,
            boolean signedOrAccepted, boolean invoiceFile, boolean paidWithProof, boolean finalReceiptWithEvidence) {
    }

    /** Confirmação manual de um item. */
    public record Manual(boolean checked, String note) {
    }

    public record Item(int no, String label, boolean manual, boolean optional, State state, String detail) {
    }

    private record Def(int no, String label, boolean manual, boolean optional) {
    }

    private static final List<Def> DEFS = List.of(
            new Def(1, "DFD-NATA com atesto financeiro de previsão e saldo", true, false),
            new Def(2, "Estimativa de valor com fonte (Banco de Preços / cotações / Painel de Preços)", true, false),
            new Def(3, "Registro do rodízio ou ata do sorteio (lista de credenciados convocados)", false, false),
            new Def(4, "Texto da oportunidade e comprovante de envio aos credenciados", false, false),
            new Def(5, "Publicação da chamada no sítio eletrônico (print com data)", true, false),
            new Def(6, "Propostas recebidas (mínimo 3 válidas na Faixa 2)", false, false),
            new Def(7, "Mapa comparativo com empate ficto, preferência local e desempate", false, false),
            new Def(8, "Ata de julgamento / parecer da Comissão / aprovação da Diretoria (Faixa 3)", false, false),
            new Def(9, "Habilitação atualizada: certidões, CCMEI/contrato social, consulta CEIS/CNEP/CEPIM", false,
                    false),
            new Def(10, "Ordem de Fornecimento/Serviço assinada ou aceite eletrônico registrado", false, false),
            new Def(11, "Nota fiscal ou RPA", false, false),
            new Def(12, "Comprovante de transferência/Pix identificando o beneficiário", false, false),
            new Def(13, "Termo de Recebimento e Avaliação com evidências", false, false),
            new Def(14, "Justificativa de urgência ou de ampliação de território, quando houver", true, true),
            new Def(15, "Registro na Planilha de Gestão (contratação, avaliação, indicadores)", false, false));

    public static List<Item> evaluate(Facts f, Map<Integer, Manual> manual) {
        List<Item> out = new ArrayList<>();
        boolean band1 = f.band() == Band.FAIXA_1;
        for (Def d : DEFS) {
            Manual m = manual.get(d.no());
            State state;
            String detail = null;
            switch (d.no()) {
                case 3 -> {
                    state = !band1 ? State.NOT_APPLICABLE : f.invitations() > 0 ? State.OK : State.PENDING;
                    detail = band1 ? f.invitations() + " convite(s) registrados no rodízio" : null;
                }
                case 4 -> state = f.published() ? State.OK : State.PENDING;
                case 5 -> state = f.band() != Band.FAIXA_3 ? State.NOT_APPLICABLE : manualState(m);
                case 6 -> state = band1 ? (f.proposalReceived() ? State.OK : State.PENDING)
                        : f.minimumProposalsOrJustified() ? State.OK : State.PENDING;
                case 7 -> state = band1 ? State.NOT_APPLICABLE : f.judged() ? State.OK : State.PENDING;
                case 8 -> state = band1 ? State.NOT_APPLICABLE : f.ataHomologated() ? State.OK : State.PENDING;
                case 9 -> {
                    state = f.issuedWhileRegular() ? State.OK : State.PENDING;
                    detail = "credenciamento vigente conferido pelo sistema na emissão da ordem";
                }
                case 10 -> state = f.signedOrAccepted() ? State.OK : State.PENDING;
                case 11 -> state = f.invoiceFile() ? State.OK : State.PENDING;
                case 12 -> state = f.paidWithProof() ? State.OK : State.PENDING;
                case 13 -> state = f.finalReceiptWithEvidence() ? State.OK : State.PENDING;
                case 14 -> state = m != null && m.checked() ? State.OK : State.NOT_APPLICABLE;
                case 15 -> {
                    state = State.OK;
                    detail = "registrado no SupriHub, que substitui a Planilha";
                }
                default -> state = manualState(m);
            }
            if (m != null && m.note() != null) {
                detail = m.note();
            }
            out.add(new Item(d.no(), d.label(), d.manual(), d.optional(), state, detail));
        }
        return out;
    }

    private static State manualState(Manual m) {
        return m != null && m.checked() ? State.OK : State.PENDING;
    }

    /** Dossiê completo: nenhum item aplicável pendente. */
    public static boolean complete(List<Item> items) {
        return items.stream().noneMatch(i -> i.state() == State.PENDING);
    }

    /** Itens que aceitam confirmação manual. */
    public static boolean isManual(int no) {
        return DEFS.stream().anyMatch(d -> d.no() == no && d.manual());
    }
}
