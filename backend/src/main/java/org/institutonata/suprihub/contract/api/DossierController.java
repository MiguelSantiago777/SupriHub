package org.institutonata.suprihub.contract.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.contract.application.DossierService;
import org.institutonata.suprihub.contract.application.DossierService.Dossier;
import org.institutonata.suprihub.contract.application.DossierService.Sample;
import org.institutonata.suprihub.contract.domain.DossierChecklist.Item;
import org.institutonata.suprihub.contract.domain.DossierChecklist.State;
import org.institutonata.suprihub.contract.domain.DossierReview;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.report.application.IndicatorService;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dossiê da contratação (Anexo XIII) e revisão por amostragem (Controle Interno). */
@RestController
@RequestMapping("/api/dossiers")
@PreAuthorize("hasAnyRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN')")
@Tag(name = "Dossiê")
public class DossierController {

    public record ItemDto(int no, String label, boolean manual, boolean optional, State state, String detail) {
        static ItemDto of(Item i) {
            return new ItemDto(i.no(), i.label(), i.manual(), i.optional(), i.state(), i.detail());
        }
    }

    public record ReviewDto(Long id, boolean complete, String note, Instant reviewedAt) {
        static ReviewDto of(DossierReview r) {
            return new ReviewDto(r.getId(), r.isComplete(), r.getNote(), r.getReviewedAt());
        }
    }

    public record DossierDto(Long orderId, String orderNumber, String tenderNumber, String bandLabel,
            List<ItemDto> items, boolean complete, int pending, List<ReviewDto> reviews) {
        static DossierDto of(Dossier d) {
            return new DossierDto(d.order().getId(), d.order().getNumber(), d.tender().getNumber(),
                    d.tender().getBand().label(), d.items().stream().map(ItemDto::of).toList(), d.complete(),
                    (int) d.items().stream().filter(i -> i.state() == State.PENDING).count(),
                    d.reviews().stream().map(ReviewDto::of).toList());
        }
    }

    public record CheckRequest(boolean checked, @Size(max = 500) String note) {
    }

    public record ReviewRequest(@Size(max = 2000) String note) {
    }

    public record SampleRow(Long orderId, String orderNumber, BigDecimal value, LocalDate paidOn, Boolean complete,
            Instant reviewedAt) {
    }

    public record SampleDto(LocalDate from, LocalDate to, int paid, int reviewed, int minimum, List<SampleRow> rows) {
    }

    private final DossierService dossiers;
    private final IndicatorService indicators;

    public DossierController(DossierService dossiers, IndicatorService indicators) {
        this.dossiers = dossiers;
        this.indicators = indicators;
    }

    @GetMapping("/{orderId}")
    public DossierDto get(@PathVariable long orderId) {
        return DossierDto.of(dossiers.dossier(orderId));
    }

    @PostMapping("/{orderId}/items/{itemNo}")
    @PreAuthorize("hasAnyRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'ADMIN')")
    @Operation(summary = "Confirma um item manual do Anexo XIII")
    public DossierDto check(@PathVariable long orderId, @PathVariable int itemNo, @RequestBody CheckRequest req) {
        return DossierDto.of(dossiers.check(orderId, itemNo, req.checked(), req.note(), CurrentUser.requireId()));
    }

    @PostMapping("/{orderId}/review")
    @PreAuthorize("hasAnyRole('INTERNAL_CONTROL', 'ADMIN')")
    @Operation(summary = "Registra a revisão do Controle Interno")
    public DossierDto review(@PathVariable long orderId, @RequestBody ReviewRequest req) {
        return DossierDto.of(dossiers.review(orderId, req.note(), CurrentUser.requireId()));
    }

    @GetMapping("/sample")
    @Operation(summary = "Dossiês pagos no período e a amostra mínima para revisão (10%)")
    public SampleDto sample(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] sem = indicators.currentSemester();
        LocalDate f = from == null ? sem[0] : from;
        LocalDate t = to == null ? sem[1] : to;
        Sample s = dossiers.sample(f, t);
        List<SampleRow> rows = s.paid().stream().map((ServiceOrder o) -> {
            DossierReview r = s.lastReview().get(o.getId());
            return new SampleRow(o.getId(), o.getNumber(), o.getTotalValue(), o.getPaidOn(),
                    r == null ? null : r.isComplete(), r == null ? null : r.getReviewedAt());
        }).toList();
        return new SampleDto(f, t, s.paid().size(), s.lastReview().size(), s.minimum(), rows);
    }
}
