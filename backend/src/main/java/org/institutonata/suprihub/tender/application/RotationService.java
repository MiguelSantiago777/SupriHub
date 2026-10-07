package org.institutonata.suprihub.tender.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.RotationPolicy;
import org.institutonata.suprihub.tender.domain.RotationDisplacement;
import org.institutonata.suprihub.tender.domain.RotationPolicy.Candidate;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums.InvitationStatus;
import org.institutonata.suprihub.tender.domain.TenderEnums.Status;
import org.institutonata.suprihub.tender.domain.TenderEvents;
import org.institutonata.suprihub.tender.domain.TenderInvitation;
import org.institutonata.suprihub.tender.infra.RotationDisplacementRepository;
import org.institutonata.suprihub.tender.infra.TenderInvitationRepository;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rodízio da Faixa 1 (Política art. 13; Manual 4.1): convida o próximo da fila; quem não
 * aceita em 2 dias úteis (ou recusa) dá lugar ao seguinte.
 */
@Service
public class RotationService {

    private static final Logger log = LoggerFactory.getLogger(RotationService.class);

    private final TenderRepository tenders;
    private final TenderInvitationRepository invitations;
    private final RotationDisplacementRepository displacements;
    private final EligibleSuppliers eligibleSuppliers;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;

    public RotationService(TenderRepository tenders, TenderInvitationRepository invitations,
            RotationDisplacementRepository displacements, EligibleSuppliers eligibleSuppliers,
            ParameterService parameters, CalendarService calendar, DomainEvents events, AuditService audit,
            Clock clock) {
        this.tenders = tenders;
        this.invitations = invitations;
        this.displacements = displacements;
        this.eligibleSuppliers = eligibleSuppliers;
        this.parameters = parameters;
        this.calendar = calendar;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Convida o próximo da fila, se a oportunidade for da Faixa 1, estiver aberta e sem
     * convite ativo. Vazio = ninguém disponível (a Comissão pode ampliar o território).
     */
    @Transactional
    public Optional<TenderInvitation> inviteNext(Tender tender) {
        if (tender.getBand() != Band.FAIXA_1 || tender.getStatus() != Status.OPEN
                || invitations.existsByTenderIdAndStatusIn(tender.getId(),
                        List.of(InvitationStatus.INVITED, InvitationStatus.ACCEPTED))) {
            return Optional.empty();
        }
        LocalDate today = calendar.today();
        List<SupplierApplication> eligible = eligibleSuppliers.of(tender, today);
        Set<Long> invited = invitations.findByTenderIdOrderByInvitedAt(tender.getId()).stream()
                .map(TenderInvitation::getApplicationId).collect(Collectors.toSet());
        Map<Long, Instant> lastSelected = new HashMap<>();
        if (!eligible.isEmpty()) {
            List<Long> ids = eligible.stream().map(SupplierApplication::getId).toList();
            for (Object[] row : invitations.lastSelection(InvitationStatus.CONFIRMED, ids)) {
                lastSelected.put((Long) row[0], (Instant) row[1]);
            }
            // Deslocado ao final por recusas injustificadas: conta como seleção (art. 13, § 4º).
            for (Object[] row : displacements.lastDisplacement(ids)) {
                lastSelected.merge((Long) row[0], (Instant) row[1], (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        List<Candidate> candidates = eligible.stream()
                .map(a -> new Candidate(a.getId(), lastSelected.get(a.getId()), a.getDecidedAt()))
                .toList();
        Optional<Candidate> next = RotationPolicy.next(candidates, invited);
        if (next.isEmpty()) {
            log.info("Oportunidade #{}: nenhum credenciado disponível no rodízio", tender.getId());
            return Optional.empty();
        }
        Instant now = clock.instant();
        LocalDate due = calendar.calendar().addBusinessDays(today,
                parameters.integer(ParameterKey.BAND1_ACCEPTANCE_BUSINESS_DAYS));
        TenderInvitation invitation = invitations.save(
                new TenderInvitation(tender.getId(), next.get().applicationId(), due, now));
        audit.record("tender_invitation", invitation.getId(), "INVITE", null,
                Map.of("tenderId", tender.getId(), "applicationId", invitation.getApplicationId(),
                        "dueDate", due.toString()));
        events.publish(DomainEvent.of(TenderEvents.INVITATION_CREATED, TenderEvents.AGGREGATE, tender.getId(),
                Map.of("tenderId", tender.getId(), "invitationId", invitation.getId(),
                        "applicationId", invitation.getApplicationId()), clock));
        return Optional.of(invitation);
    }

    /**
     * Depois de uma recusa ou de um convite sem resposta: com {@code rotation_unjustified_refusals}
     * (2) recusas injustificadas seguidas, o credenciado vai para o final da fila (Política
     * art. 13, § 4º). Na reincidência, a Comissão é avisada de que cabe advertência (Anexo XII).
     */
    @Transactional
    public void registerRefusal(TenderInvitation last) {
        if (!new RotationPolicy.Response(last.getInvitedAt(), last.getStatus(), last.getDeclineReason())
                .unjustifiedRefusal()) {
            return;
        }
        int refusals = parameters.integer(ParameterKey.ROTATION_UNJUSTIFIED_REFUSALS);
        List<RotationPolicy.Response> responses = invitations.findByApplicationIdAndStatusIn(last.getApplicationId(),
                        List.of(InvitationStatus.CONFIRMED, InvitationStatus.DECLINED, InvitationStatus.EXPIRED,
                                InvitationStatus.PRICE_REJECTED)).stream()
                .map(i -> new RotationPolicy.Response(i.getInvitedAt(), i.getStatus(), i.getDeclineReason()))
                .toList();
        Optional<RotationDisplacement> previous =
                displacements.findFirstByApplicationIdOrderByDisplacedAtDesc(last.getApplicationId());
        if (!RotationPolicy.shouldDisplace(responses, refusals, previous.map(RotationDisplacement::getDisplacedAt)
                .orElse(null))) {
            return;
        }
        Instant now = clock.instant();
        RotationDisplacement d = displacements.save(new RotationDisplacement(last.getApplicationId(),
                last.getTenderId(), now, refusals + " recusas injustificadas seguidas no rodízio (sem resposta no "
                        + "prazo ou recusa sem motivo).", previous.isPresent()));
        audit.record("rotation_displacement", d.getId(), "DISPLACE", null,
                Map.of("applicationId", d.getApplicationId(), "tenderId", d.getTenderId(),
                        "recidivism", d.isRecidivism()));
        events.publish(DomainEvent.of(TenderEvents.ROTATION_DISPLACED, TenderEvents.AGGREGATE, d.getTenderId(),
                Map.of("displacementId", d.getId(), "applicationId", d.getApplicationId(),
                        "tenderId", d.getTenderId(), "recidivism", d.isRecidivism()), clock));
    }

    /** Convites sem resposta no prazo expiram e o rodízio segue (job horário e testes). */
    @Transactional
    public int expireOverdue() {
        Instant now = clock.instant();
        List<TenderInvitation> overdue = invitations.findByStatusAndDueDateBefore(InvitationStatus.INVITED,
                calendar.today());
        for (TenderInvitation inv : overdue) {
            inv.expire(now);
            audit.record("tender_invitation", inv.getId(), "EXPIRE", Map.of("status", "INVITED"),
                    Map.of("status", inv.getStatus().name()));
            invitations.flush();
            registerRefusal(inv);
            tenders.findById(inv.getTenderId()).ifPresent(this::inviteNext);
        }
        return overdue.size();
    }
}
