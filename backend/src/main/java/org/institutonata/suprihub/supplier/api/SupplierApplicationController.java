package org.institutonata.suprihub.supplier.api;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.supplier.api.SupplierDtos.ApplicationDto;
import org.institutonata.suprihub.supplier.api.SupplierDtos.ApplicationRequest;
import org.institutonata.suprihub.supplier.api.SupplierDtos.DocumentDto;
import org.institutonata.suprihub.supplier.api.SupplierDtos.VersionRequest;
import org.institutonata.suprihub.supplier.application.SupplierApplicationService;
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness.Issue;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/supplier/application")
@PreAuthorize("hasRole('SUPPLIER')")
@Tag(name = "Requerimento do fornecedor")
public class SupplierApplicationController {

    private final SupplierApplicationService service;

    public SupplierApplicationController(SupplierApplicationService service) {
        this.service = service;
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

    private ApplicationDto dto(SupplierApplication app) {
        List<Issue> pending = app.getStatus().isEditableBySupplier() ? service.pendingItems(app) : List.of();
        return ApplicationDto.of(app, service.documents(app.getId()), service.history(app.getId()), pending);
    }
}
