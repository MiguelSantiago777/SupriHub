package org.institutonata.suprihub.judgment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.catalog.domain.TerritoryResolver;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Advantage;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Bid;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Entry;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Params;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Result;
import org.institutonata.suprihub.judgment.domain.JudgmentEvents;
import org.institutonata.suprihub.judgment.infra.CoverOfferRepository;
import org.institutonata.suprihub.judgment.infra.DrawRecordRepository;
import org.institutonata.suprihub.judgment.infra.JudgmentEntryRepository;
import org.institutonata.suprihub.judgment.infra.JudgmentRepository;
import org.institutonata.suprihub.judgment.model.CoverOffer;
import org.institutonata.suprihub.judgment.model.DrawRecord;
import org.institutonata.suprihub.judgment.model.Judgment;
import org.institutonata.suprihub.judgment.model.JudgmentEntry;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.CoverStatus;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.Status;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.proposal.domain.Proposal;
import org.institutonata.suprihub.proposal.infra.ProposalRepository;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.storage.FileStorage;
import org.institutonata.suprihub.shared.storage.FileStorage.StoredFile;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierEnums.Race;
import org.institutonata.suprihub.supplier.domain.SupplierType;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.tender.application.TenderCancellationListener;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Julgamento das Faixas 2 e 3 (Manual 4.2, passos 5 e 6). Depois do prazo, a Comissão
 * desclassifica o que for preciso e julga: o {@link JudgmentEngine} monta o mapa; se a
 * primeira colocada não é a de menor preço, ela é convocada a cobrir em 1 dia útil, e assim
 * por diante até a de menor preço. Com o resultado, a Comissão (Faixa 2) ou a Diretoria
 * (Faixa 3) homologa e a ata em PDF é congelada.
 */
@Service
public class JudgmentService implements TenderCancellationListener {

    /** Tudo que a tela do julgamento precisa. {@code judgment} = o vivo, ou o último anulado. */
    public record Snapshot(Judgment judgment, List<JudgmentEntry> entries, List<CoverOffer> covers,
            DrawRecord draw, List<Judgment> history) {

        public Optional<JudgmentEntry> entry(Long proposalId) {
            return entries.stream().filter(e -> e.getProposalId().equals(proposalId)).findFirst();
        }
    }

