package org.institutonata.suprihub.sanction.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.sanction.api.SanctionDtos.Detail;
import org.institutonata.suprihub.sanction.api.SanctionDtos.Summary;
import org.institutonata.suprihub.sanction.api.SanctionDtos.TextRequest;
import org.institutonata.suprihub.sanction.application.SanctionService;
import org.institutonata.suprihub.sanction.application.SanctionService.View;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.application.OpportunityService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** O fornecedor no processo sancionador: notificação, defesa e recurso (contraditório). */
@RestController
@RequestMapping("/api/supplier/sanctions")
@PreAuthorize("hasRole('SUPPLIER')")
@Tag(name = "Sanções (fornecedor)")
public class SupplierSanctionController {

    private final SanctionService sanctions;
    private final OpportunityService opportunities;
    private final CalendarService calendar;

    public SupplierSanctionController(SanctionService sanctions, OpportunityService opportunities,
            CalendarService calendar) {
        this.sanctions = sanctions;
        this.opportunities = opportunities;
        this.calendar = calendar;
    }

    @GetMapping
    public List<Summary> list() {
        long app = applicationId();
        SupplierApplication a = opportunities.application(CurrentUser.requireId());
        return sanctions.forSupplier(app).stream().map(p -> Summary.of(p, a)).toList();
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable long id) {
        return detail(id);
    }

    @PostMapping(path = "/{id}/defense", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Apresenta a defesa (5 dias úteis)")
    public Detail defend(@PathVariable long id, @RequestPart("data") TextRequest req,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        sanctions.defend(applicationId(), id, req.text(), file, CurrentUser.requireId());
        return detail(id);
    }

    @PostMapping(path = "/{id}/appeal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Recorre à Diretoria (5 dias úteis após a decisão)")
    public Detail appeal(@PathVariable long id, @RequestPart("data") TextRequest req,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        sanctions.appeal(applicationId(), id, req.text(), file, CurrentUser.requireId());
        return detail(id);
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<InputStreamResource> file(@PathVariable long id, @PathVariable long fileId) {
        sanctions.viewForSupplier(applicationId(), id);
        return SanctionController.download(sanctions, sanctions.file(id, fileId));
    }

    private long applicationId() {
        return opportunities.application(CurrentUser.requireId()).getId();
    }

    /** O fornecedor não vê o histórico de outros processos nem a marca de reincidência. */
    private Detail detail(long id) {
        View v = sanctions.viewForSupplier(applicationId(), id);
        Detail d = Detail.of(new View(v.process(), v.supplier(), v.order(), v.files(), List.of(), false),
                calendar.today());
        return d;
    }
}
