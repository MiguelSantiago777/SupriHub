package org.institutonata.suprihub.judgment.application;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.judgment.model.CoverOffer;
import org.institutonata.suprihub.judgment.model.DrawRecord;
import org.institutonata.suprihub.judgment.model.Judgment;
import org.institutonata.suprihub.judgment.model.JudgmentEntry;
import org.institutonata.suprihub.tender.domain.Tender;
import org.springframework.stereotype.Component;

/**
 * Ata sucinta do julgamento (Edital 7.5; Anexo VI, parte 6; checklist do dossiê, itens 7 e 8):
 * dados da oportunidade, parâmetros aplicados, mapa comparativo, desclassificações, sorteio,
 * direito de cobrir, resultado e homologação. Sem {@code approval}, sai como minuta.
 *
 * O mapa não traz raça nem CadÚnico por fornecedor (Política art. 28): o critério só aparece
 * no motivo, quando foi ele que decidiu.
 */
@Component
public class AtaPdf {

    /** Quem homologou (Comissão, Faixa 2) ou aprovou (Diretoria, Faixa 3). */
    public record Approval(Long userId, Instant at, String note, boolean byBoard) {
    }

    private static final Font TITLE = new Font(Font.HELVETICA, 14, Font.BOLD);
    private static final Font H2 = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(0x0b, 0x4f, 0x6c));
    private static final Font BODY = new Font(Font.HELVETICA, 9.5f);
    private static final Font BOLD = new Font(Font.HELVETICA, 9.5f, Font.BOLD);
    private static final Font SMALL = new Font(Font.HELVETICA, 8);
    private static final Font SMALL_BOLD = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font DRAFT = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0xb0, 0x30, 0x20));
    private static final Color HEADER_BG = new Color(0xe6, 0xf0, 0xf4);

    private final AppUserRepository users;
    private final ObjectMapper json;
    private final Clock clock;
    private final DateTimeFormatter dateTime;
    private final DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public AtaPdf(AppUserRepository users, ObjectMapper json, Clock clock) {
        this.users = users;
        this.json = json;
        this.clock = clock;
        this.dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(clock.getZone());
    }

    public byte[] render(Tender t, Judgment j, List<JudgmentEntry> entries, List<CoverOffer> covers, DrawRecord draw,
            Approval approval) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.addTitle("Ata de julgamento " + t.getNumber());
        doc.addCreator("SupriHub — Contrata + NATA");
        doc.open();

        doc.add(center(new Paragraph("INSTITUTO NATA — CONTRATA + NATA", SMALL_BOLD)));
        doc.add(center(new Paragraph("ATA DE JULGAMENTO — OPORTUNIDADE Nº " + t.getNumber(), TITLE)));
        if (approval == null) {
            doc.add(center(new Paragraph("MINUTA — documento sem homologação, sujeito a alteração", DRAFT)));
        }
        Map<Long, CoverOffer> coverByProposal = covers.stream()
                .collect(Collectors.toMap(CoverOffer::getProposalId, Function.identity(), (a, b) -> b));

        section(doc, "1. Oportunidade");
        PdfPTable info = new PdfPTable(new float[] {1.3f, 4f});
        info.setWidthPercentage(100);
        row(info, "Objeto", t.getTitle());
        row(info, "Linha", t.getSupplyLine().getCode() + " — " + t.getSupplyLine().getShortName());
        row(info, "Faixa / modo", t.getBand().label() + " — " + t.getBand().selectionMode());
        row(info, "Município de execução",
                t.getExecutionMunicipality() == null ? "não informado" : t.getExecutionMunicipality().getName());
        row(info, "Prazo das propostas", t.getProposalDeadline() == null ? "—" : dateTime.format(t.getProposalDeadline()));
        row(info, "Julgamento aberto em", dateTime.format(j.getStartedAt()) + " por " + name(j.getStartedBy()));
        doc.add(info);

        section(doc, "2. Propostas e parâmetros aplicados");
        long disqualified = entries.stream().filter(JudgmentEntry::isDisqualified).count();
        Judgment.Rules r = j.rules();
        doc.add(new Paragraph("Propostas recebidas: " + entries.size() + "; válidas: " + j.getValidCount()
                + "; desclassificadas: " + disqualified
                + (t.getBand().name().equals("FAIXA_2") ? "; mínimo exigido na Faixa 2: " + r.minValidRequired() : "")
                + ".", BODY));
        if (j.getBelowMinimumJustification() != null) {
            doc.add(new Paragraph("Justificativa por julgar com menos propostas que o mínimo: "
                    + j.getBelowMinimumJustification(), BODY));
        }
        doc.add(new Paragraph("Empate ficto (item 8.2): pequenos negócios até " + pct(r.tieMargin())
                + " acima da menor proposta. Preferência local (item 8.3): até " + pct(r.localMargin())
                + (r.localPreferenceForAll() ? ", aplicada a todos os proponentes" : ", entre as empatadas")
                + ". Desempate (item 8.4): mulher, mulher negra ou inscrita no CadÚnico, MEI, avaliação; "
                + "depois o menor percentual acima e, persistindo, sorteio (item 7.6). Direito de cobrir: valor "
                + (r.coverStrictlyLower() ? "inferior" : "igual ou inferior") + " à menor proposta.", BODY));

        section(doc, "3. Mapa comparativo");
        PdfPTable map = new PdfPTable(new float[] {0.5f, 3.6f, 1.1f, 0.8f, 1.3f, 0.9f, 0.9f, 0.8f, 1.2f, 2.6f});
        map.setWidthPercentage(100);
        map.setHeaderRows(1);
        for (String h : List.of("Pos.", "Fornecedor", "Porte", "Terr.", "Valor (R$)", "% acima", "Empate ficto",
                "Mulher", "Situação", "Prioridade sobre a menor")) {
            header(map, h);
        }
        for (JudgmentEntry e : entries) {
            if (e.isDisqualified()) {
                continue;
            }
            cell(map, String.valueOf(e.getPosition()));
            cell(map, e.getSupplierName());
            cell(map, e.getSupplierType() == null ? "—" : e.getSupplierType().name());
            cell(map, e.getTerritory() == null ? "—" : e.getTerritory() == Territory.OUT_OF_STATE ? "Fora" : e.getTerritory().name());
            cell(map, num(e.getTotalValue()));
            cell(map, pct(e.getPctAbove()));
            cell(map, e.isFictitiousTie() ? "Sim" : e.isLocalPreference() ? "Pref. local" : "—");
            cell(map, e.isWomanLed() ? "Sim" : "Não");
            cell(map, e.isFinalist() ? (e.isDrawn() ? "Finalista (sorteio)" : "Finalista") : "Fora da margem");
            cell(map, e.getAdvantage() == null ? "—" : e.getAdvantage().label());
        }
        doc.add(map);
        doc.add(new Paragraph("Território da sede em relação à execução: T1 = Mesquita e município de execução; "
                + "T2 = Região Metropolitana; T3 = demais municípios do Estado.", SMALL));

        if (disqualified > 0) {
            section(doc, "4. Propostas desclassificadas");
            PdfPTable dq = new PdfPTable(new float[] {3.5f, 1.3f, 5f});
            dq.setWidthPercentage(100);
            header(dq, "Fornecedor");
            header(dq, "Valor (R$)");
            header(dq, "Motivo");
            entries.stream().filter(JudgmentEntry::isDisqualified).forEach(e -> {
                cell(dq, e.getSupplierName());
                cell(dq, num(e.getTotalValue()));
                cell(dq, e.getDisqualifiedReason());
            });
            doc.add(dq);
        }

        if (draw != null) {
            section(doc, "5. Sorteio (item 7.6)");
            Map<Long, String> names = entries.stream()
                    .collect(Collectors.toMap(JudgmentEntry::getProposalId, JudgmentEntry::getSupplierName));
            StringBuilder sb = new StringBuilder("Realizado pelo sistema em ").append(dateTime.format(draw.getDrawnAt()))
                    .append(", semente ").append(draw.getSeed()).append(" (").append(draw.getAlgorithm())
                    .append("). Com a mesma semente o resultado se reproduz. Ordem sorteada: ");
            List<List<Long>> groups = groups(draw);
            for (int g = 0; g < groups.size(); g++) {
                sb.append(g == 0 ? "" : "; ").append(groups.get(g).stream().map(names::get)
                        .collect(Collectors.joining(", ")));
            }
            doc.add(new Paragraph(sb.append('.').toString(), BODY));
        }

        if (!covers.isEmpty()) {
            section(doc, "6. Direito de cobrir (item 8.2)");
            PdfPTable ct = new PdfPTable(new float[] {0.6f, 3.6f, 1.6f, 1.3f, 2.2f, 1.4f});
            ct.setWidthPercentage(100);
            for (String h : List.of("Pos.", "Fornecedor", "Convocada em", "Prazo", "Situação", "Valor ofertado")) {
                header(ct, h);
            }
            Map<Long, String> names = entries.stream()
                    .collect(Collectors.toMap(JudgmentEntry::getProposalId, JudgmentEntry::getSupplierName));
            for (CoverOffer c : covers) {
                cell(ct, String.valueOf(c.getPosition()));
                cell(ct, names.get(c.getProposalId()));
                cell(ct, dateTime.format(c.getOfferedAt()));
                cell(ct, date.format(c.getDueDate()));
                cell(ct, c.getStatus().label());
                cell(ct, c.getOfferedValue() == null ? "—" : num(c.getOfferedValue()));
            }
            doc.add(ct);
        }

        section(doc, "7. Resultado");
        if (j.getWinnerProposalId() == null) {
            doc.add(new Paragraph("Situação: " + j.getStatus().label() + ". Resultado ainda não definido.", BODY));
        } else {
            JudgmentEntry w = entries.stream().filter(e -> e.getProposalId().equals(j.getWinnerProposalId()))
                    .findFirst().orElseThrow();
            Paragraph p = new Paragraph();
            p.add(new Phrase("Vencedora: ", BOLD));
            p.add(new Phrase(w.getSupplierName() + " — valor adjudicado R$ " + num(j.getAwardedValue())
                    + (coverByProposal.containsKey(w.getProposalId()) ? " (após cobrir)" : "") + ".", BODY));
            doc.add(p);
            doc.add(new Paragraph("Motivo: " + j.getWinnerReason(), BODY));
            doc.add(new Paragraph("Resultado definido em " + dateTime.format(j.getDecidedAt()) + ".", BODY));
        }

        section(doc, "8. " + (t.getBand().name().equals("FAIXA_3") ? "Aprovação da Diretoria" : "Homologação"));
        if (approval == null) {
            doc.add(new Paragraph(j.getStatus().label() + ". "
                    + (t.getBand().name().equals("FAIXA_3") ? "Na Faixa 3 a ata é aprovada pela Diretoria."
                            : "Na Faixa 2 a Comissão homologa o resultado."), BODY));
        } else {
            doc.add(new Paragraph((approval.byBoard() ? "Aprovada pela Diretoria" : "Homologada pela Comissão")
                    + " em " + dateTime.format(approval.at()) + " por " + name(approval.userId()) + "."
                    + (approval.note() == null || approval.note().isBlank() ? "" : " Observação: " + approval.note().trim()),
                    BODY));
        }

        Paragraph foot = new Paragraph("Gerado pelo SupriHub em " + dateTime.format(clock.instant())
                + ". O hash SHA-256 da ata homologada fica registrado no sistema e na trilha de auditoria.", SMALL);
        foot.setSpacingBefore(14);
        doc.add(foot);
        doc.close();
        return out.toByteArray();
    }

    private List<List<Long>> groups(DrawRecord draw) {
        try {
            return json.readValue(draw.getGroupsJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String name(Long userId) {
        if (userId == null) {
            return "sistema";
        }
        return users.findById(userId).map(AppUser::getFullName).orElse("usuário #" + userId);
    }

    private static Paragraph center(Paragraph p) {
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static void section(Document doc, String title) {
        Paragraph p = new Paragraph(title, H2);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        doc.add(p);
    }

    private static void row(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, BOLD));
        l.setBackgroundColor(HEADER_BG);
        l.setPadding(4);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "—" : value, BODY));
        v.setPadding(4);
        t.addCell(v);
    }

    private static void header(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, SMALL_BOLD));
        c.setBackgroundColor(HEADER_BG);
        c.setPadding(3);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "—" : text, SMALL));
        c.setPadding(3);
        t.addCell(c);
    }

    private static String num(BigDecimal v) {
        return v == null ? "—" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%,.2f", v);
    }

    private static String pct(BigDecimal v) {
        return v == null ? "—" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f%%",
                v.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
    }
}