    /** Situação do fornecedor no julgamento (sem dados dos concorrentes). */
    public record SupplierView(CoverOffer cover, boolean canCover, boolean coverStrictlyLower, Judgment judgment,
            JudgmentEntry myEntry, String publicReason) {
    }

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final JudgmentRepository judgments;
    private final JudgmentEntryRepository entries;
    private final CoverOfferRepository covers;
    private final DrawRecordRepository draws;
    private final TenderRepository tenders;
    private final ProposalRepository proposals;
    private final SupplierApplicationRepository applications;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final AtaPdf ataPdf;
    private final FileStorage storage;
    private final DomainEvents events;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public JudgmentService(JudgmentRepository judgments, JudgmentEntryRepository entries, CoverOfferRepository covers,
            DrawRecordRepository draws, TenderRepository tenders, ProposalRepository proposals,
            SupplierApplicationRepository applications, ParameterService parameters, CalendarService calendar,
            AtaPdf ataPdf, FileStorage storage, DomainEvents events, AuditService audit, ObjectMapper json,
            Clock clock) {
        this.judgments = judgments;
        this.entries = entries;
        this.covers = covers;
        this.draws = draws;
        this.tenders = tenders;
        this.proposals = proposals;
        this.applications = applications;
        this.parameters = parameters;
        this.calendar = calendar;
        this.ataPdf = ataPdf;
        this.storage = storage;
        this.events = events;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public Snapshot snapshot(long tenderId) {
        List<Judgment> all = judgments.findByTenderIdOrderByStartedAtDesc(tenderId);
        if (all.isEmpty()) {
            return new Snapshot(null, List.of(), List.of(), null, List.of());
        }
        Judgment current = all.stream().filter(j -> j.getStatus() != Status.ANNULLED).findFirst().orElse(all.get(0));
        return new Snapshot(current, entries.findByJudgment(current.getId()),
                covers.findByJudgmentIdOrderByPosition(current.getId()),
                draws.findByJudgmentId(current.getId()).orElse(null),
                all.stream().filter(j -> j != current).toList());
    }

    @Transactional(readOnly = true)
    public Judgment get(long judgmentId) {
        return judgments.findById(judgmentId).orElseThrow(() -> new NotFoundException("Julgamento"));
    }

    /** Fila da Diretoria: Faixa 3 com resultado, aguardando homologação. */
    @Transactional(readOnly = true)
    public List<Judgment> awaitingBoard() {
        return judgments.findByStatusInOrderByDecidedAtAsc(List.of(Status.DECIDED)).stream()
                .filter(j -> tender(j.getTenderId()).getBand() == Band.FAIXA_3)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SupplierView> forSupplier(long tenderId, long applicationId) {
        Optional<Judgment> live = judgments.findFirstByTenderIdAndStatusNot(tenderId, Status.ANNULLED);
        if (live.isEmpty()) {
            return Optional.empty();
        }
        Judgment j = live.get();
        CoverOffer cover = covers.findByJudgmentIdAndApplicationId(j.getId(), applicationId).orElse(null);
        boolean homologated = j.getStatus() == Status.HOMOLOGATED;
        JudgmentEntry mine = homologated ? entries.findByJudgment(j.getId()).stream()
                .filter(e -> e.getApplicationId().equals(applicationId)).findFirst().orElse(null) : null;
        String reason = homologated ? publicReason(j) : null;
        return Optional.of(new SupplierView(cover, cover != null && cover.canRespond(calendar.today()),
                j.rules().coverStrictlyLower(), homologated ? j : null, mine, reason));
    }

    // ---------------------------------------------------------- desclassificação

    @Transactional
    public Proposal disqualify(long tenderId, long proposalId, String reason, long userId) {
        Proposal p = editableProposal(tenderId, proposalId);
        p.disqualify(reason, userId, clock.instant());
        audit.record("proposal", proposalId, "DISQUALIFY", null, Map.of("tenderId", tenderId), reason);
        return p;
    }

    @Transactional
    public Proposal requalify(long tenderId, long proposalId) {
        Proposal p = editableProposal(tenderId, proposalId);
        String before = p.getDisqualifiedReason();
        p.requalify();
        audit.record("proposal", proposalId, "REQUALIFY", before == null ? null : Map.of("reason", before), null);
        return p;
    }

    private Proposal editableProposal(long tenderId, long proposalId) {
        Tender t = tender(tenderId);
        requireJudgeable(t);
        if (judgments.findFirstByTenderIdAndStatusNot(tenderId, Status.ANNULLED).isPresent()) {
            throw new BusinessException("JUDGMENT_IN_PROGRESS",
                    "Já há julgamento em curso. Anule-o para mudar a desclassificação.", HttpStatus.CONFLICT);
        }
        Proposal p = proposals.findById(proposalId).orElseThrow(() -> new NotFoundException("Proposta"));
        if (!p.getTenderId().equals(tenderId)) {
            throw new NotFoundException("Proposta");
        }
        return p;
    }

    // ------------------------------------------------------------------ julgar

    /**
     * Julga: desclassifica quem não tem habilitação vigente (D-033), exige justificativa se a
     * Faixa 2 tiver menos propostas válidas que o mínimo, monta o mapa e convoca a cobrir ou
     * define a vencedora.
     */
    @Transactional
    public Snapshot start(long tenderId, String justification, long userId) {
        Tender tender = tender(tenderId);
        requireJudgeable(tender);
        if (judgments.findFirstByTenderIdAndStatusNot(tenderId, Status.ANNULLED).isPresent()) {
            throw new BusinessException("JUDGMENT_IN_PROGRESS", "Esta oportunidade já está em julgamento.",
                    HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        List<Proposal> all = proposals.findByTenderIdOrderByTotalValueAscSubmittedAtAsc(tenderId);
        Map<Long, SupplierApplication> apps = new HashMap<>();
        applications.findAllById(all.stream().map(Proposal::getApplicationId).toList())
                .forEach(a -> apps.put(a.getId(), a));

        Map<Long, String> disqualified = new LinkedHashMap<>();
        List<Proposal> valid = new ArrayList<>();
        for (Proposal p : all) {
            SupplierApplication app = apps.get(p.getApplicationId());
            if (p.isDisqualified()) {
                disqualified.put(p.getId(), p.getDisqualifiedReason());
            } else if (!accredited(app, today)) {
                disqualified.put(p.getId(), "Sem credenciamento vigente na data do julgamento (Edital 2.1; D-033).");
            } else {
                valid.add(p);
            }
        }
        if (valid.isEmpty()) {
            throw new BusinessException("NO_VALID_PROPOSALS",
                    "Não há proposta válida. Cancele a oportunidade ou publique outra.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        int min = minValidRequired(tender);
        if (valid.size() < min && (justification == null || justification.isBlank())) {
            throw new BusinessException("BELOW_MINIMUM_PROPOSALS", "A Faixa 2 pede " + min
                    + " propostas válidas e há " + valid.size()
                    + ". Justifique para julgar assim mesmo, ou cancele e repita a cotação (Edital 7.1).",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    List.of(Map.of("field", "justification", "message", "Escreva a justificativa.")));
        }

        Judgment.Rules rules = new Judgment.Rules(parameters.decimal(ParameterKey.FICTITIOUS_TIE_MARGIN),
                parameters.decimal(ParameterKey.LOCAL_PREFERENCE_MARGIN),
                parameters.bool(ParameterKey.LOCAL_PREFERENCE_FOR_ALL),
                parameters.bool(ParameterKey.COVER_REQUIRES_STRICTLY_LOWER), min);
        Map<Long, JudgmentEntry.Supplier> profiles = new HashMap<>();
        List<Bid> bids = new ArrayList<>();
        for (Proposal p : all) {
            JudgmentEntry.Supplier s = profile(apps.get(p.getApplicationId()), tender);
            profiles.put(p.getId(), s);
            if (!disqualified.containsKey(p.getId())) {
                bids.add(new Bid(p.getId(), p.getTotalValue(), s.smallBusiness(), level(s.territory()), s.womanLed(),
                        s.blackWomanOrCadUnico(), s.mei(), s.rating()));
            }
        }
        long seed = random.nextLong();
        Result result = JudgmentEngine.judge(bids,
                new Params(rules.tieMargin(), rules.localMargin(), rules.localPreferenceForAll()), seed);

        Judgment j = judgments.save(new Judgment(tenderId, result.lowest(), rules, valid.size(),
                valid.size() < min ? justification : null, userId, now));
        Map<Long, Proposal> byId = new HashMap<>();
        all.forEach(p -> byId.put(p.getId(), p));
        for (Entry e : result.entries()) {
            long pid = e.bid().proposalId();
            entries.save(JudgmentEntry.ranked(j.getId(), pid, profiles.get(pid), e.bid().total(), e.position(),
                    e.pctAbove(), e.fictitiousTie(), e.localPreference(), e.finalist(), e.drawn(), e.advantage()));
        }
        disqualified.forEach((pid, reason) -> entries.save(JudgmentEntry.disqualified(j.getId(), pid,
                profiles.get(pid), byId.get(pid).getTotalValue(), reason)));
        if (result.drawUsed()) {
            draws.save(new DrawRecord(j.getId(), seed, toJson(result.drawGroups()), now));
        }
        tender.startJudgment(now);
        entries.flush();
        audit.record(JudgmentEvents.AGGREGATE, j.getId(), "START", null,
                Map.of("tenderId", tenderId, "valid", valid.size(), "disqualified", disqualified.size(),
                        "draw", result.drawUsed()), justification);
        advance(j, tender, 0);
        return snapshot(tenderId);
    }

    /** Fornecedor convocado: oferece valor para cobrir a menor proposta. */
    @Transactional
    public void cover(long tenderId, long applicationId, BigDecimal value) {
        Judgment j = live(tenderId);
        CoverOffer offer = pendingOffer(j, applicationId);
        BigDecimal v = value.setScale(2, RoundingMode.HALF_UP);
        if (!JudgmentEngine.covers(v, offer.getCeiling(), j.rules().coverStrictlyLower())) {
            throw new BusinessException("COVER_TOO_HIGH", j.rules().coverStrictlyLower()
                    ? "O valor precisa ser menor que " + money(offer.getCeiling()) + "."
                    : "O valor precisa ser igual ou menor que " + money(offer.getCeiling()) + ".",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    List.of(Map.of("field", "value", "message", "Valor acima do permitido.")));
        }
        Instant now = clock.instant();
        offer.cover(v, now);
        audit.record("cover_offer", offer.getId(), "COVER", Map.of("status", "OFFERED"),
                Map.of("status", offer.getStatus().name(), "value", v));
        JudgmentEntry entry = entryOf(j, offer.getProposalId());
        decide(j, tender(tenderId), entry, v, now);
    }

    /** Fornecedor convocado não quer cobrir: passa à próxima finalista. */
    @Transactional
    public void declineCover(long tenderId, long applicationId) {
        Judgment j = live(tenderId);
        CoverOffer offer = pendingOffer(j, applicationId);
        offer.decline(clock.instant());
        covers.flush();
        audit.record("cover_offer", offer.getId(), "DECLINE", Map.of("status", "OFFERED"),
                Map.of("status", offer.getStatus().name()));
        advance(j, tender(tenderId), offer.getPosition());
    }

    /** Job: convocações cujo dia útil terminou viram EXPIRED e a vez passa adiante. */
    @Transactional
    public int expireOverdue() {
        List<CoverOffer> overdue = covers.findByStatusAndDueDateBefore(CoverStatus.OFFERED, calendar.today());
        for (CoverOffer offer : overdue) {
            offer.expire(clock.instant());
            covers.flush();
            audit.record("cover_offer", offer.getId(), "EXPIRE", Map.of("status", "OFFERED"),
                    Map.of("status", offer.getStatus().name()));
            Judgment j = get(offer.getJudgmentId());
            advance(j, tender(j.getTenderId()), offer.getPosition());
        }
        return overdue.size();
    }

    /**
     * Próxima finalista depois de {@code afterPosition}: se é a de menor preço, vence com o
     * próprio valor; se não, é convocada a cobrir.
     */
    private void advance(Judgment j, Tender tender, int afterPosition) {
        JudgmentEntry next = entries.findByJudgment(j.getId()).stream()
                .filter(e -> e.isFinalist() && e.getPosition() > afterPosition)
                .findFirst().orElseThrow(() -> new IllegalStateException("Mapa sem a proposta de menor preço"));
        Instant now = clock.instant();
        if (!next.mustCover()) {
            decide(j, tender, next, next.getTotalValue(), now);
            return;
        }
        int days = parameters.integer(ParameterKey.COVER_DEADLINE_BUSINESS_DAYS);
        LocalDate due = calendar.calendar().addBusinessDays(calendar.today(), days);
        CoverOffer offer = covers.save(new CoverOffer(j.getId(), next.getProposalId(), next.getApplicationId(),
                next.getPosition(), j.getLowestValue(), due, now));
        audit.record("cover_offer", offer.getId(), "OFFER", null,
                Map.of("judgmentId", j.getId(), "position", next.getPosition(), "dueDate", due.toString()));
        events.publish(DomainEvent.of(JudgmentEvents.COVER_OFFERED, JudgmentEvents.AGGREGATE, j.getId(),
                Map.of("judgmentId", j.getId(), "coverOfferId", offer.getId(), "tenderId", tender.getId()), clock));
    }

    private void decide(Judgment j, Tender tender, JudgmentEntry winner, BigDecimal value, Instant now) {
        String reason = winnerReason(j, winner, value);
        j.decide(winner.getProposalId(), value, reason, now);
        judgments.flush();
        audit.record(JudgmentEvents.AGGREGATE, j.getId(), "DECIDE", Map.of("status", "AWAITING_COVER"),
                Map.of("status", j.getStatus().name(), "proposalId", winner.getProposalId(), "value", value));
        events.publish(DomainEvent.of(JudgmentEvents.DECIDED, JudgmentEvents.AGGREGATE, j.getId(),
                Map.of("judgmentId", j.getId(), "tenderId", tender.getId()), clock));
    }

    // ------------------------------------------------------- homologar / anular

    /** Faixa 2: a Comissão homologa. Faixa 3: a Diretoria ({@code byBoard}). */
    @Transactional
    public Judgment homologate(long judgmentId, String note, long userId, boolean byBoard, long version) {
        Judgment j = current(judgmentId, version);
        Tender tender = tender(j.getTenderId());
        if (j.getStatus() != Status.DECIDED) {
            throw new BusinessException("JUDGMENT_NOT_DECIDED", "O julgamento ainda não tem resultado.",
                    HttpStatus.CONFLICT);
        }
        boolean boardBand = tender.getBand() == Band.FAIXA_3;
        if (boardBand != byBoard) {
            throw new BusinessException("WRONG_APPROVER", boardBand
                    ? "Na Faixa 3 quem aprova a ata é a Diretoria."
                    : "Na Faixa 2 quem homologa é a Comissão.", HttpStatus.FORBIDDEN);
        }
        Instant now = clock.instant();
        byte[] pdf = ataPdf.render(tender, j, entries.findByJudgment(j.getId()),
                covers.findByJudgmentIdOrderByPosition(j.getId()), draws.findByJudgmentId(j.getId()).orElse(null),
                new AtaPdf.Approval(userId, now, note, byBoard));
        StoredFile stored = storage.store("ata-" + tender.getId(), "ata-" + tender.getNumber() + ".pdf",
                new java.io.ByteArrayInputStream(pdf));
        j.homologate(userId, note, stored.storageKey(), stored.sha256(), now);
        tender.award(now);
        judgments.flush();
        audit.record(JudgmentEvents.AGGREGATE, j.getId(), "HOMOLOGATE", Map.of("status", "DECIDED"),
                Map.of("status", j.getStatus().name(), "ataSha256", stored.sha256(), "byBoard", byBoard), note);
        events.publish(DomainEvent.of(JudgmentEvents.HOMOLOGATED, JudgmentEvents.AGGREGATE, j.getId(),
                Map.of("judgmentId", j.getId(), "tenderId", tender.getId()), clock));
        return j;
    }

    /**
     * Anula o julgamento (erro, recurso acolhido, ou a Diretoria devolve a ata da Faixa 3).
     * As propostas voltam a ficar disponíveis para um novo julgamento.
     */
    @Transactional
    public Judgment annul(long judgmentId, String reason, long userId, boolean byBoard, long version) {
        Judgment j = current(judgmentId, version);
        if (!j.getStatus().isOpen()) {
            throw new BusinessException("JUDGMENT_CLOSED", "Este julgamento não pode mais ser anulado.",
                    HttpStatus.CONFLICT);
        }
        if (byBoard && (j.getStatus() != Status.DECIDED || tender(j.getTenderId()).getBand() != Band.FAIXA_3)) {
            throw new BusinessException("WRONG_APPROVER", "A Diretoria só devolve atas da Faixa 3 com resultado.",
                    HttpStatus.FORBIDDEN);
        }
        annulInternal(j, reason, userId);
        return j;
    }

    private void annulInternal(Judgment j, String reason, Long userId) {
        Instant now = clock.instant();
        covers.findByJudgmentIdAndStatus(j.getId(), CoverStatus.OFFERED).ifPresent(c -> c.cancel(now));
        String before = j.getStatus().name();
        j.annul(userId, reason, now);
        judgments.flush();
        audit.record(JudgmentEvents.AGGREGATE, j.getId(), "ANNUL", Map.of("status", before),
                Map.of("status", j.getStatus().name()), reason);
    }

    /** Oportunidade cancelada durante o julgamento: o julgamento vivo é anulado junto. */
    @Override
    public void onCancelled(Tender tender, String reason) {
        judgments.findFirstByTenderIdAndStatusNot(tender.getId(), Status.ANNULLED)
                .filter(j -> j.getStatus().isOpen())
                .ifPresent(j -> annulInternal(j, "Oportunidade cancelada: " + reason, null));
    }

    // -------------------------------------------------------------------- ata

    /** PDF da ata: o congelado depois da homologação; antes disso, a minuta do momento. */
    @Transactional(readOnly = true)
    public byte[] ata(long judgmentId) {
        Judgment j = get(judgmentId);
        if (j.getAtaKey() != null) {
            try (var in = storage.load(j.getAtaKey())) {
                return in.readAllBytes();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException("Falha ao ler a ata", e);
            }
        }
        return ataPdf.render(tender(j.getTenderId()), j, entries.findByJudgment(j.getId()),
                covers.findByJudgmentIdOrderByPosition(j.getId()), draws.findByJudgmentId(j.getId()).orElse(null),
                null);
    }

    /** Faixa 2: mínimo de propostas válidas (Edital 7.1). Faixa 3: basta uma. */
    public int minValidRequired(Tender t) {
        return t.getBand() == Band.FAIXA_2 ? parameters.integer(ParameterKey.BAND2_MIN_VALID_PROPOSALS) : 1;
    }

    @Transactional(readOnly = true)
    public Tender tender(long tenderId) {
        return tenders.findById(tenderId).orElseThrow(() -> new NotFoundException("Oportunidade"));
    }

    // ------------------------------------------------------------------ apoio

    private void requireJudgeable(Tender t) {
        if (t.getBand() == Band.FAIXA_1) {
            throw new BusinessException("BAND1_NO_JUDGMENT", "A Faixa 1 é por rodízio, sem julgamento de preço.",
                    HttpStatus.CONFLICT);
        }
        boolean open = t.getStatus() == TenderEnums.Status.OPEN || t.getStatus() == TenderEnums.Status.JUDGING;
        if (!open || t.proposalsSealed(clock.instant())) {
            throw new BusinessException("TENDER_NOT_JUDGEABLE",
                    "O julgamento só começa depois do prazo das propostas, com a oportunidade aberta.",
                    HttpStatus.CONFLICT);
        }
    }

    private static boolean accredited(SupplierApplication app, LocalDate today) {
        return app != null && app.getStatus() == ApplicationStatus.APPROVED
                && app.getAccreditationValidUntil() != null && !app.getAccreditationValidUntil().isBefore(today);
    }

    /**
     * Perfil do fornecedor para o julgamento. Todos os tipos aceitos são pequenos negócios
     * (D-031). "Mulher negra ou inscrita no CadÚnico" (Anexo IV; D-016): liderado por mulher e
     * (preta ou parda, ou CadÚnico). Avaliação de desempenho só existe a partir da Fase 3.
     */
    static JudgmentEntry.Supplier profile(SupplierApplication app, Tender tender) {
        Territory territory = app.getMunicipality() == null ? Territory.OUT_OF_STATE
                : TerritoryResolver.resolve(app.getMunicipality().toRef(), tender.getExecutionMunicipality() == null
                        ? null : tender.getExecutionMunicipality().toRef()).territory();
        boolean black = app.getRace() == Race.PRETA || app.getRace() == Race.PARDA;
        return new JudgmentEntry.Supplier(app.getId(), name(app), app.getSupplierType(), territory, true,
                app.isWomanLed(), app.isWomanLed() && (black || app.isCadUnico()),
                app.getSupplierType() == SupplierType.MEI, null);
    }

    static String name(SupplierApplication a) {
        String legal = a.getLegalName() == null ? "Fornecedor #" + a.getId() : a.getLegalName();
        return a.getTradeName() != null ? a.getTradeName() + " (" + legal + ")" : legal;
    }

    private static int level(Territory t) {
        return t == null ? Territory.OUT_OF_STATE.level() : t.level();
    }

    /** Motivo da vitória para a ata (Manual 4.2: "vencedora: B, após exercício da preferência local"). */
    private String winnerReason(Judgment j, JudgmentEntry w, BigDecimal value) {
        StringBuilder sb = new StringBuilder();
        if (w.isLowest()) {
            boolean someoneDeclined = covers.findByJudgmentIdOrderByPosition(j.getId()).stream()
                    .anyMatch(c -> c.getStatus() == CoverStatus.DECLINED || c.getStatus() == CoverStatus.EXPIRED);
            sb.append("Menor preço");
            if (someoneDeclined) {
                sb.append(", depois que as finalistas convocadas não exerceram o direito de cobrir");
            }
            sb.append('.');
        } else {
            sb.append(w.isLocalPreference() ? "Preferência local (item 8.3 do Edital)"
                    : "Empate ficto (item 8.2 do Edital)");
            if (w.getAdvantage() != null && w.getAdvantage() != Advantage.NONE
                    && !(w.isLocalPreference() && w.getAdvantage() == Advantage.LOCAL_PREFERENCE)) {
                sb.append(", com prioridade por ").append(w.getAdvantage().label());
            }
            sb.append("; exerceu o direito de cobrir com ").append(money(value))
                    .append(" (menor proposta: ").append(money(j.getLowestValue())).append(").");
        }
        if (w.isDrawn()) {
            sb.append(" Classificação definida por sorteio entre propostas empatadas (item 7.6 do Edital).");
        }
        return sb.toString();
    }

    /** Motivo para os fornecedores: sem revelar dados de equidade de ninguém (Política art. 28). */
    private String publicReason(Judgment j) {
        JudgmentEntry w = entryOf(j, j.getWinnerProposalId());
        String base;
        if (w.isLowest()) {
            base = "menor preço";
        } else if (w.isLocalPreference() || w.getAdvantage() == Advantage.LOCAL_PREFERENCE) {
            base = "empate ficto com preferência local, após cobrir o menor preço";
        } else if (w.getAdvantage() != null && w.getAdvantage().isEquity()) {
            base = "empate ficto com desempate por equidade, após cobrir o menor preço";
        } else {
            base = "empate ficto, após cobrir o menor preço";
        }
        return w.isDrawn() ? base + " (com sorteio entre empatadas)" : base;
    }

    private JudgmentEntry entryOf(Judgment j, Long proposalId) {
        return entries.findById(new JudgmentEntry.Key(j.getId(), proposalId))
                .orElseThrow(() -> new IllegalStateException("Proposta fora do mapa"));
    }

    private Judgment live(long tenderId) {
        return judgments.findFirstByTenderIdAndStatusNot(tenderId, Status.ANNULLED)
                .orElseThrow(() -> new NotFoundException("Julgamento"));
    }

    private CoverOffer pendingOffer(Judgment j, long applicationId) {
        CoverOffer offer = covers.findByJudgmentIdAndApplicationId(j.getId(), applicationId)
                .orElseThrow(() -> new NotFoundException("Convocação"));
        if (j.getStatus() != Status.AWAITING_COVER || !offer.canRespond(calendar.today())) {
            throw new BusinessException("COVER_CLOSED", "Esta convocação não está mais aberta.", HttpStatus.CONFLICT);
        }
        return offer;
    }

    private Judgment current(long id, long version) {
        Judgment j = get(id);
        if (j.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Este julgamento foi alterado por outra pessoa. Recarregue a página.", HttpStatus.CONFLICT);
        }
        return j;
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String money(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(v);
    }
}
