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
import org.institutonata.suprihub.supplier.api.SupplierDtos.AppealDecisionRequest;
import org.institutonata.suprihub.supplier.api.SupplierDtos.AppealDetail;
import org.institutonata.suprihub.supplier.api.SupplierDtos.AppealDto;
import org.institutonata.suprihub.supplier.api.SupplierDtos.AppealSummary;
import org.institutonata.suprihub.supplier.api.SupplierDtos.ApplicationDto;
import org.institutonata.suprihub.supplier.api.SupplierDtos.BoardDecisionRequest;
import org.institutonata.suprihub.supplier.application.AppealService;
import org.institutonata.suprihub.supplier.application.AppealService.Stage;
import org.institutonata.suprihub.supplier.application.ApplicationReviewService;
import org.institutonata.suprihub.supplier.application.SupplierApplicationService;
import org.institutonata.suprihub.supplier.domain.Appeal;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recursos da inabilitação. Comissão reconsidera ou encaminha; Diretoria decide os
 * encaminhados. O ADMIN pode agir nas duas etapas.
 */
@RestController
@RequestMapping("/api/appeals")
@PreAuthorize("hasAnyRole('COMMISSION', 'BOARD', 'ADMIN')")
@Tag(name = "Recursos")
public class AppealController {

    private final AppealService appeals;
    private final SupplierApplicationService applications;
    private final ApplicationReviewService review;
    private final CalendarService calendar;

    public AppealController(AppealService appeals, SupplierApplicationService applications,
            ApplicationReviewService review, CalendarService calendar) {
        this.appeals = appeals;
        this.applications = applications;
        this.review = review;
        this.calendar = calendar;
    }

    @GetMapping
    @Operation(summary = "Recursos por etapa: COMMISSION, BOARD ou DECIDED")
    public PageResponse<AppealSummary> list(@RequestParam(defaultValue = "COMMISSION") Stage stage,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Sort sort = stage == Stage.DECIDED ? Sort.by("filedAt").descending() : Sort.by("filedAt").ascending();
        LocalDate today = calendar.today();
        return PageResponse.of(appeals.search(stage, PageRequest.of(page, Math.min(size, 100), sort)),
                a -> AppealSummary.of(a, appeals.applicationOf(a), today));
    }

    @GetMapping("/counts")
    @Operation(summary = "Quantidade de recursos por etapa")
    public Map<Stage, Long> counts() {
        return appeals.counts();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recurso com o requerimento completo")
    public AppealDetail get(@PathVariable long id) {
        return detail(appeals.get(id));
    }

    @GetMapping("/{id}/attachment")
    @Operation(summary = "Anexo do recurso")
    public ResponseEntity<InputStreamResource> attachment(@PathVariable long id) {
        Appeal appeal = appeals.get(id);
        return Downloads.of(appeal.attachment(), appeals.openAttachment(appeal));
    }

    @GetMapping("/{id}/documents/{documentId}/file")
    @Operation(summary = "Documento do requerimento recorrido (para a Diretoria)")
    public ResponseEntity<InputStreamResource> document(@PathVariable long id, @PathVariable long documentId) {
        Appeal appeal = appeals.get(id);
        SupplierDocument doc = review.document(appeal.getApplicationId(), documentId);
        return Downloads.of(doc, applications.open(doc));
    }

    @PostMapping("/{id}/reconsider")
    @PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN')")
    @Operation(summary = "Comissão reconsidera: recurso provido e fornecedor habilitado")
    public AppealDetail reconsider(@PathVariable long id, @Valid @RequestBody AppealDecisionRequest req) {
        return detail(appeals.reconsider(id, CurrentUser.requireId(), req.reason(), req.version()));
    }

    @PostMapping("/{id}/forward")
    @PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN')")
    @Operation(summary = "Comissão mantém a decisão e encaminha à Diretoria (5 dias úteis)")
    public AppealDetail forward(@PathVariable long id, @Valid @RequestBody AppealDecisionRequest req) {
        return detail(appeals.forward(id, CurrentUser.requireId(), req.reason(), req.version()));
    }

    @PostMapping("/{id}/board-decision")
    @PreAuthorize("hasAnyRole('BOARD', 'ADMIN')")
    @Operation(summary = "Diretoria dá ou nega provimento ao recurso")
    public AppealDetail boardDecision(@PathVariable long id, @Valid @RequestBody BoardDecisionRequest req) {
        return detail(appeals.boardDecision(id, CurrentUser.requireId(), req.granted(), req.reason(),
                req.version()));
    }

    private AppealDetail detail(Appeal appeal) {
        SupplierApplication app = appeals.applicationOf(appeal);
        ApplicationDto dto = ApplicationDto.of(app, applications.documents(app.getId()),
                applications.history(app.getId()), List.of(), appeal, calendar.today(), null);
        return new AppealDetail(AppealDto.of(appeal), dto);
    }
}
