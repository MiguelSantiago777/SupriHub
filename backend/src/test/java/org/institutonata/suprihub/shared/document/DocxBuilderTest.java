package org.institutonata.suprihub.shared.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.institutonata.suprihub.shared.document.DocxBuilder.Cell;
import org.junit.jupiter.api.Test;

class DocxBuilderTest {

    static Map<String, String> unzip(byte[] docx) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    void geraDocxValidoComTextoETabela() throws Exception {
        byte[] docx = new DocxBuilder()
                .title("ANEXO VII — MODELO DE PROPOSTA")
                .text("Ação & preço <com> \"aspas\"")
                .table(new int[] {1, 3}, List.of(List.of(Cell.head("Item"), Cell.of("Cadeira\nde plástico")),
                        List.of(Cell.head("Total"))))
                .build();

        assertThat(docx[0]).isEqualTo((byte) 'P');
        assertThat(docx[1]).isEqualTo((byte) 'K');
        Map<String, String> files = unzip(docx);
        assertThat(files).containsKeys("[Content_Types].xml", "_rels/.rels", "word/document.xml");
        String doc = files.get("word/document.xml");
        assertThat(doc).contains("ANEXO VII — MODELO DE PROPOSTA", "Ação &amp; preço &lt;com&gt; &quot;aspas&quot;",
                "Cadeira", "de plástico", "w:gridSpan w:val=\"2\"");
        for (String xml : files.values()) {
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    void removeCaracteresDeControleInvalidosEmXml() {
        assertThat(DocxBuilder.escape("a\u0001b\tc")).isEqualTo("ab\tc");
    }
}
