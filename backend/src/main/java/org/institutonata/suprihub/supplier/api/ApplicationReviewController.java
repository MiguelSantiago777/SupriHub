package org.institutonata.suprihub.supplier.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.institutonata.suprihub.supplier.api.SupplierDtos.ApplicationDto;
import org.institutonata.suprihub.supplier.api.SupplierDtos.ApplicationSummary;
import org.institutonata.suprihub.supplier.api.SupplierDtos.DecisionRequest;
import org.institutonata.suprihub.supplier.api.SupplierDtos.RequiredMessageRequest;
import org.institutonata.suprihub.supplier.api.SupplierDtos.ValidityRequest;
import org.institutonata.suprihub.supplier.application.AppealService;
import org.institutonata.suprihub.supplier.application.ApplicationReviewService;
import org.institutonata.suprihub.supplier.application.SupplierApplicationService;
import org.institutonata.suprihub.supplier.application.SupplierMaintenanceService;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/commission/applications")
@PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN')")
@Tag(name = "Análise de requerimentos")
public class ApplicationReviewController {

    private final ApplicationReviewService review;
    private final SupplierApplicationService applications;
    private final CalendarService calendar;
    private final AppealService appeals;
    private final SupplierMaintenanceService maintenance;

    public ApplicationReviewController(ApplicationReviewService review, SupplierApplicationService applications,
            CalendarService calendar, AppealService appeals, SupplierMaintenanceService maintenance) {
        this.maintenance = maintenance;
        this.review = review;
        this.applications = applications;
        this.calendar = calendar;
        this.appeals = appeals;
    }

    @GetMapping
    @Operation(summary = "Fila de requerimentos por situação (mais antigos primeiro)")
    public PageResponse<ApplicationSummary> list(@RequestParam(defaultValue = "SUBMITTED") ApplicationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Sort sort = status == ApplicationStatus.DRAFT ? Sort.by("updatedAt").descending()
                : status == ApplicationStatus.SUBMITTED || status == ApplicationStatus.ADJUSTMENT_REQUESTED
                        ? Sort.by("submittedAt").ascending()
                        : Sort.by("decidedAt").descending();
        LocalDate today = calendar.today();
        return PageResponse.of(review.search(status, q, PageRequest.of(page, Math.min(size, 100), sort)),
                a -> ApplicationSummary.of(a, today));
    }

    @GetMapping("/counts")
    @Operation(summary = "Quantidade de requerimentos por situação")
    public Map<ApplicationStatus, Long> counts() {
        return review.counts();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Requerimento completo, documentos e andamento")
    public ApplicationDto get(@PathVariable long id) {
        return dto(review.get(id));
    }

    @GetMapping("/{id}/documents/{documentId}/file")
    @Operation(summary = "Abre um documento do requerimento")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id, @PathVariable long documentId) {
        SupplierDocument doc = review.document(id, documentId);
        return Downloads.of(doc, applications.open(doc));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Habilita o fornecedor (credenciamento por 24 meses)")
    public ApplicationDto approve(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        return dto(review.approve(id, CurrentUser.requireId(), req.message(), req.version()));
    }

    @PostMapping("/{id}/request-adjustment")
    @Operation(summary = "Pede ajuste ao fornecedor (prazo de 5 dias úteis)")
    public ApplicationDto requestAdjustment(@PathVariable long id, @Valid @RequestBody RequiredMessageRequest req) {
        return dto(review.requestAdjustment(id, CurrentUser.requireId(), req.message(), req.version()));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Inabilita, com motivação obrigatória")
    public ApplicationDto reject(@PathVariable long id, @Valid @RequestBody RequiredMessageRequest req) {
        return dto(review.reject(id, CurrentUser.requireId(), req.message(), req.version()));
    }

    // ------------------------------------------------- manutenção do cadastro

    @PostMapping(path = "/{id}/documents/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Anexa certidão obtida pela Comissão no sítio oficial (já conferida)")
    public ApplicationDto uploadCertificate(@PathVariable long id, @PathVariable DocumentType type,
            @RequestParam("file") MultipartFile file,
            @RequestParam("validUntil") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil) {
        applications.uploadByCommission(id, type, file, validUntil, CurrentUser.requireId());
        return dto(review.get(id));
    }

    @PutMapping("/{id}/documents/{documentId}/validity")
    @Operation(summary = "Confere (e corrige, se preciso) a validade de uma certidão")
    public ApplicationDto validity(@PathVariable long id, @PathVariable long documentId,
            @Valid @RequestBody ValidityRequest req) {
        maintenance.setValidity(review.get(id), documentId, req.validUntil(), CurrentUser.requireId(), true);
        return dto(review.get(id));
    }

    @PostMapping("/{id}/recheck")
    @Operation(summary = "Registra a reconferência semestral das certidões")
    public ApplicationDto recheck(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        return dto(maintenance.recheck(id, CurrentUser.requireId(), req.message(), req.version()));
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renova o credenciamento por mais 24 meses")
    public ApplicationDto renew(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        return dto(maintenance.renew(id, CurrentUser.requireId(), req.message(), req.version()));
    }

    @PostMapping("/{id}/return-renewal")
    @Operation(summary = "Devolve o pedido de renovação com o que falta")
    public ApplicationDto returnRenewal(@PathVariable long id, @Valid @RequestBody RequiredMessageRequest req) {
        return dto(maintenance.returnRenewal(id, CurrentUser.requireId(), req.message(), req.version()));
    }

    private ApplicationDto dto(SupplierApplication app) {
        return ApplicationDto.of(app, applications.documents(app.getId()), applications.history(app.getId()),
                List.of(), appeals.latestFor(app.getId()).orElse(null), calendar.today(),
                app.getStatus() == ApplicationStatus.APPROVED ? maintenance.of(app) : null);
    }
}
