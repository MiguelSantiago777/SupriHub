package org.institutonata.suprihub.judgment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.CoverRequest;
import org.institutonata.suprihub.judgment.api.JudgmentDtos.MyJudgment;
import org.institutonata.suprihub.judgment.application.JudgmentAppealService;
import org.institutonata.suprihub.judgment.application.JudgmentService;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.tender.application.OpportunityService;
import org.institutonata.suprihub.tender.application.OpportunityService.View;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** O fornecedor no julgamento: direito de cobrir (Edital 8.2) e resultado homologado. */
@RestController
@Validated
@RequestMapping("/api/supplier/opportunities/{tenderId}/judgment")
@PreAuthorize("hasRole('SUPPLIER')")
@Tag(name = "Julgamento (fornecedor)")
public class SupplierJudgmentController {

    private static final MyJudgment NONE = new MyJudgment(false, null, null, null);

    private final JudgmentService judgments;
    private final OpportunityService opportunities;
    private final JudgmentAppealService appeals;

    public SupplierJudgmentController(JudgmentService judgments, OpportunityService opportunities,
            JudgmentAppealService appeals) {
        this.judgments = judgments;
        this.opportunities = opportunities;
        this.appeals = appeals;
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

    @PostMapping(path = "/appeal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recorre do resultado homologado (Anexo X) — 3 dias úteis da homologação")
    public MyJudgment appeal(@PathVariable long tenderId,
            @RequestParam("reasons") @NotBlank(message = "Escreva as razões do recurso.") @Size(max = 5000)
            String reasons,
            @RequestParam("request") @NotBlank(message = "Escreva o seu pedido.") @Size(max = 2000) String request,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        applicationId(tenderId);
        appeals.file(CurrentUser.requireId(), tenderId, reasons, request, file);
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
        long applicationId = v.proposal().getApplicationId();
        MyJudgment base = judgments.forSupplier(tenderId, applicationId)
                .map(sv -> MyJudgment.of(sv, appeals.canAppeal(tenderId, applicationId))).orElse(NONE);
        return base.withAppeal(appeals.mine(tenderId, applicationId).orElse(null));
    }
}
