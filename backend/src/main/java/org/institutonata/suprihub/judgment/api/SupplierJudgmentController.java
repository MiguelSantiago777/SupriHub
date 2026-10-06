package org.institutonata.suprihub.judgment.api;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.CoverRequest;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.MyJudgment;
import org.institutonata.suprihub.judgment.application.JudgmentService;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.tender.application.OpportunityService;
import org.institutonata.suprihub.tender.application.OpportunityService.View;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** O fornecedor no julgamento: direito de cobrir (Edital 8.2) e resultado homologado. */
@RestController
@RequestMapping("/api/supplier/opportunities/{tenderId}/judgment")
@PreAuthorize("hasRole('SUPPLIER')")
@Tag(name = "Julgamento (fornecedor)")
public class SupplierJudgmentController {

    private static final MyJudgment NONE = new MyJudgment(false, null, null);

    private final JudgmentService judgments;
    private final OpportunityService opportunities;

    public SupplierJudgmentController(JudgmentService judgments, OpportunityService opportunities) {
        this.judgments = judgments;
        this.opportunities = opportunities;
    }

    @GetMapping
    @Operation(summary = "Minha convocação para cobrir e o resultado (depois de homologado)")
    public MyJudgment get(@PathVariable long tenderId) {
        return mine(tenderId);
    }

    @PostMapping("/cover")
    @Operation(summary = "Exerce o direito de cobrir com valor menor que a melhor proposta")
    public MyJudgment cover(@PathVariable long tenderId, @Valid @RequestBody CoverRequest req) {
        judgments.cover(tenderId, applicationId(tenderId), req.value());
        return mine(tenderId);
    }

    @PostMapping("/cover/decline")
    @Operation(summary = "Não quer cobrir: a vez passa à próxima")
    public MyJudgment decline(@PathVariable long tenderId) {
        judgments.declineCover(tenderId, applicationId(tenderId));
        return mine(tenderId);
    }

    /** Só quem enviou proposta participa do julgamento. */
    private long applicationId(long tenderId) {
        View v = opportunities.get(CurrentUser.requireId(), tenderId);
        if (v.proposal() == null) {
            throw new NotFoundException("Proposta");
        }
        return v.proposal().getApplicationId();
    }

    private MyJudgment mine(long tenderId) {
        View v = opportunities.get(CurrentUser.requireId(), tenderId);
        if (v.proposal() == null) {
            return NONE;
        }
        return judgments.forSupplier(tenderId, v.proposal().getApplicationId()).map(MyJudgment::of).orElse(NONE);
    }
}
