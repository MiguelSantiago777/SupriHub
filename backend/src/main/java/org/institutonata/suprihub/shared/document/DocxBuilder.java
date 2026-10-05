package org.institutonata.suprihub.shared.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Gera documentos Word (.docx) simples — títulos, parágrafos e tabelas com borda — sem
 * biblioteca externa (o .docx é um ZIP com XML WordprocessingML). Página A4, margens de 2 cm,
 * fonte Arial. Suficiente para modelos editáveis como o Anexo VII.
 */
public final class DocxBuilder {

    /** Largura útil da página A4 com margens de 2 cm, em twips (1/1440 pol). */
    private static final int PAGE_WIDTH = 9638;

    private final StringBuilder body = new StringBuilder();

    /** Célula de tabela: texto (quebras de linha viram parágrafos) e destaque. */
    public record Cell(String text, boolean bold, boolean shaded) {
        public static Cell of(String text) {
            return new Cell(text, false, false);
        }

        public static Cell head(String text) {
            return new Cell(text, true, true);
        }
    }

    public DocxBuilder title(String text) {
        body.append(paragraph(text, true, 32, "center", 120));
        return this;
    }

    public DocxBuilder subtitle(String text) {
        body.append(paragraph(text, false, 22, "center", 240));
        return this;
    }

    public DocxBuilder heading(String text) {
        body.append(paragraph(text, true, 24, "left", 120));
        return this;
    }

    public DocxBuilder text(String text) {
        body.append(paragraph(text, false, 22, "both", 120));
        return this;
    }

    public DocxBuilder spacer() {
        body.append(paragraph("", false, 22, "left", 0));
        return this;
    }

    /**
     * Tabela com bordas. {@code widths} são proporções das colunas (ex.: 1, 4, 1).
     */
    public DocxBuilder table(int[] widths, List<List<Cell>> rows) {
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        int[] twips = new int[widths.length];
        for (int i = 0; i < widths.length; i++) {
            twips[i] = PAGE_WIDTH * widths[i] / total;
        }
        StringBuilder t = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"").append(PAGE_WIDTH)
                .append("\" w:type=\"dxa\"/><w:tblBorders>");
        for (String side : new String[] {"top", "left", "bottom", "right", "insideH", "insideV"}) {
            t.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"8592A3\"/>");
        }
        t.append("</w:tblBorders><w:tblCellMar><w:left w:w=\"100\" w:type=\"dxa\"/><w:right w:w=\"100\" w:type=\"dxa\"/>")
                .append("</w:tblCellMar></w:tblPr><w:tblGrid>");
        for (int w : twips) {
            t.append("<w:gridCol w:w=\"").append(w).append("\"/>");
        }
        t.append("</w:tblGrid>");
        for (List<Cell> row : rows) {
            t.append("<w:tr>");
            for (int c = 0; c < row.size(); c++) {
                Cell cell = row.get(c);
                int span = c == row.size() - 1 ? widths.length - c : 1;
                int width = 0;
                for (int k = c; k < c + span; k++) {
                    width += twips[k];
                }
                t.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(width).append("\" w:type=\"dxa\"/>");
                if (span > 1) {
                    t.append("<w:gridSpan w:val=\"").append(span).append("\"/>");
                }
                if (cell.shaded()) {
                    t.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E6F3FB\"/>");
                }
                t.append("</w:tcPr>");
                for (String line : (cell.text() == null ? "" : cell.text()).split("\n", -1)) {
                    t.append(paragraph(line, cell.bold(), 20, "left", 40));
                }
                t.append("</w:tc>");
            }
            t.append("</w:tr>");
        }
        t.append("</w:tbl>");
        body.append(t);
        return this;
    }

    public byte[] build() {
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + body
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"709\""
                + " w:footer=\"709\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>";
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument"
                + ".wordprocessingml.document.main+xml\"/></Types>";
        String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                + "/officeDocument\" Target=\"word/document.xml\"/></Relationships>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", contentTypes);
            put(zip, "_rels/.rels", rels);
            put(zip, "word/document.xml", document);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o documento", e);
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String paragraph(String text, boolean bold, int halfPoints, String align, int spacingAfter) {
        return "<w:p><w:pPr><w:spacing w:after=\"" + spacingAfter + "\"/><w:jc w:val=\"" + align + "\"/></w:pPr>"
                + "<w:r><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/>"
                + (bold ? "<w:b/>" : "") + "<w:sz w:val=\"" + halfPoints + "\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r></w:p>";
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                default -> {
                    if (ch >= 0x20 || ch == '\t') {
                        b.append(ch);
                    }
                }
            }
        }
        return b.toString();
    }
}
