package org.institutonata.suprihub.supplier.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.supplier.api.SupplierDtos.ApplicationDto;
import org.institutonata.suprihub.supplier.api.SupplierDtos.ApplicationRequest;
import org.institutonata.suprihub.supplier.api.SupplierDtos.DocumentDto;
import org.institutonata.suprihub.supplier.api.SupplierDtos.VersionRequest;
import org.institutonata.suprihub.supplier.application.AppealService;
import org.institutonata.suprihub.supplier.application.SupplierApplicationService;
import org.institutonata.suprihub.supplier.domain.Appeal;
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness.Issue;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/supplier/application")
@PreAuthorize("hasRole('SUPPLIER')")
@Tag(name = "Requerimento do fornecedor")
public class SupplierApplicationController {

    private final SupplierApplicationService service;
    private final AppealService appeals;
    private final CalendarService calendar;

    public SupplierApplicationController(SupplierApplicationService service, AppealService appeals,
            CalendarService calendar) {
        this.service = service;
        this.appeals = appeals;
        this.calendar = calendar;
    }

    @GetMapping
    @Operation(summary = "Meu requerimento (cria o rascunho na primeira visita)")
    public ApplicationDto get() {
        return dto(service.getOrCreate(CurrentUser.requireId()));
    }

    @PutMapping
    @Operation(summary = "Salva o rascunho")
    public ApplicationDto save(@Valid @RequestBody ApplicationRequest req) {
        return dto(service.saveDraft(CurrentUser.requireId(), req.toData(), req.version()));
    }

    @PostMapping(path = "/documents/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia (ou substitui) um documento — PDF, JPG ou PNG")
    public DocumentDto upload(@PathVariable DocumentType type, @RequestParam("file") MultipartFile file) {
        return DocumentDto.of(service.upload(CurrentUser.requireId(), type, file));
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um documento")
    public void deleteDocument(@PathVariable long id) {
        service.deleteDocument(CurrentUser.requireId(), id);
    }

    @GetMapping("/documents/{id}/file")
    @Operation(summary = "Baixa um documento enviado")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id) {
        SupplierDocument doc = service.ownDocument(CurrentUser.requireId(), id);
        return Downloads.of(doc, service.open(doc));
    }

    @PostMapping("/submit")
    @Operation(summary = "Envia para análise da Comissão (exige requerimento completo)")
    public ApplicationDto submit(@RequestBody VersionRequest req) {
        return dto(service.submit(CurrentUser.requireId(), req.version()));
    }

    @PostMapping(path = "/appeal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recorre da inabilitação (Anexo X) — 3 dias úteis da comunicação")
    public ApplicationDto appeal(@RequestParam("reasons") @NotBlank(message = "Escreva as razões do recurso.")
            @Size(max = 5000) String reasons,
            @RequestParam("request") @NotBlank(message = "Escreva o seu pedido.") @Size(max = 2000) String request,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        long userId = CurrentUser.requireId();
        appeals.file(userId, reasons, request, file);
        return dto(service.getOrCreate(userId));
    }

    @GetMapping("/appeal/attachment")
    @Operation(summary = "Baixa o anexo do próprio recurso")
    public ResponseEntity<InputStreamResource> appealAttachment() {
        SupplierApplication app = service.getOrCreate(CurrentUser.requireId());
        Appeal appeal = appeals.latestFor(app.getId()).orElseThrow(() -> new NotFoundException("Recurso"));
        return Downloads.of(appeal.attachment(), appeals.openAttachment(appeal));
    }

    private ApplicationDto dto(SupplierApplication app) {
        boolean editable = app.getStatus().isEditableBySupplier() && !service.hasOpenAppeal(app);
        List<Issue> pending = editable ? service.pendingItems(app) : List.of();
        return ApplicationDto.of(app, service.documents(app.getId()), service.history(app.getId()), pending,
                appeals.latestFor(app.getId()).orElse(null), calendar.today());
    }
}
