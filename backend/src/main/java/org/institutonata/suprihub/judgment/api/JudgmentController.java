package org.institutonata.suprihub.judgment.api;

import java.time.Instant;

import jakarta.validation.Valid;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.AnnulRequest;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.DecisionRequest;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.JudgmentDto;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.JudgmentPanel;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.ReasonRequest;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.StartRequest;
import org.institutonata.suprihub.judgment.application.JudgmentService;
import org.institutonata.suprihub.judgment.application.JudgmentService.Snapshot;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.Status;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Julgamento das Faixas 2 e 3 pela Comissão (Manual 4.2, passos 5 e 6). */
@RestController
@RequestMapping("/api/tenders/{tenderId}/judgment")
@PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN')")
@Tag(name = "Julgamento (Comissão)")
public class JudgmentController {

    private final JudgmentService judgments;
    private final ObjectMapper json;

    public JudgmentController(JudgmentService judgments, ObjectMapper json) {
        this.judgments = judgments;
        this.json = json;
    }

    @GetMapping
    @Operation(summary = "Mapa comparativo, cobertura, sorteio e resultado")
    public JudgmentPanel get(@PathVariable long tenderId) {
        return panel(tenderId);
    }

    @PostMapping("/start")
    @Operation(summary = "Julga as propostas válidas (empate ficto, preferência local, desempate, sorteio)")
    public JudgmentPanel start(@PathVariable long tenderId, @Valid @RequestBody StartRequest req) {
        judgments.start(tenderId, req.justification(), CurrentUser.requireId());
        return panel(tenderId);
    }

    @PostMapping("/proposals/{proposalId}/disqualify")
    @Operation(summary = "Desclassifica a proposta, com motivo (antes de julgar)")
    public JudgmentPanel disqualify(@PathVariable long tenderId, @PathVariable long proposalId,
            @Valid @RequestBody ReasonRequest req) {
        judgments.disqualify(tenderId, proposalId, req.reason(), CurrentUser.requireId());
        return panel(tenderId);
    }

    @PostMapping("/proposals/{proposalId}/requalify")
    @Operation(summary = "Desfaz a desclassificação")
    public JudgmentPanel requalify(@PathVariable long tenderId, @PathVariable long proposalId) {
        judgments.requalify(tenderId, proposalId);
        return panel(tenderId);
    }

    @PostMapping("/homologate")
    @Operation(summary = "Faixa 2: homologa o resultado e congela a ata")
    public JudgmentPanel homologate(@PathVariable long tenderId, @Valid @RequestBody DecisionRequest req) {
        judgments.homologate(liveId(tenderId), req.note(), CurrentUser.requireId(), false, req.version());
        return panel(tenderId);
    }

    @PostMapping("/annul")
    @Operation(summary = "Anula o julgamento em curso (permite julgar de novo)")
    public JudgmentPanel annul(@PathVariable long tenderId, @Valid @RequestBody AnnulRequest req) {
        judgments.annul(liveId(tenderId), req.reason(), CurrentUser.requireId(), false, req.version());
        return panel(tenderId);
    }

    private long liveId(long tenderId) {
        Snapshot s = judgments.snapshot(tenderId);
        if (s.judgment() == null || !s.judgment().getStatus().isOpen()) {
            throw new NotFoundException("Julgamento em curso");
        }
        return s.judgment().getId();
    }

    private JudgmentPanel panel(long tenderId) {
        Tender t = judgments.tender(tenderId);
        Snapshot s = judgments.snapshot(tenderId);
        boolean live = s.judgment() != null && s.judgment().getStatus() != Status.ANNULLED;
        boolean canStart = !live && t.getBand() != Band.FAIXA_1 && !t.proposalsSealed(Instant.now())
                && (t.getStatus() == TenderEnums.Status.OPEN || t.getStatus() == TenderEnums.Status.JUDGING);
        return new JudgmentPanel(canStart, judgments.minValidRequired(t),
                s.judgment() == null ? null : JudgmentDto.of(t, s, json));
    }
}
