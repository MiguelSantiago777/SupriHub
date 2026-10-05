package org.institutonata.suprihub.proposal.application;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.catalog.domain.TerritoryResolver;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.shared.document.DocxBuilder;
import org.institutonata.suprihub.shared.document.DocxBuilder.Cell;
import org.institutonata.suprihub.shared.validation.DocumentNumbers;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierType;
import org.institutonata.suprihub.tender.application.OpportunityService;
import org.institutonata.suprihub.tender.application.OpportunityService.View;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modelo de Proposta (Anexo VII) em Word, já preenchido com os dados da oportunidade e do
 * fornecedor. Preços, prazo, validade e assinatura ficam em branco para ele completar.
 */
@Service
public class ProposalModelService {

    public record Model(String filename, byte[] content) {
    }

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final OpportunityService opportunities;
    private final ParameterService parameters;

    public ProposalModelService(OpportunityService opportunities, ParameterService parameters) {
        this.opportunities = opportunities;
        this.parameters = parameters;
    }

    @Transactional(readOnly = true)
    public Model build(long userId, long tenderId) {
        View view = opportunities.get(userId, tenderId);
        Tender t = view.tender();
        SupplierApplication a = opportunities.application(userId);

        String doc = a.getDocumentNumber() == null ? ""
                : a.getDocumentNumber().length() == 11 ? DocumentNumbers.formatCpf(a.getDocumentNumber())
                        : DocumentNumbers.formatCnpj(a.getDocumentNumber());
        Territory seat = a.getMunicipality() == null ? null
                : TerritoryResolver.resolve(a.getMunicipality().toRef(),
                        t.getExecutionMunicipality() == null ? null : t.getExecutionMunicipality().toRef()).territory();

        DocxBuilder d = new DocxBuilder()
                .title("ANEXO VII — MODELO DE PROPOSTA")
                .subtitle("Contrata + NATA — Instituto NATA · Edital nº 001/2026");

        d.table(new int[] {2, 5}, List.of(
                row("Oportunidade nº / Linha", nz(t.getNumber()) + " — " + t.getTitle() + "\nLinha "
                        + t.getSupplyLine().getCode() + " · " + t.getSupplyLine().getShortName()),
                row("Fornecedor (razão social ou nome) / CNPJ ou CPF", nz(a.getLegalName()) + "\n" + doc),
                row("Contato (telefone/WhatsApp, e-mail)", phone(a.getPhone()) + "\n" + nz(a.getContactEmail())),
                row("Porte", porte(a.getSupplierType())),
                row("Negócio liderado por mulher (Anexo IV entregue)?",
                        (a.isWomanLed() ? "☒" : "☐") + " Sim   " + (a.isWomanLed() ? "☐" : "☒") + " Não"),
                row("Território da sede", mark(seat == Territory.T1) + " T1   " + mark(seat == Territory.T2) + " T2   "
                        + mark(seat == Territory.T3) + " T3")));
        d.spacer();

        List<List<Cell>> items = new ArrayList<>();
        items.add(List.of(Cell.head("Item"), Cell.head("Descrição"), Cell.head("Unid."), Cell.head("Qtd."),
                Cell.head("Valor unitário (R$)"), Cell.head("Valor total (R$)")));
        for (TenderItem i : t.getItems()) {
            items.add(List.of(Cell.of(String.valueOf(i.getPosition())), Cell.of(i.getDescription()),
                    Cell.of(i.getUnit()), Cell.of(qty(i.getQuantity())), Cell.of(""), Cell.of("")));
        }
        items.add(List.of(Cell.of(""), Cell.head("TOTAL"), Cell.of(""), Cell.of(""), Cell.of(""), Cell.of("")));
        d.table(new int[] {1, 6, 2, 2, 3, 3}, items);
        d.spacer();

        int validity = parameters.integer(ParameterKey.PROPOSAL_VALIDITY_DAYS);
        d.table(new int[] {2, 5}, List.of(
                row("Prazo de execução / entrega", t.getExecutionDeadline() == null ? ""
                        : "(Pedido: " + t.getExecutionDeadline() + ")\n"),
                row("Validade da proposta (mínimo " + validity + " dias)", ""),
                row("Condições especiais (sinal, montagem, equipe, equipamentos)", ""),
                row("Observações", "")));
        d.spacer();
        d.text("Declaro que o valor proposto inclui todos os custos diretos e indiretos, tributos, encargos, "
                + "materiais, equipamentos, deslocamento e EPIs necessários à execução integral do objeto; que "
                + "mantenho as condições de habilitação; e que aceito as condições da oportunidade, do Edital "
                + "nº 001/2026 e da Política de Credenciamento.");
        d.spacer();
        d.text("____________________________, ____ de ________________ de 20____.");
        d.spacer();
        d.text("______________________________________");
        d.text("Assinatura do(a) fornecedor(a)");
        d.spacer();
        d.text("Como enviar: preencha os valores, assine, e envie este arquivo (ou foto/PDF dele) pela área do "
                + "fornecedor no SupriHub, na própria oportunidade, informando também o valor total no sistema.");

        String name = "Proposta-" + (t.getNumber() == null ? "oportunidade" : t.getNumber()) + ".docx";
        return new Model(name, d.build());
    }

    private static List<Cell> row(String label, String value) {
        return List.of(Cell.head(label), Cell.of(value));
    }

    private static String porte(SupplierType type) {
        return mark(type == SupplierType.MEI) + " MEI   " + mark(type == SupplierType.ME) + " ME   "
                + mark(type == SupplierType.EPP) + " EPP   " + mark(type == SupplierType.COOPERATIVE)
                + " Cooperativa   " + mark(type == SupplierType.INDIVIDUAL) + " PF   ☐ Outro";
    }

    private static String mark(boolean on) {
        return on ? "☒" : "☐";
    }

    private static String qty(BigDecimal q) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(2);
        return f.format(q);
    }

    private static String phone(String digits) {
        if (digits == null || digits.length() < 10) {
            return nz(digits);
        }
        String rest = digits.substring(2);
        int split = rest.length() == 9 ? 5 : 4;
        return "(" + digits.substring(0, 2) + ") " + rest.substring(0, split) + "-" + rest.substring(split);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
