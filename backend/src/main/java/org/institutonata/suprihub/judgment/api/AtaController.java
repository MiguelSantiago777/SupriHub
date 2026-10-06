package org.institutonata.suprihub.judgment.api;

import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.judgment.application.JudgmentService;
import org.institutonata.suprihub.judgment.model.Judgment;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ata do julgamento em PDF: congelada depois da homologação, minuta antes. */
@RestController
@RequestMapping("/api/judgments")
@PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN', 'BOARD', 'INTERNAL_CONTROL')")
@Tag(name = "Julgamento (ata)")
public class AtaController {

    private final JudgmentService judgments;

    public AtaController(JudgmentService judgments) {
        this.judgments = judgments;
    }

    @GetMapping("/{judgmentId}/ata")
    @Operation(summary = "Baixa a ata de julgamento (PDF)")
    public ResponseEntity<byte[]> ata(@PathVariable long judgmentId) {
        Judgment j = judgments.get(judgmentId);
        String number = judgments.tender(j.getTenderId()).getNumber();
        String name = (j.getAtaKey() == null ? "minuta-ata-" : "ata-") + number + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(judgments.ata(judgmentId));
    }
}
