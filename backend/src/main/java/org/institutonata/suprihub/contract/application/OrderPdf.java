package org.institutonata.suprihub.contract.application;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.institutonata.suprihub.contract.application.ServiceOrderService.View;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.domain.Tender;
import org.springframework.stereotype.Component;

/** Ordem de Fornecimento ou de Serviço (Anexo VIII do Edital) em PDF. */
@Component
public class OrderPdf {

    private static final Font TITLE = new Font(Font.HELVETICA, 14, Font.BOLD);
    private static final Font H2 = new Font(Font.HELVETICA, 10.5f, Font.BOLD, new Color(0x0b, 0x4f, 0x6c));
    private static final Font BODY = new Font(Font.HELVETICA, 9.5f);
    private static final Font BOLD = new Font(Font.HELVETICA, 9.5f, Font.BOLD);
    private static final Font SMALL = new Font(Font.HELVETICA, 8);
    private static final Font DRAFT = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0xb0, 0x30, 0x20));
    private static final Color HEADER_BG = new Color(0xe6, 0xf0, 0xf4);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static final List<String> OBLIGATIONS = List.of(
            "executar o objeto com seus próprios meios, pessoal, materiais e equipamentos, sem subordinação, "
                    + "pessoalidade ou habitualidade que caracterizem vínculo empregatício com o Instituto;",
            "responder por encargos trabalhistas, previdenciários, fiscais e comerciais, e por danos causados ao "
                    + "Instituto, aos participantes e a terceiros;",
            "cumprir as normas de segurança, saúde, acessibilidade e sustentabilidade aplicáveis, identificar sua "
                    + "equipe e zelar pelo bom trato aos participantes, em especial pessoas idosas, crianças e "
                    + "adolescentes;",
            "tratar os dados pessoais a que tiver acesso exclusivamente para a execução do objeto, com sigilo "
                    + "(Lei nº 13.709/2018);",
            "abster-se de qualquer prática vedada pela Lei nº 12.846/2013;",
            "manter as condições de habilitação até o pagamento.");

    private final DateTimeFormatter dateTime;
    private final DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final Clock clock;

    public OrderPdf(Clock clock) {
        this.clock = clock;
        this.dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(clock.getZone());
    }

    public byte[] render(View v) {
        ServiceOrder o = v.order();
        Tender t = v.tender();
        SupplierApplication s = v.supplier();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.addTitle(o.getKind().label() + " " + (o.getNumber() == null ? "" : o.getNumber()));
        doc.addCreator("SupriHub — Contrata + NATA");
        doc.open();

        Paragraph head = new Paragraph("INSTITUTO NATA — CONTRATA + NATA", SMALL);
        head.setAlignment(Element.ALIGN_CENTER);
        doc.add(head);
        Paragraph title = new Paragraph(o.getKind().label().toUpperCase(PT_BR) + " Nº "
                + (o.getNumber() == null ? "(a emitir)" : o.getNumber()), TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        if (o.getNumber() == null) {
            Paragraph d = new Paragraph("MINUTA — ainda não emitida", DRAFT);
            d.setAlignment(Element.ALIGN_CENTER);
            doc.add(d);
        }

        PdfPTable info = new PdfPTable(new float[] {1.4f, 4f});
        info.setWidthPercentage(100);
        info.setSpacingBefore(10);
        row(info, "Oportunidade nº", t.getNumber() + " — " + t.getTitle());
        row(info, "Projeto / parceria / rubrica", o.getProject());
        row(info, "Contratante", "Instituto NATA — Novas Atitudes Transformam o Amanhã (Mesquita/RJ)");
        if (s != null) {
            row(info, "Contratado(a)", nz(s.getLegalName()) + " — " + document(s.getDocumentNumber()) + " — "
                    + nz(s.getStreet()) + ", " + nz(s.getAddressNumber()) + " — " + nz(s.getDistrict()) + " — "
                    + (s.getMunicipality() == null ? "" : s.getMunicipality().getName()) + " — " + nz(s.getPhone())
                    + " — " + nz(s.getContactEmail()));
            row(info, "Dados bancários (titularidade própria)", nz(s.getBankName()) + " — agência "
                    + nz(s.getBankBranch()) + " — conta " + nz(s.getBankAccount())
                    + (s.getPixKey() == null ? "" : " — Pix " + s.getPixKey()) + " — titular " + nz(s.getAccountHolder()));
        }
        doc.add(info);

        clause(doc, "Cláusula 1ª — Objeto");
        doc.add(new Paragraph(o.getObjectDescription(), BODY));
        PdfPTable items = new PdfPTable(new float[] {0.5f, 4f, 0.9f, 1f, 1.3f, 1.4f});
        items.setWidthPercentage(100);
        items.setSpacingBefore(6);
        for (String h : List.of("#", "Item", "Unid.", "Qtd.", "Preço unit.", "Subtotal")) {
            header(items, h);
        }
        for (ServiceOrder.Item i : o.getItems()) {
            cell(items, String.valueOf(i.getPosition()));
            cell(items, i.getDescription());
            cell(items, i.getUnit());
            cell(items, qty(i.getQuantity()));
            cell(items, i.getUnitPrice() == null ? "na proposta" : money(i.getUnitPrice()));
            cell(items, i.getUnitPrice() == null ? "—" : money(i.getUnitPrice().multiply(i.getQuantity())));
        }
        doc.add(items);
        doc.add(new Paragraph("Conforme a proposta do(a) contratado(a), que integra esta Ordem.", SMALL));

        clause(doc, "Cláusula 2ª — Preço e pagamento");
        doc.add(new Paragraph("Valor total: " + money(o.getTotalValue()) + ". Pagamento por transferência "
                + "eletrônica/Pix para a conta indicada, em até " + o.getPaymentDays() + " dias após o recebimento "
                + "definitivo e a apresentação da nota fiscal ou recibo (RPA), com as retenções legais. Sinal: "
                + (o.getUpfrontValue() == null ? "não" : money(o.getUpfrontValue())) + ".", BODY));

        clause(doc, "Cláusula 3ª — Prazo e local");
        doc.add(new Paragraph("Execução/entrega: " + nz(o.getSchedule()) + ", em " + nz(o.getLocation())
                + ". Vigência desta Ordem: até o recebimento definitivo e o pagamento.", BODY));

        clause(doc, "Cláusula 4ª — Obrigações do contratado");
        for (int i = 0; i < OBLIGATIONS.size(); i++) {
            doc.add(new Paragraph((char) ('a' + i) + ") " + OBLIGATIONS.get(i), BODY));
        }

        clause(doc, "Cláusula 5ª — Obrigações do Instituto");
        doc.add(new Paragraph("Fornecer as informações e o acesso necessários à execução; receber o objeto e "
                + "formalizar o Termo de Recebimento e Avaliação; efetuar o pagamento no prazo; comunicar ocorrências.",
                BODY));

        clause(doc, "Cláusula 6ª — Alterações, rescisão e sanções");
        doc.add(new Paragraph("Alterações somente por escrito, de comum acordo, limitadas a 25% do valor. A Ordem "
                + "poderá ser rescindida por inexecução, atraso injustificado, perda de habilitação ou fato "
                + "superveniente, sem prejuízo das sanções do Anexo XII do Edital. Multa por inexecução: "
                + (o.getPenaltyPct() == null ? "não prevista" : o.getPenaltyPct().stripTrailingZeros().toPlainString()
                        + "% sobre o valor da Ordem") + ".", BODY));

        clause(doc, "Cláusula 7ª — Disposições gerais");
        doc.add(new Paragraph("Aplicam-se o Edital de Credenciamento, seus anexos e a Política de Credenciamento. "
                + "Fica eleito o foro da Comarca de Mesquita/RJ.", BODY));
        if (o.getNotes() != null) {
            doc.add(new Paragraph("Observações: " + o.getNotes(), BODY));
        }

        clause(doc, "Assinaturas");
        doc.add(new Paragraph(o.getIssuedAt() == null ? "Ordem ainda não emitida."
                : "Emitida pelo Instituto em " + dateTime.format(o.getIssuedAt()) + ".", BODY));
        if (o.getSignedAt() != null) {
            doc.add(new Paragraph("ACCEPTANCE".equals(o.getSignedVia())
                    ? "Faixa 1: aceite eletrônico da oportunidade em " + dateTime.format(o.getSignedAt())
                            + ", com os mesmos efeitos da assinatura (Edital 9.2)."
                    : "Assinada eletronicamente pelo(a) contratado(a) em " + dateTime.format(o.getSignedAt()) + ".",
                    BODY));
        } else if (o.getSignatureDueDate() != null) {
            doc.add(new Paragraph("Aguardando a assinatura do(a) contratado(a) até "
                    + date.format(o.getSignatureDueDate()) + " (Edital 9.1).", BODY));
        }
        Paragraph foot = new Paragraph("Gerado pelo SupriHub em " + dateTime.format(clock.instant()) + ".", SMALL);
        foot.setSpacingBefore(12);
        doc.add(foot);
        doc.close();
        return out.toByteArray();
    }

    private static void clause(Document doc, String text) {
        Paragraph p = new Paragraph(text, H2);
        p.setSpacingBefore(9);
        p.setSpacingAfter(3);
        doc.add(p);
    }

    private static void row(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, BOLD));
        l.setBackgroundColor(HEADER_BG);
        l.setPadding(4);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value == null || value.isBlank() ? "—" : value, BODY));
        v.setPadding(4);
        t.addCell(v);
    }

    private static void header(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, SMALL));
        c.setBackgroundColor(HEADER_BG);
        c.setPadding(3);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, SMALL));
        c.setPadding(3);
        t.addCell(c);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String money(BigDecimal v) {
        return String.format(PT_BR, "R$ %,.2f", v);
    }

    private static String qty(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String document(String digits) {
        if (digits == null) {
            return "";
        }
        if (digits.length() == 14) {
            return digits.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        if (digits.length() == 11) {
            return digits.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        }
        return digits;
    }
}
