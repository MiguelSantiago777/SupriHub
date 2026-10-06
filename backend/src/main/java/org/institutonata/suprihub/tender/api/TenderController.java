package org.institutonata.suprihub.tender.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.proposal.application.ProposalService;
import org.institutonata.suprihub.proposal.domain.Proposal;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.api.TenderDtos.InvitationDto;
import org.institutonata.suprihub.tender.api.TenderDtos.ItemReference;
import org.institutonata.suprihub.tender.application.PriceReferenceSource;
import org.institutonata.suprihub.tender.api.TenderDtos.ItemDto;
import org.institutonata.suprihub.tender.api.TenderDtos.LineDto;
import org.institutonata.suprihub.tender.api.TenderDtos.ProposalRow;
import org.institutonata.suprihub.tender.api.TenderDtos.ReasonRequest;
import org.institutonata.suprihub.tender.api.TenderDtos.TenderDetail;
import org.institutonata.suprihub.tender.api.TenderDtos.TenderRequest;
import org.institutonata.suprihub.tender.api.TenderDtos.TenderSummary;
import org.institutonata.suprihub.tender.api.TenderDtos.TerritoriesRequest;
import org.institutonata.suprihub.tender.api.TenderDtos.VersionRequest;
import org.institutonata.suprihub.tender.application.TenderService;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums.InvitationStatus;
import org.institutonata.suprihub.tender.domain.TenderEnums.Status;
import org.institutonata.suprihub.tender.domain.TenderInvitation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenders")
@PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN')")
@Tag(name = "Oportunidades (Comissão)")
public class TenderController {

    private final TenderService tenders;
    private final ProposalService proposals;
    private final List<PriceReferenceSource> priceReferences;

    public TenderController(TenderService tenders, ProposalService proposals,
            List<PriceReferenceSource> priceReferences) {
        this.tenders = tenders;
        this.proposals = proposals;
        this.priceReferences = priceReferences;
    }

    /** Abas: rascunhos, abertas (inclui prazo encerrado), concluídas (selecionadas/canceladas). */
    @GetMapping
    @Operation(summary = "Oportunidades por situação")
    public PageResponse<TenderSummary> list(@RequestParam(defaultValue = "OPEN") List<Status> status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Instant now = Instant.now();
        return PageResponse.of(tenders.search(status, PageRequest.of(page, Math.min(size, 100),
                Sort.by("updatedAt").descending())), t -> summary(t, now));
    }

