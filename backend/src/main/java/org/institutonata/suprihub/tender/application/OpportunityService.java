package org.institutonata.suprihub.tender.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.proposal.domain.Proposal;
import org.institutonata.suprihub.proposal.infra.ProposalRepository;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums.InvitationStatus;
import org.institutonata.suprihub.tender.domain.TenderEnums.Status;
import org.institutonata.suprihub.tender.domain.TenderInvitation;
import org.institutonata.suprihub.tender.infra.TenderInvitationRepository;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oportunidades vistas pelo fornecedor: abertas da sua linha e território (Faixas 2 e 3),
 * convites do rodízio (Faixa 1) e aquelas em que já enviou proposta.
 */
@Service
public class OpportunityService {

    /** Oportunidade com a situação do fornecedor nela. */
    public record View(Tender tender, TenderInvitation invitation, Proposal proposal, boolean canSubmit,
            boolean canWithdraw) {
    }

    private final TenderRepository tenders;
    private final TenderInvitationRepository invitations;
    private final ProposalRepository proposals;
    private final SupplierApplicationRepository applications;
    private final RotationService rotation;
    private final CalendarService calendar;
    private final AuditService audit;
    private final Clock clock;

    public OpportunityService(TenderRepository tenders, TenderInvitationRepository invitations,
            ProposalRepository proposals, SupplierApplicationRepository applications, RotationService rotation,
            CalendarService calendar, AuditService audit, Clock clock) {
        this.tenders = tenders;
        this.invitations = invitations;
        this.proposals = proposals;
        this.applications = applications;
        this.rotation = rotation;
        this.calendar = calendar;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<View> list(long userId) {
        Optional<SupplierApplication> found = applications.findByUserId(userId);
        if (found.isEmpty()) {
            return List.of();
        }
        SupplierApplication app = found.get();
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        Map<Long, Tender> visible = new LinkedHashMap<>();
        List<String> codes = app.getLines().stream().map(SupplyLine::getCode).toList();
        if (!codes.isEmpty()) {
            tenders.findOpenByLines(Status.OPEN, codes, Band.FAIXA_1).stream()
                    .filter(t -> t.acceptsOpenProposals(now) && EligibleSuppliers.eligible(t, app, today))
                    .forEach(t -> visible.put(t.getId(), t));
        }
        Map<Long, TenderInvitation> myInvitations = invitations
                .findByApplicationIdAndStatusIn(app.getId(), List.of(InvitationStatus.values())).stream()
                .collect(Collectors.toMap(TenderInvitation::getTenderId, i -> i, (a, b) -> b));
        Map<Long, Proposal> myProposals = proposals.findByApplicationId(app.getId()).stream()
                .collect(Collectors.toMap(Proposal::getTenderId, p -> p));
        for (Long id : myInvitations.keySet()) {
            tenders.findById(id).ifPresent(t -> visible.putIfAbsent(id, t));
        }
        for (Long id : myProposals.keySet()) {
            tenders.findById(id).ifPresent(t -> visible.putIfAbsent(id, t));
        }
        List<View> result = new ArrayList<>();
        for (Tender t : visible.values()) {
            result.add(view(t, app, myInvitations.get(t.getId()), myProposals.get(t.getId()), now, today));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public View get(long userId, long tenderId) {
        SupplierApplication app = application(userId);
        Tender t = tenders.findById(tenderId).orElseThrow(() -> new NotFoundException("Oportunidade"));
        TenderInvitation inv = invitations.findByTenderIdAndApplicationId(tenderId, app.getId()).orElse(null);
        Proposal p = proposals.findByTenderIdAndApplicationId(tenderId, app.getId()).orElse(null);
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        boolean openToMe = t.getStatus() != Status.DRAFT && t.getBand() != Band.FAIXA_1
                && EligibleSuppliers.eligible(t, app, today);
        if (!openToMe && inv == null && p == null) {
            throw new NotFoundException("Oportunidade");
        }
        return view(t, app, inv, p, now, today);
    }

    /** Rodízio: o fornecedor recusa o convite e a vez passa ao próximo. */
    @Transactional
    public void decline(long userId, long tenderId, String reason) {
        SupplierApplication app = application(userId);
        TenderInvitation inv = invitations.findByTenderIdAndApplicationId(tenderId, app.getId())
                .orElseThrow(() -> new NotFoundException("Convite"));
        if (!inv.canRespond(calendar.today())) {
            throw new BusinessException("INVITATION_CLOSED", "Este convite não está mais aguardando resposta.",
                    HttpStatus.CONFLICT);
        }
        inv.decline(reason, clock.instant());
        invitations.flush();
        audit.record("tender_invitation", inv.getId(), "DECLINE", Map.of("status", "INVITED"),
                Map.of("status", inv.getStatus().name()));
        rotation.registerRefusal(inv);
        tenders.findById(tenderId).ifPresent(rotation::inviteNext);
    }

    @Transactional(readOnly = true)
    public SupplierApplication application(long userId) {
        return applications.findByUserId(userId).orElseThrow(() -> new BusinessException("NO_APPLICATION",
                "Faça o seu credenciamento para participar das oportunidades.", HttpStatus.FORBIDDEN));
    }

    private View view(Tender t, SupplierApplication app, TenderInvitation inv, Proposal p, Instant now,
            LocalDate today) {
        boolean canSubmit;
        boolean canWithdraw;
        if (t.getBand() == Band.FAIXA_1) {
            canSubmit = t.getStatus() == Status.OPEN && inv != null && inv.canRespond(today);
            canWithdraw = false;
        } else {
            canSubmit = t.acceptsOpenProposals(now) && EligibleSuppliers.eligible(t, app, today);
            canWithdraw = canSubmit && p != null;
        }
        return new View(t, inv, p, canSubmit, canWithdraw);
    }
}
