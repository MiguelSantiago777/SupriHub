package org.institutonata.suprihub.sanction.api;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.sanction.api.SanctionDtos.BoardRequest;
import org.institutonata.suprihub.sanction.api.SanctionDtos.DecisionRequest;
import org.institutonata.suprihub.sanction.api.SanctionDtos.Detail;
import org.institutonata.suprihub.sanction.api.SanctionDtos.InfractionDto;
import org.institutonata.suprihub.sanction.api.SanctionDtos.OpenRequest;
import org.institutonata.suprihub.sanction.api.SanctionDtos.Summary;
import org.institutonata.suprihub.sanction.application.SanctionService;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Infraction;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Status;
import org.institutonata.suprihub.sanction.domain.SanctionFile;
import org.institutonata.suprihub.sanction.domain.SanctionProcess;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Processos sancionadores para a equipe. Instaurar e decidir: Comissão. Recurso: Diretoria.
 * Consulta: Controle Interno e Diretoria.
 */
@RestController
@RequestMapping("/api/sanctions")
@PreAuthorize("hasAnyRole('COMMISSION', 'BOARD', 'INTERNAL_CONTROL', 'ADMIN')")
@Tag(name = "Sanções (equipe)")
public class SanctionController {

    private static final String COMMISSION = "hasAnyRole('COMMISSION', 'ADMIN')";

    private final SanctionService sanctions;
    private final CalendarService calendar;

    public SanctionController(SanctionService sanctions, CalendarService calendar) {
        this.sanctions = sanctions;
        this.calendar = calendar;
    }

    @GetMapping("/infractions")
    @Operation(summary = "Tabela de infrações e sanções (Anexo XII)")
    public List<InfractionDto> infractions() {
        return Arrays.stream(Infraction.values()).map(i -> new InfractionDto(i, i.label(), i.reference())).toList();
    }

    @GetMapping
    public PageResponse<Summary> list(@RequestParam(defaultValue = "DEFENSE") List<Status> status,
            @RequestParam(defaultValue = "0") int page) {
        return PageResponse.of(sanctions.search(status, PageRequest.of(page, 20, Sort.by("openedAt").descending())),
                p -> Summary.of(p, sanctions.view(p.getId()).supplier()));
    }

    @GetMapping("/counts")
    public Map<Status, Long> counts() {
        return sanctions.counts();
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable long id) {
        return detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(COMMISSION)
    @Operation(summary = "Instaura o processo e notifica o fornecedor (defesa em 5 d.u.)")
    public Detail open(@Valid @RequestBody OpenRequest req) {
        SanctionProcess p = sanctions.open(req.applicationId(), req.serviceOrderId(), req.infraction(), req.facts(),
                req.interimBlock(), CurrentUser.requireId());
        return detail(p.getId());
    }

    @PostMapping(path = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(COMMISSION)
    public Detail evidence(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        sanctions.addEvidence(id, file, CurrentUser.requireId());
        return detail(id);
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize(COMMISSION)
    @Operation(summary = "Decisão motivada da Comissão (abre o prazo de recurso)")
    public Detail decide(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        sanctions.decide(id, req.toDecision(), req.reason(), CurrentUser.requireId(), req.version());
        return detail(id);
    }

    @PostMapping("/{id}/board-decision")
    @PreAuthorize("hasRole('BOARD')")
    @Operation(summary = "Decisão final da Diretoria sobre o recurso")
    public Detail board(@PathVariable long id, @Valid @RequestBody BoardRequest req) {
        sanctions.boardDecide(id, req.outcome(), req.reformed(), req.reason(), CurrentUser.requireId(), req.version());
        return detail(id);
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<InputStreamResource> file(@PathVariable long id, @PathVariable long fileId) {
        return download(sanctions, sanctions.file(id, fileId));
    }

    static ResponseEntity<InputStreamResource> download(SanctionService sanctions, SanctionFile f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getFileType()))
                .contentLength(f.getFileSize())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(sanctions.open(f)));
    }

    private Detail detail(long id) {
        return Detail.of(sanctions.view(id), calendar.today());
    }
}
