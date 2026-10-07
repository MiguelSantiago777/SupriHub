package org.institutonata.suprihub.contract.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.contract.api.OrderDtos.AcknowledgeRequest;
import org.institutonata.suprihub.contract.api.OrderDtos.OrderDetail;
import org.institutonata.suprihub.contract.api.OrderDtos.UrgencySummary;
import org.institutonata.suprihub.contract.api.OrderDtos.UrgentRequest;
import org.institutonata.suprihub.contract.application.ServiceOrderService;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.tender.application.TenderService;
import org.institutonata.suprihub.tender.domain.Tender;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contratação de urgência (Política art. 13, § 3º; Manual 4.3): o gestor (ou a Comissão)
 * contrata direto um credenciado da linha; a Comissão toma ciência em 2 d.u.
 */
@RestController
@RequestMapping("/api/urgent-contracts")
@Tag(name = "Contratação de urgência")
public class UrgencyController {

    private final TenderService tenders;
    private final ServiceOrderService orders;
    private final CalendarService calendar;

    public UrgencyController(TenderService tenders, ServiceOrderService orders, CalendarService calendar) {
        this.tenders = tenders;
        this.orders = orders;
        this.calendar = calendar;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DEMAND_MANAGER', 'COMMISSION', 'ADMIN')")
    @Operation(summary = "Contrata direto um credenciado da linha, com justificativa; devolve a ordem em preparação")
    public OrderDetail create(@Valid @RequestBody UrgentRequest req) {
        Tender t = tenders.contractUrgently(CurrentUser.requireId(), req.toData(), req.lineCode(),
                req.executionMunicipalityId(), req.applicationId(), req.justification());
        ServiceOrder o = orders.byTender(t.getId()).orElseThrow(() -> new NotFoundException("Ordem de fornecimento"));
        return OrderDetail.of(orders.view(o.getId()), calendar.today());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN')")
    @Operation(summary = "Urgências aguardando a ciência da Comissão")
    public List<UrgencySummary> pending() {
        LocalDate today = calendar.today();
        return tenders.urgenciesAwaitingAcknowledgement().stream()
                .map(t -> new UrgencySummary(t.getId(), t.getNumber(), t.getTitle(), t.getSupplyLine().getCode(),
                        t.getEstimatedValue(), t.getUrgencyJustification(), t.getUrgencyAckDue(),
                        t.getUrgencyAckDue() != null && t.getUrgencyAckDue().isBefore(today),
                        orders.byTender(t.getId()).map(ServiceOrder::getId).orElse(null)))
                .toList();
    }

    @PostMapping("/{tenderId}/acknowledge")
    @PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN')")
    @Operation(summary = "A Comissão toma ciência da contratação de urgência")
    public OrderDetail acknowledge(@PathVariable long tenderId, @Valid @RequestBody AcknowledgeRequest req) {
        tenders.acknowledgeUrgency(tenderId, CurrentUser.requireId(), req.note());
        ServiceOrder o = orders.byTender(tenderId).orElseThrow(() -> new NotFoundException("Ordem de fornecimento"));
        return OrderDetail.of(orders.view(o.getId()), calendar.today());
    }
}
