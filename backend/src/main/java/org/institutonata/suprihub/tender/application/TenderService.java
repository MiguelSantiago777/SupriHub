package org.institutonata.suprihub.tender.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.catalog.domain.Municipality;
import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.catalog.infra.MunicipalityRepository;
import org.institutonata.suprihub.catalog.infra.SupplyLineRepository;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.BandPolicy;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums.InvitationStatus;
import org.institutonata.suprihub.tender.domain.TenderEnums.Status;
import org.institutonata.suprihub.tender.domain.TenderEvents;
import org.institutonata.suprihub.tender.domain.TenderInvitation;
import org.institutonata.suprihub.tender.infra.TenderInvitationRepository;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oportunidades criadas e publicadas pela Comissão (ou ADMIN). A faixa sai do valor
 * estimado (parâmetros vigentes); Faixas 2 e 3 exigem prazo mínimo de propostas em dias
 * úteis; a Faixa 1 entra no rodízio ao ser publicada.
 */
@Service
public class TenderService {

    private final TenderRepository tenders;
    private final TenderInvitationRepository invitations;
    private final SupplyLineRepository lines;
    private final MunicipalityRepository municipalities;
    private final RotationService rotation;
    private final EligibleSuppliers eligibleSuppliers;
    private final SupplierApplicationRepository applications;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;
    private final List<TenderCancellationListener> cancellationListeners;

