package org.institutonata.suprihub.tender.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.proposal.application.ProposalModelService;
import org.institutonata.suprihub.proposal.application.ProposalModelService.Model;
import org.institutonata.suprihub.proposal.application.ProposalService;
import org.institutonata.suprihub.proposal.domain.Proposal;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.shared.storage.UploadedFiles;
import org.institutonata.suprihub.tender.api.TenderDtos.DeclineRequest;
import org.institutonata.suprihub.tender.api.TenderDtos.ItemDto;
import org.institutonata.suprihub.tender.api.TenderDtos.LineDto;
import org.institutonata.suprihub.tender.api.TenderDtos.MyInvitation;
import org.institutonata.suprihub.tender.api.TenderDtos.MyProposal;
import org.institutonata.suprihub.tender.api.TenderDtos.Opportunity;
import org.institutonata.suprihub.tender.api.TenderDtos.ProposalRequest;
import org.institutonata.suprihub.tender.application.OpportunityService;
import org.institutonata.suprihub.tender.application.OpportunityService.View;
import org.institutonata.suprihub.tender.domain.Tender;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** "Editais em aberto" do fornecedor: oportunidades, modelo de proposta e envio. */
@RestController
@RequestMapping("/api/supplier/opportunities")
@PreAuthorize("hasRole('SUPPLIER')")
@Tag(name = "Oportunidades (fornecedor)")
public class OpportunityController {

    private final OpportunityService opportunities;
    private final ProposalService proposals;
    private final ProposalModelService models;
    private final ParameterService parameters;

    public OpportunityController(OpportunityService opportunities, ProposalService proposals,
            ProposalModelService models, ParameterService parameters) {
        this.opportunities = opportunities;
        this.proposals = proposals;
        this.models = models;
        this.parameters = parameters;
    }

    @GetMapping
    @Operation(summary = "Oportunidades abertas para mim, convites do rodízio e minhas propostas")
    public List<Opportunity> list() {
        return opportunities.list(CurrentUser.requireId()).stream().map(this::dto).toList();
    }

    @GetMapping("/{id}")
    public Opportunity get(@PathVariable long id) {
        return dto(opportunities.get(CurrentUser.requireId(), id));
    }

    @GetMapping("/{id}/proposal-model")
    @Operation(summary = "Baixa o Modelo de Proposta (Anexo VII) em Word, já preenchido")
    public ResponseEntity<byte[]> model(@PathVariable long id) {
        Model m = models.build(CurrentUser.requireId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(UploadedFiles.DOCX))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(m.filename(), StandardCharsets.UTF_8).build().toString())
                .body(m.content());
    }

    @PostMapping(path = "/{id}/proposal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia ou substitui a proposta (dados + arquivo opcional)")
    public Opportunity submit(@PathVariable long id, @Valid @RequestPart("data") ProposalRequest data,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        long userId = CurrentUser.requireId();
        proposals.submit(userId, id, new ProposalService.Input(data.mode(),
                data.unitPrices() == null ? Map.of() : data.unitPrices(), data.totalValue(), data.executionDeadline(),
                data.validityDays(), data.specialConditions(), data.notes(), data.declarationAccepted()), file);
        return dto(opportunities.get(userId, id));
    }

    @DeleteMapping("/{id}/proposal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retira a proposta (antes do prazo)")
    public void withdraw(@PathVariable long id) {
        proposals.withdraw(CurrentUser.requireId(), id);
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Rodízio: recusa o convite (passa ao próximo)")
    public Opportunity decline(@PathVariable long id, @Valid @RequestBody DeclineRequest req) {
        long userId = CurrentUser.requireId();
        opportunities.decline(userId, id, req.reason());
        return dto(opportunities.get(userId, id));
    }

    @GetMapping("/{id}/proposal/file")
    @Operation(summary = "Baixa o arquivo da minha proposta")
    public ResponseEntity<InputStreamResource> myFile(@PathVariable long id) {
        View v = opportunities.get(CurrentUser.requireId(), id);
        Proposal p = v.proposal();
        if (p == null || !p.hasFile()) {
            throw new BusinessException("NO_FILE", "Sua proposta não tem arquivo.", HttpStatus.NOT_FOUND);
        }
        Proposal.File f = p.file();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(f.size())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.name(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(proposals.openFile(p)));
    }

    private Opportunity dto(View v) {
        Tender t = v.tender();
        Instant now = Instant.now();
        return new Opportunity(t.getId(), t.getNumber(), t.getStatus(), t.getTitle(), t.getDescription(),
                new LineDto(t.getSupplyLine().getCode(), t.getSupplyLine().getShortName()),
                t.getExecutionMunicipality() == null ? null : t.getExecutionMunicipality().getName(),
                t.getLocation(), t.getSchedule(), t.getExecutionDeadline(), t.getRequirements(), t.getPaymentTerms(),
                t.getAudience().label(), t.getBand(), t.getBand().label(), t.getBand().selectionMode(),
                t.getProposalDeadline(), t.getProposalDeadline() != null && !now.isBefore(t.getProposalDeadline()),
                t.getTerritories(), t.getItems().stream().map(ItemDto::of).toList(), MyInvitation.of(v.invitation()),
                MyProposal.of(v.proposal()), v.canSubmit(), v.canWithdraw(),
                parameters.integer(ParameterKey.PROPOSAL_VALIDITY_DAYS));
    }
}