    @GetMapping("/counts")
    public Map<Status, Long> counts() {
        return tenders.counts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um rascunho (a faixa sai do valor estimado)")
    public TenderDetail create(@Valid @RequestBody TenderRequest req) {
        return detail(tenders.create(CurrentUser.requireId(), req.toData(), req.lineCode(),
                req.executionMunicipalityId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera o rascunho")
    public TenderDetail update(@PathVariable long id, @Valid @RequestBody TenderRequest req) {
        return detail(tenders.update(id, req.toData(), req.lineCode(), req.executionMunicipalityId(),
                req.version()));
    }

    @GetMapping("/{id}")
    public TenderDetail get(@PathVariable long id) {
        return detail(tenders.get(id));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publica: Faixas 2/3 avisam os elegíveis; Faixa 1 convida o próximo do rodízio")
    public TenderDetail publish(@PathVariable long id, @RequestBody VersionRequest req) {
        return detail(tenders.publish(id, req.version()));
    }

    @PostMapping("/{id}/cancel")
    public TenderDetail cancel(@PathVariable long id, @Valid @RequestBody ReasonRequest req) {
        return detail(tenders.cancel(id, req.reason(), req.version()));
    }

    @PostMapping("/{id}/expand-territories")
    @Operation(summary = "Rodízio sem credenciados: amplia território e convida o próximo")
    public TenderDetail expand(@PathVariable long id, @Valid @RequestBody TerritoriesRequest req) {
        return detail(tenders.expandTerritories(id, req.territories(), req.version()));
    }

    @PostMapping("/invitations/{invitationId}/confirm")
    @Operation(summary = "Rodízio: confirma a seleção depois de conferir o preço")
    public TenderDetail confirm(@PathVariable long invitationId) {
        return detail(tenders.confirmSelection(invitationId));
    }

    @PostMapping("/invitations/{invitationId}/reject-price")
    @Operation(summary = "Rodízio: recusa o preço e convida o próximo")
    public TenderDetail rejectPrice(@PathVariable long invitationId, @Valid @RequestBody ReasonRequest req) {
        return detail(tenders.rejectPrice(invitationId, req.reason()));
    }

    @GetMapping("/{id}/proposals/{proposalId}/file")
    @Operation(summary = "Arquivo da proposta (depois do prazo)")
    public ResponseEntity<InputStreamResource> proposalFile(@PathVariable long id, @PathVariable long proposalId) {
        Tender t = tenders.get(id);
        Proposal p = proposals.get(proposalId);
        if (!p.getTenderId().equals(id) || t.proposalsSealed(Instant.now())) {
            throw new BusinessException("PROPOSALS_SEALED", "As propostas só podem ser abertas depois do prazo.",
                    HttpStatus.FORBIDDEN);
        }
        Proposal.File f = p.file();
        if (f == null) {
            throw new BusinessException("NO_FILE", "Esta proposta não tem arquivo.", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(f.size())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.name(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(proposals.openFile(p)));
    }

    private TenderSummary summary(Tender t, Instant now) {
        List<TenderInvitation> invs = tenders.invitations(t.getId());
        TenderInvitation pending = invs.stream().filter(i -> i.getStatus().isActive()).findFirst().orElse(null);
        String pendingName = pending == null ? null
                : TenderDtos.name(tenders.suppliers(Set.of(pending.getApplicationId())).get(pending.getApplicationId()));
        return new TenderSummary(t.getId(), t.getNumber(), t.getStatus(), t.getStatus().label(), t.getTitle(),
                t.getSupplyLine().getCode(), t.getSupplyLine().getShortName(), t.getBand(), t.getBand().label(),
                t.getEstimatedValue(), t.getProposalDeadline(),
                t.getProposalDeadline() != null && !now.isBefore(t.getProposalDeadline()),
                proposals.countForTender(t.getId()), pendingName, pending == null ? null : pending.getDueDate(),
                t.getPublishedAt(), t.getCreatedAt());
    }

    private TenderDetail detail(Tender t) {
        Instant now = Instant.now();
        List<TenderInvitation> invs = tenders.invitations(t.getId());
        boolean sealed = t.proposalsSealed(now);
        List<Proposal> props = proposals.forTender(t.getId());
        Set<Long> ids = new HashSet<>();
        invs.forEach(i -> ids.add(i.getApplicationId()));
        props.forEach(p -> ids.add(p.getApplicationId()));
        Map<Long, SupplierApplication> apps = tenders.suppliers(ids);
        Map<Long, Proposal> byApp = new java.util.HashMap<>();
        props.forEach(p -> byApp.put(p.getApplicationId(), p));
        List<InvitationDto> invitations = invs.stream()
                .map(i -> InvitationDto.of(i, apps.get(i.getApplicationId()),
                        i.getStatus() == InvitationStatus.INVITED ? null : byApp.get(i.getApplicationId())))
                .toList();
        List<ProposalRow> rows = sealed ? List.of()
                : props.stream().map(p -> ProposalRow.of(p, apps.get(p.getApplicationId()), t)).toList();
        int eligible = t.getStatus() == Status.DRAFT || t.getStatus() == Status.OPEN ? tenders.eligibleCount(t) : 0;
        List<ItemReference> refs = new java.util.ArrayList<>();
        java.math.BigDecimal ceilingTotal = java.math.BigDecimal.ZERO;
        boolean allPriced = !t.getItems().isEmpty();
        for (var item : t.getItems()) {
            var ref = priceReferences.stream()
                    .map(s -> s.referenceFor(t.getSupplyLine().getId(), item.getDescription(), item.getUnit()))
                    .flatMap(java.util.Optional::stream).findFirst();
            if (ref.isPresent()) {
                refs.add(new ItemReference(item.getId(), ref.get().count(), ref.get().average(), ref.get().ceiling()));
                ceilingTotal = ceilingTotal.add(ref.get().ceiling().multiply(item.getQuantity()));
            } else {
                allPriced = false;
            }
        }
        return new TenderDetail(t.getId(), t.getNumber(), t.getStatus(), t.getStatus().label(), t.getTitle(),
                t.getDescription(), new LineDto(t.getSupplyLine().getCode(), t.getSupplyLine().getShortName()),
                t.getExecutionMunicipality() == null ? null : t.getExecutionMunicipality().getId(),
                t.getExecutionMunicipality() == null ? null : t.getExecutionMunicipality().getName(),
                t.getLocation(), t.getSchedule(), t.getExecutionDeadline(), t.getRequirements(), t.getPaymentTerms(),
                t.getAudience(), t.getAudience().label(), t.getEstimatedValue(), t.getBand(), t.getBand().label(),
                t.getBand().selectionMode(), t.getProposalDeadline(),
                t.getProposalDeadline() != null && !now.isBefore(t.getProposalDeadline()), t.getPublishedAt(),
                t.getCancelReason(), t.getTerritories(), t.getItems().stream().map(ItemDto::of).toList(),
                invitations, sealed, props.size(), rows, eligible, refs,
                allPriced ? ceilingTotal.setScale(2, java.math.RoundingMode.HALF_UP) : null, t.getVersion());
    }
}
