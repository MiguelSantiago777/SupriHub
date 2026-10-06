package org.institutonata.suprihub.contract.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.contract.domain.ContractEnums.FileKind;
import org.institutonata.suprihub.contract.domain.ContractEnums.ReceiptKind;
import org.institutonata.suprihub.contract.domain.ContractEnums.Status;
import org.institutonata.suprihub.contract.domain.DossierCheck;
import org.institutonata.suprihub.contract.domain.DossierChecklist;
import org.institutonata.suprihub.contract.domain.DossierChecklist.Facts;
import org.institutonata.suprihub.contract.domain.DossierChecklist.Item;
import org.institutonata.suprihub.contract.domain.DossierChecklist.Manual;
import org.institutonata.suprihub.contract.domain.DossierReview;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.contract.domain.ServiceOrderReceipt;
import org.institutonata.suprihub.contract.infra.DossierCheckRepository;
import org.institutonata.suprihub.contract.infra.DossierReviewRepository;
import org.institutonata.suprihub.contract.infra.ServiceOrderFileRepository;
import org.institutonata.suprihub.contract.infra.ServiceOrderReceiptRepository;
import org.institutonata.suprihub.contract.infra.ServiceOrderRepository;
import org.institutonata.suprihub.judgment.infra.JudgmentRepository;
import org.institutonata.suprihub.judgment.model.Judgment;
import org.institutonata.suprihub.judgment.model.JudgmentEnums;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.proposal.infra.ProposalRepository;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.infra.TenderInvitationRepository;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dossiê da contratação (Anexo XIII) e revisão por amostragem do Controle Interno. */
@Service
public class DossierService {

    public record Dossier(ServiceOrder order, Tender tender, List<Item> items, boolean complete,
            List<DossierReview> reviews) {
    }

    /** Amostra do semestre: dossiês pagos, quantos revisados e o mínimo (10%). */
    public record Sample(List<ServiceOrder> paid, Map<Long, DossierReview> lastReview, int minimum) {
    }

    private final ServiceOrderRepository orders;
    private final ServiceOrderReceiptRepository receipts;
    private final ServiceOrderFileRepository files;
    private final DossierCheckRepository checks;
    private final DossierReviewRepository reviews;
    private final TenderRepository tenders;
    private final TenderInvitationRepository invitations;
    private final ProposalRepository proposals;
    private final JudgmentRepository judgments;
    private final ParameterService parameters;
    private final AuditService audit;
    private final Clock clock;

    public DossierService(ServiceOrderRepository orders, ServiceOrderReceiptRepository receipts,
            ServiceOrderFileRepository files, DossierCheckRepository checks, DossierReviewRepository reviews,
            TenderRepository tenders, TenderInvitationRepository invitations, ProposalRepository proposals,
            JudgmentRepository judgments, ParameterService parameters, AuditService audit, Clock clock) {
        this.orders = orders;
        this.receipts = receipts;
        this.files = files;
        this.checks = checks;
        this.reviews = reviews;
        this.tenders = tenders;
        this.invitations = invitations;
        this.proposals = proposals;
        this.judgments = judgments;
        this.parameters = parameters;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dossier dossier(long orderId) {
        ServiceOrder o = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Ordem"));
        Tender t = tenders.findById(o.getTenderId()).orElseThrow();
        Judgment j = judgments.findFirstByTenderIdAndStatusNot(t.getId(), JudgmentEnums.Status.ANNULLED).orElse(null);
        List<ServiceOrderReceipt> rs = receipts.findByServiceOrderIdOrderByReceivedAt(orderId);
        boolean finalWithEvidence = rs.stream().anyMatch(r -> r.getKind() == ReceiptKind.DEFINITIVE)
                && files.existsByServiceOrderIdAndKind(orderId, FileKind.EVIDENCE);
        Facts f = new Facts(t.getBand(), t.getPublishedAt() != null,
                invitations.findByTenderIdOrderByInvitedAt(t.getId()).size(),
                o.getProposalId() != null || proposals.countByTenderId(t.getId()) > 0,
                j != null && (j.getValidCount() >= j.rules().minValidRequired() || j.getBelowMinimumJustification() != null),
                j != null, j != null && j.getStatus() == JudgmentEnums.Status.HOMOLOGATED && j.getAtaSha256() != null,
                o.getIssuedAt() != null, o.getSignedAt() != null,
                files.existsByServiceOrderIdAndKind(orderId, FileKind.INVOICE),
                o.getStatus() == Status.PAID && files.existsByServiceOrderIdAndKind(orderId, FileKind.PAYMENT_PROOF),
                finalWithEvidence);
        Map<Integer, Manual> manual = new HashMap<>();
        checks.findByServiceOrderId(orderId).forEach(c -> manual.put(c.getItemNo(), new Manual(c.isChecked(),
                c.getNote())));
        List<Item> items = DossierChecklist.evaluate(f, manual);
        return new Dossier(o, t, items, DossierChecklist.complete(items),
                reviews.findByServiceOrderIdOrderByReviewedAtDesc(orderId));
    }

    /** Confirma (ou desmarca) um item manual: atesto do DFD, estimativa, publicação, justificativa. */
    @Transactional
    public Dossier check(long orderId, int itemNo, boolean checked, String note, long userId) {
        if (!DossierChecklist.isManual(itemNo)) {
            throw new BusinessException("ITEM_AUTOMATIC", "Este item é conferido automaticamente pelo sistema.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        orders.findById(orderId).orElseThrow(() -> new NotFoundException("Ordem"));
        DossierCheck c = checks.findById(new DossierCheck.Key(orderId, (short) itemNo))
                .orElseGet(() -> new DossierCheck(orderId, itemNo));
        c.mark(checked, note, userId, clock.instant());
        checks.save(c);
        audit.record("dossier", orderId, "CHECK", null, Map.of("item", itemNo, "checked", checked));
        return dossier(orderId);
    }

    /** Revisão do Controle Interno: registra se o dossiê estava sem pendência. */
    @Transactional
    public Dossier review(long orderId, String note, long userId) {
        Dossier d = dossier(orderId);
        reviews.save(new DossierReview(orderId, d.complete(), note, userId, clock.instant()));
        audit.record("dossier", orderId, "REVIEW", null, Map.of("complete", d.complete()), note);
        return dossier(orderId);
    }

    @Transactional(readOnly = true)
    public Sample sample(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(clock.getZone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        List<ServiceOrder> paid = orders.findAll().stream()
                .filter(o -> o.getStatus() == Status.PAID && o.getPaidOn() != null
                        && !o.getPaidOn().isBefore(from) && !o.getPaidOn().isAfter(to))
                .toList();
        Map<Long, DossierReview> last = new HashMap<>();
        for (DossierReview r : reviews.findByReviewedAtBetween(start, end)) {
            last.merge(r.getServiceOrderId(), r, (a, b) -> a.getReviewedAt().isAfter(b.getReviewedAt()) ? a : b);
        }
        BigDecimal share = parameters.decimal(ParameterKey.DOSSIER_REVIEW_SAMPLE_SHARE);
        int min = paid.isEmpty() ? 0
                : BigDecimal.valueOf(paid.size()).multiply(share).setScale(0, RoundingMode.CEILING).intValue();
        return new Sample(paid, last, Math.max(min, paid.isEmpty() ? 0 : 1));
    }
}
