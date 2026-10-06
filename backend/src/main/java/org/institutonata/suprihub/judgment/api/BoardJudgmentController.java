package org.institutonata.suprihub.judgment.api;

import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.AnnulRequest;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.BoardDetail;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.BoardItem;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.DecisionRequest;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.JudgmentDto;
import org.institutonata.suprihub.judgment.application.JudgmentService;
import org.institutonata.suprihub.judgment.application.JudgmentService.Snapshot;
import org.institutonata.suprihub.judgment.model.Judgment;
import org.institutonata.suprihub.judgment.model.JudgmentEntry;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.tender.domain.Tender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Diretoria: aprova (ou devolve) a ata de julgamento da Faixa 3 (checklist do dossiê, item 8). */
@RestController
@RequestMapping("/api/board/judgments")
@PreAuthorize("hasRole('BOARD')")
@Tag(name = "Julgamento (Diretoria)")
public class BoardJudgmentController {

    private final JudgmentService judgments;
    private final ObjectMapper json;

    public BoardJudgmentController(JudgmentService judgments, ObjectMapper json) {
        this.judgments = judgments;
        this.json = json;
    }

    @GetMapping
    @Operation(summary = "Atas da Faixa 3 aguardando aprovação")
    public List<BoardItem> pending() {
        return judgments.awaitingBoard().stream().map(j -> {
            Tender t = judgments.tender(j.getTenderId());
            Snapshot s = judgments.snapshot(t.getId());
            String winner = s.entries().stream().filter(e -> e.getProposalId().equals(j.getWinnerProposalId()))
                    .map(JudgmentEntry::getSupplierName).findFirst().orElse(null);
            return new BoardItem(j.getId(), t.getId(), t.getNumber(), t.getTitle(), t.getSupplyLine().getCode(),
                    winner, j.getAwardedValue(), t.getEstimatedValue(), j.getDecidedAt());
        }).toList();
    }

    @GetMapping("/{judgmentId}")
    public BoardDetail get(@PathVariable long judgmentId) {
        return detail(judgments.get(judgmentId));
    }

    @PostMapping("/{judgmentId}/approve")
    @Operation(summary = "Aprova a ata (homologa) da Faixa 3")
    public BoardDetail approve(@PathVariable long judgmentId, @Valid @RequestBody DecisionRequest req) {
        return detail(judgments.homologate(judgmentId, req.note(), CurrentUser.requireId(), true, req.version()));
    }

    @PostMapping("/{judgmentId}/return")
    @Operation(summary = "Devolve a ata: anula o julgamento para a Comissão refazer")
    public BoardDetail giveBack(@PathVariable long judgmentId, @Valid @RequestBody AnnulRequest req) {
        return detail(judgments.annul(judgmentId, req.reason(), CurrentUser.requireId(), true, req.version()));
    }

    private BoardDetail detail(Judgment j) {
        Tender t = judgments.tender(j.getTenderId());
        Snapshot s = judgments.snapshot(t.getId());
        Snapshot own = s.judgment() != null && s.judgment().getId().equals(j.getId()) ? s
                : new Snapshot(j, List.of(), List.of(), null, List.of());
        return new BoardDetail(t.getId(), t.getNumber(), t.getTitle(), t.getDescription(), t.getSupplyLine().getCode(),
                t.getSupplyLine().getShortName(), t.getBand().label(), t.getEstimatedValue(),
                t.getExecutionMunicipality() == null ? null : t.getExecutionMunicipality().getName(),
                t.getProposalDeadline(), JudgmentDto.of(t, own, json));
    }
}