    public TenderService(TenderRepository tenders, TenderInvitationRepository invitations, SupplyLineRepository lines,
            MunicipalityRepository municipalities, RotationService rotation, EligibleSuppliers eligibleSuppliers,
            SupplierApplicationRepository applications, ParameterService parameters, CalendarService calendar,
            DomainEvents events, AuditService audit, Clock clock,
            List<TenderCancellationListener> cancellationListeners) {
        this.cancellationListeners = cancellationListeners;
        this.tenders = tenders;
        this.invitations = invitations;
        this.lines = lines;
        this.municipalities = municipalities;
        this.rotation = rotation;
        this.eligibleSuppliers = eligibleSuppliers;
        this.applications = applications;
        this.parameters = parameters;
        this.calendar = calendar;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Tender create(long userId, Tender.Data data, String lineCode, Integer municipalityId) {
        Tender tender = new Tender(userId, clock.instant());
        apply(tender, data, lineCode, municipalityId);
        tender = tenders.save(tender);
        audit.record(TenderEvents.AGGREGATE, tender.getId(), "CREATE", null,
                Map.of("band", tender.getBand().name(), "line", lineCode));
        return tender;
    }

    @Transactional
    public Tender update(long id, Tender.Data data, String lineCode, Integer municipalityId, long version) {
        Tender tender = current(id, version);
        if (tender.getStatus() != Status.DRAFT) {
            throw new BusinessException("TENDER_LOCKED", "Oportunidade publicada não pode ser alterada.",
                    HttpStatus.CONFLICT);
        }
        apply(tender, data, lineCode, municipalityId);
        return tender;
    }

    private void apply(Tender tender, Tender.Data data, String lineCode, Integer municipalityId) {
        SupplyLine line = lines.findByCode(lineCode).filter(SupplyLine::isActive)
                .orElseThrow(() -> new BusinessException("SUPPLY_LINE_NOT_FOUND", "Linha de fornecimento inválida."));
        Municipality municipality = municipalityId == null ? null : municipalities.findById(municipalityId)
                .orElseThrow(() -> new BusinessException("MUNICIPALITY_NOT_FOUND", "Município não encontrado."));
        Band band = new BandPolicy(parameters.decimal(ParameterKey.BAND1_UPPER_LIMIT),
                parameters.decimal(ParameterKey.BAND2_UPPER_LIMIT)).classify(data.estimatedValue());
        tender.update(data, line, municipality, band, clock.instant());
    }

    /** Publica: valida o conteúdo e o prazo, numera, avisa (Faixas 2/3) ou abre o rodízio (Faixa 1). */
    @Transactional
    public Tender publish(long id, long version) {
        Tender tender = current(id, version);
        if (tender.getStatus() != Status.DRAFT) {
            throw new BusinessException("TENDER_ALREADY_PUBLISHED", "Esta oportunidade já foi publicada.",
                    HttpStatus.CONFLICT);
        }
        List<Map<String, String>> issues = new ArrayList<>();
        if (tender.getItems().isEmpty()) {
            issues.add(Map.of("field", "items", "message", "Inclua pelo menos um item."));
        }
        if (tender.getTerritories().isEmpty()) {
            issues.add(Map.of("field", "territories", "message", "Escolha pelo menos um território."));
        }
        if (tender.getBand() != Band.FAIXA_1) {
            int minDays = parameters.integer(tender.getBand() == Band.FAIXA_2
                    ? ParameterKey.BAND2_MIN_PROPOSAL_BUSINESS_DAYS : ParameterKey.BAND3_MIN_PROPOSAL_BUSINESS_DAYS);
            LocalDate earliest = calendar.calendar().addBusinessDays(calendar.today(), minDays);
            if (tender.getProposalDeadline() == null) {
                issues.add(Map.of("field", "proposalDeadline", "message", "Informe o prazo para propostas."));
            } else if (LocalDate.ofInstant(tender.getProposalDeadline(), clock.getZone()).isBefore(earliest)) {
                issues.add(Map.of("field", "proposalDeadline", "message", "Na " + tender.getBand().label()
                        + " o prazo mínimo é de " + minDays + " dias úteis: a partir de " + br(earliest) + "."));
            }
        }
        if (!issues.isEmpty()) {
            throw new BusinessException("TENDER_INCOMPLETE", "Corrija os itens abaixo para publicar.",
                    HttpStatus.UNPROCESSABLE_ENTITY, issues);
        }
        Instant now = clock.instant();
        tender.publish("OP-%d-%04d".formatted(calendar.today().getYear(), tenders.nextNumber()), now);
        tenders.flush();
        audit.record(TenderEvents.AGGREGATE, id, "PUBLISH", Map.of("status", "DRAFT"),
                Map.of("status", tender.getStatus().name(), "number", tender.getNumber()));
        if (tender.getBand() == Band.FAIXA_1) {
            rotation.inviteNext(tender);
        } else {
            events.publish(DomainEvent.of(TenderEvents.PUBLISHED, TenderEvents.AGGREGATE, id,
                    Map.of("tenderId", id), clock));
        }
        return tender;
    }

    @Transactional
    public Tender cancel(long id, String reason, long version) {
        Tender tender = current(id, version);
        if (tender.getStatus() == Status.CANCELLED || tender.getStatus() == Status.AWARDED) {
            throw new BusinessException("TENDER_CLOSED", "Esta oportunidade não pode ser cancelada.",
                    HttpStatus.CONFLICT);
        }
        tender.cancel(reason, clock.instant());
        cancellationListeners.forEach(l -> l.onCancelled(tender, reason));
        audit.record(TenderEvents.AGGREGATE, id, "CANCEL", null, Map.of("status", "CANCELLED"), reason);
        return tender;
    }

    /** Rodízio sem credenciados: amplia o território (T2/T3 — Manual 4.3) e convida o próximo. */
    @Transactional
    public Tender expandTerritories(long id, Set<Territory> territories, long version) {
        Tender tender = current(id, version);
        if (tender.getStatus() != Status.OPEN) {
            throw new BusinessException("TENDER_NOT_OPEN", "Só oportunidade aberta pode ampliar território.",
                    HttpStatus.CONFLICT);
        }
        tender.expandTerritories(territories, clock.instant());
        tenders.flush();
        audit.record(TenderEvents.AGGREGATE, id, "EXPAND_TERRITORIES", null,
                Map.of("territories", tender.getTerritories()));
        rotation.inviteNext(tender);
        return tender;
    }

    /** Rodízio: a Comissão conferiu o preço aceito e confirma a seleção. */
    @Transactional
    public Tender confirmSelection(long invitationId) {
        TenderInvitation inv = invitation(invitationId, InvitationStatus.ACCEPTED);
        Tender tender = get(inv.getTenderId());
        Instant now = clock.instant();
        inv.confirm(now);
        tender.award(now);
        audit.record("tender_invitation", invitationId, "CONFIRM", Map.of("status", "ACCEPTED"),
                Map.of("status", inv.getStatus().name()));
        return tender;
    }

    /** Rodízio: preço acima do aceitável — recusa e passa ao próximo da fila. */
    @Transactional
    public Tender rejectPrice(long invitationId, String reason) {
        TenderInvitation inv = invitation(invitationId, InvitationStatus.ACCEPTED);
        inv.rejectPrice(reason, clock.instant());
        invitations.flush();
        audit.record("tender_invitation", invitationId, "REJECT_PRICE", Map.of("status", "ACCEPTED"),
                Map.of("status", inv.getStatus().name()), reason);
        Tender tender = get(inv.getTenderId());
        rotation.inviteNext(tender);
        return tender;
    }

    @Transactional(readOnly = true)
    public Page<Tender> search(List<Status> statuses, Pageable pageable) {
        return tenders.findByStatusIn(statuses, pageable);
    }

    @Transactional(readOnly = true)
    public Map<Status, Long> counts() {
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : tenders.countByStatus()) {
            counts.put((Status) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Tender get(long id) {
        return tenders.findById(id).orElseThrow(() -> new NotFoundException("Oportunidade"));
    }

    @Transactional(readOnly = true)
    public List<TenderInvitation> invitations(long tenderId) {
        return invitations.findByTenderIdOrderByInvitedAt(tenderId);
    }

    /** Requerimentos por id (nomes nos convites e no mapa de propostas). */
    @Transactional(readOnly = true)
    public Map<Long, SupplierApplication> suppliers(Collection<Long> ids) {
        Map<Long, SupplierApplication> map = new HashMap<>();
        applications.findAllById(ids).forEach(a -> map.put(a.getId(), a));
        return map;
    }

    /** Quantos fornecedores podem ver/receber a oportunidade hoje. */
    @Transactional(readOnly = true)
    public int eligibleCount(Tender tender) {
        return eligibleSuppliers.of(tender, calendar.today()).size();
    }

    public LocalDate today() {
        return calendar.today();
    }

    /** Limites vigentes das faixas (para a tela mostrar a faixa ao digitar o valor). */
    public BandPolicy bandPolicy() {
        return new BandPolicy(parameters.decimal(ParameterKey.BAND1_UPPER_LIMIT),
                parameters.decimal(ParameterKey.BAND2_UPPER_LIMIT));
    }

    private TenderInvitation invitation(long id, InvitationStatus expected) {
        TenderInvitation inv = invitations.findById(id).orElseThrow(() -> new NotFoundException("Convite"));
        if (inv.getStatus() != expected) {
            throw new BusinessException("INVITATION_WRONG_STATUS",
                    "Este convite não está aguardando conferência (situação: " + inv.getStatus().label() + ").",
                    HttpStatus.CONFLICT);
        }
        return inv;
    }

    private Tender current(long id, long version) {
        Tender tender = get(id);
        if (tender.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Esta oportunidade foi alterada por outra pessoa. Recarregue a página.", HttpStatus.CONFLICT);
        }
        return tender;
    }

    private static String br(LocalDate d) {
        return "%02d/%02d/%d".formatted(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
