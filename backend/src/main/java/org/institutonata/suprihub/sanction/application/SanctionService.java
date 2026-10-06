package org.institutonata.suprihub.sanction.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.contract.infra.ServiceOrderRepository;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.BoardOutcome;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.FileKind;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Infraction;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Sanction;
import org.institutonata.suprihub.sanction.domain.SanctionEnums.Status;
import org.institutonata.suprihub.sanction.domain.SanctionEvents;
import org.institutonata.suprihub.sanction.domain.SanctionFile;
import org.institutonata.suprihub.sanction.domain.SanctionProcess;
import org.institutonata.suprihub.sanction.domain.SanctionRules;
import org.institutonata.suprihub.sanction.domain.SanctionRules.Decision;
import org.institutonata.suprihub.sanction.infra.SanctionFileRepository;
import org.institutonata.suprihub.sanction.infra.SanctionProcessRepository;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.storage.FileScanner;
import org.institutonata.suprihub.shared.storage.FileStorage;
import org.institutonata.suprihub.shared.storage.FileStorage.StoredFile;
import org.institutonata.suprihub.shared.storage.UploadedFiles;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Processo sancionador (Edital 11; Anexo XII; Política art. 24 a 26). A Comissão instaura e
 * decide; o fornecedor se defende e pode recorrer; a Diretoria decide o recurso. Suspensão e
 * descredenciamento bloqueiam o fornecedor ({@link SupplierApplication#block}); a inativação
 * cautelar vale enquanto o processo corre.
 */
@Service
public class SanctionService {

    /** Bloqueio cautelar "até o fim do processo". */
    static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    /** Processo com o histórico do fornecedor (reincidência). */
    public record View(SanctionProcess process, SupplierApplication supplier, ServiceOrder order,
            List<SanctionFile> files, List<SanctionProcess> previous, boolean recidivist) {
    }

    private final SanctionProcessRepository processes;
    private final SanctionFileRepository files;
    private final SupplierApplicationRepository applications;
    private final ServiceOrderRepository orders;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final FileStorage storage;
    private final FileScanner scanner;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;

    public SanctionService(SanctionProcessRepository processes, SanctionFileRepository files,
            SupplierApplicationRepository applications, ServiceOrderRepository orders, ParameterService parameters,
            CalendarService calendar, FileStorage storage, FileScanner scanner, DomainEvents events,
            AuditService audit, Clock clock) {
        this.processes = processes;
        this.files = files;
        this.applications = applications;
        this.orders = orders;
        this.parameters = parameters;
        this.calendar = calendar;
        this.storage = storage;
        this.scanner = scanner;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public Page<SanctionProcess> search(List<Status> statuses, Pageable pageable) {
        return processes.findByStatusIn(statuses, pageable);
    }

    @Transactional(readOnly = true)
    public Map<Status, Long> counts() {
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : processes.countByStatus()) {
            counts.put((Status) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public View view(long id) {
        SanctionProcess p = process(id);
        List<SanctionProcess> previous = processes.findByApplicationIdOrderByOpenedAtDesc(p.getApplicationId())
                .stream().filter(x -> !x.getId().equals(p.getId())).toList();
        List<LocalDate> sanctionedOn = previous.stream()
                .filter(x -> x.getStatus() == Status.FINAL && x.getFinalSanction() != null
                        && x.getFinalSanction() != Sanction.NONE)
                .map(x -> LocalDate.ofInstant(x.getFinalAt(), clock.getZone())).toList();
        boolean recidivist = SanctionRules.recidivist(sanctionedOn, calendar.today(),
                parameters.integer(ParameterKey.SANCTION_RECIDIVISM_YEARS));
        return new View(p, applications.findById(p.getApplicationId()).orElse(null),
                p.getServiceOrderId() == null ? null : orders.findById(p.getServiceOrderId()).orElse(null),
                files.findByProcessIdOrderByUploadedAt(id), previous, recidivist);
    }

    @Transactional(readOnly = true)
    public List<SanctionProcess> forSupplier(long applicationId) {
        return processes.findByApplicationIdOrderByOpenedAtDesc(applicationId);
    }

    @Transactional(readOnly = true)
    public View viewForSupplier(long applicationId, long id) {
        View v = view(id);
        if (!v.process().getApplicationId().equals(applicationId)) {
            throw new NotFoundException("Processo");
        }
        return v;
    }

    // ---------------------------------------------------------------- instaurar

    @Transactional
    public SanctionProcess open(long applicationId, Long orderId, Infraction infraction, String facts,
            boolean interimBlock, long userId) {
        SupplierApplication app = applications.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Fornecedor"));
        if (app.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessException("NOT_ACCREDITED", "O processo sancionador é contra credenciados.",
                    HttpStatus.CONFLICT);
        }
        if (orderId != null) {
            ServiceOrder o = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Ordem"));
            if (!o.getApplicationId().equals(applicationId)) {
                throw new BusinessException("ORDER_MISMATCH", "A ordem não é deste fornecedor.");
            }
        }
        if (infraction == null || facts == null || facts.isBlank()) {
            throw new BusinessException("SANCTION_INVALID", "Escolha a infração e descreva os fatos.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    List.of(Map.of("field", "facts", "message", "Descreva os fatos (notificação escrita).")));
        }
        Instant now = clock.instant();
        LocalDate due = addDays(ParameterKey.SANCTION_DEFENSE_BUSINESS_DAYS);
        String number = "PS-%d-%04d".formatted(calendar.today().getYear(), processes.nextNumber());
        SanctionProcess p = processes.save(new SanctionProcess(number, applicationId, orderId, infraction, facts,
                interimBlock, userId, due, now));
        if (interimBlock) {
            app.block(OPEN_ENDED, "Inativação cautelar — " + number, now);
        }
        audit.record(SanctionEvents.AGGREGATE, p.getId(), "OPEN", null,
                Map.of("applicationId", applicationId, "infraction", infraction.name(), "interim", interimBlock));
        events.publish(DomainEvent.of(SanctionEvents.OPENED, SanctionEvents.AGGREGATE, p.getId(),
                Map.of("processId", p.getId()), clock));
        return p;
    }

    @Transactional
    public SanctionFile addEvidence(long id, MultipartFile file, long userId) {
        SanctionProcess p = process(id);
        if (p.getStatus() == Status.FINAL) {
            throw new BusinessException("PROCESS_CLOSED", "Processo encerrado.", HttpStatus.CONFLICT);
        }
        return store(p, FileKind.EVIDENCE, file, userId);
    }

    // ----------------------------------------------------------------- defesa

    @Transactional
    public SanctionProcess defend(long applicationId, long id, String text, MultipartFile file, long userId) {
        SanctionProcess p = viewForSupplier(applicationId, id).process();
        if (!p.canDefend(calendar.today())) {
            throw new BusinessException("DEFENSE_CLOSED", "O prazo de defesa terminou.", HttpStatus.CONFLICT);
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException("DEFENSE_INVALID", "Escreva a sua defesa.", HttpStatus.UNPROCESSABLE_ENTITY,
                    List.of(Map.of("field", "text", "message", "Escreva a sua defesa.")));
        }
        if (file != null && !file.isEmpty()) {
            store(p, FileKind.DEFENSE, file, userId);
        }
        p.defend(text, addDays(ParameterKey.SANCTION_DECISION_BUSINESS_DAYS), clock.instant());
        audit.record(SanctionEvents.AGGREGATE, id, "DEFENSE", Map.of("status", "DEFENSE"),
                Map.of("status", p.getStatus().name()));
        events.publish(DomainEvent.of(SanctionEvents.DEFENDED, SanctionEvents.AGGREGATE, id,
                Map.of("processId", id), clock));
        return p;
    }

    // ---------------------------------------------------------------- decisão

    /** Decisão motivada da Comissão (art. 26). Depois do prazo de defesa, com ou sem defesa. */
    @Transactional
    public SanctionProcess decide(long id, Decision d, String reason, long userId, long version) {
        SanctionProcess p = current(id, version);
        if (p.getStatus() == Status.DEFENSE) {
            if (p.canDefend(calendar.today())) {
                throw new BusinessException("DEFENSE_PENDING",
                        "Aguarde a defesa ou o fim do prazo (" + br(p.getDefenseDue()) + ").", HttpStatus.CONFLICT);
            }
            p.closeDefense(addDays(ParameterKey.SANCTION_DECISION_BUSINESS_DAYS));
        }
        if (p.getStatus() != Status.AWAITING_DECISION) {
            throw new BusinessException("PROCESS_DECIDED", "Este processo já foi decidido.", HttpStatus.CONFLICT);
        }
        validate(p, d, reason);
        p.decide(d, reason, userId, addDays(ParameterKey.SANCTION_APPEAL_BUSINESS_DAYS), clock.instant());
        audit.record(SanctionEvents.AGGREGATE, id, "DECIDE", Map.of("status", "AWAITING_DECISION"),
                Map.of("status", p.getStatus().name(), "sanction", d.sanction().name()), reason);
        events.publish(DomainEvent.of(SanctionEvents.DECIDED, SanctionEvents.AGGREGATE, id,
                Map.of("processId", id), clock));
        return p;
    }

    private void validate(SanctionProcess p, Decision d, String reason) {
        BigDecimal orderPenalty = p.getServiceOrderId() == null ? null
                : orders.findById(p.getServiceOrderId()).map(ServiceOrder::getPenaltyPct).orElse(null);
        List<Map<String, String>> issues = new ArrayList<>();
        for (SanctionRules.Issue i : SanctionRules.check(d, orderPenalty)) {
            issues.add(Map.of("field", i.field(), "message", i.message()));
        }
        if (reason == null || reason.isBlank()) {
            issues.add(Map.of("field", "reason", "message", "A decisão precisa ser motivada."));
        }
        if (!issues.isEmpty()) {
            throw new BusinessException("DECISION_INVALID", "Corrija a decisão.", HttpStatus.UNPROCESSABLE_ENTITY,
                    issues);
        }
    }

    // ----------------------------------------------------------------- recurso

    @Transactional
    public SanctionProcess appeal(long applicationId, long id, String text, MultipartFile file, long userId) {
        SanctionProcess p = viewForSupplier(applicationId, id).process();
        if (!p.canAppeal(calendar.today())) {
            throw new BusinessException("APPEAL_CLOSED", "O prazo de recurso terminou.", HttpStatus.CONFLICT);
        }
        if (p.getSanction() == Sanction.NONE) {
            throw new BusinessException("NOTHING_TO_APPEAL", "O processo foi arquivado sem sanção.",
                    HttpStatus.CONFLICT);
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException("APPEAL_INVALID", "Escreva as razões do recurso.",
                    HttpStatus.UNPROCESSABLE_ENTITY, List.of(Map.of("field", "text", "message", "Escreva as razões.")));
        }
        if (file != null && !file.isEmpty()) {
            store(p, FileKind.APPEAL, file, userId);
        }
        p.appeal(text, addDays(ParameterKey.SANCTION_BOARD_DECISION_BUSINESS_DAYS), clock.instant());
        audit.record(SanctionEvents.AGGREGATE, id, "APPEAL", Map.of("status", "APPEAL_WINDOW"),
                Map.of("status", p.getStatus().name()));
        events.publish(DomainEvent.of(SanctionEvents.APPEALED, SanctionEvents.AGGREGATE, id,
                Map.of("processId", id), clock));
        return p;
    }

    /** Decisão final da Diretoria sobre o recurso. */
    @Transactional
    public SanctionProcess boardDecide(long id, BoardOutcome outcome, Decision reformed, String reason, long userId,
            long version) {
        SanctionProcess p = current(id, version);
        if (p.getStatus() != Status.BOARD_REVIEW) {
            throw new BusinessException("NO_APPEAL", "Não há recurso pendente neste processo.", HttpStatus.CONFLICT);
        }
        if (outcome == null || reason == null || reason.isBlank()) {
            throw new BusinessException("DECISION_INVALID", "Escolha a decisão e escreva a motivação.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    List.of(Map.of("field", "reason", "message", "A decisão precisa ser motivada.")));
        }
        Decision finalDecision = switch (outcome) {
            case UPHELD -> p.commissionDecision();
            case ANNULLED -> new Decision(Sanction.NONE, null, null, null);
            case REFORMED -> {
                validate(p, reformed, reason);
                yield reformed;
            }
        };
        p.boardDecide(outcome, reformed, reason, userId, clock.instant());
        audit.record(SanctionEvents.AGGREGATE, id, "BOARD_DECIDE", Map.of("status", "BOARD_REVIEW"),
                Map.of("outcome", outcome.name(), "sanction", finalDecision.sanction().name()), reason);
        finish(p, finalDecision);
        return p;
    }

    // ------------------------------------------------------------------ prazos

    /** Job: defesa vencida → decisão; recurso não apresentado → decisão da Comissão vale. */
    @Transactional
    public int advanceDeadlines() {
        LocalDate today = calendar.today();
        int n = 0;
        for (SanctionProcess p : processes.findByStatusAndDefenseDueBefore(Status.DEFENSE, today)) {
            p.closeDefense(addDays(ParameterKey.SANCTION_DECISION_BUSINESS_DAYS));
            audit.record(SanctionEvents.AGGREGATE, p.getId(), "DEFENSE_EXPIRED", Map.of("status", "DEFENSE"),
                    Map.of("status", p.getStatus().name()));
            n++;
        }
        for (SanctionProcess p : processes.findByStatusAndAppealDueBefore(Status.APPEAL_WINDOW, today)) {
            finish(p, p.commissionDecision());
            n++;
        }
        return n;
    }

    /** Aplica a sanção definitiva ao cadastro (ou retira a inativação cautelar). */
    private void finish(SanctionProcess p, Decision d) {
        Instant now = clock.instant();
        LocalDate until = SanctionRules.effectiveUntil(d, calendar.today());
        p.finish(d, until, now);
        SupplierApplication app = applications.findById(p.getApplicationId()).orElseThrow();
        if (p.isInterimBlock() && app.getBlockReason() != null && app.getBlockReason().endsWith(p.getNumber())) {
            app.unblock(now);
        }
        if (d.sanction().blocks()) {
            app.block(until, d.sanction().label() + " — " + p.getNumber(), now);
        }
        audit.record(SanctionEvents.AGGREGATE, p.getId(), "FINAL", null,
                Map.of("sanction", d.sanction().name(), "until", until == null ? "" : until.toString()));
        audit.record("supplier_application", p.getApplicationId(), "SANCTION", null,
                Map.of("process", p.getNumber(), "sanction", d.sanction().name(),
                        "until", until == null ? "" : until.toString()));
        events.publish(DomainEvent.of(SanctionEvents.FINAL, SanctionEvents.AGGREGATE, p.getId(),
                Map.of("processId", p.getId()), clock));
    }

    // ------------------------------------------------------------------ apoio

    @Transactional(readOnly = true)
    public SanctionFile file(long processId, long fileId) {
        SanctionFile f = files.findById(fileId).orElseThrow(() -> new NotFoundException("Arquivo"));
        if (!f.getProcessId().equals(processId)) {
            throw new NotFoundException("Arquivo");
        }
        return f;
    }

    public InputStream open(SanctionFile f) {
        return storage.load(f.getFileKey());
    }

    private SanctionFile store(SanctionProcess p, FileKind kind, MultipartFile file, long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Escolha um arquivo.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        long maxBytes = parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "O arquivo é maior que "
                    + parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) + " MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String type = UploadedFiles.detect(file, true);
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store("sanction-" + p.getId(), file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo", e);
        }
        if (scanner.scan(stored.storageKey()) == FileScanner.ScanResult.INFECTED) {
            storage.delete(stored.storageKey());
            throw new BusinessException("FILE_INFECTED", "O arquivo foi recusado pela verificação de segurança.");
        }
        SanctionFile f = files.save(new SanctionFile(p.getId(), kind, UploadedFiles.safeFilename(
                file.getOriginalFilename()), type, stored.sizeBytes(), stored.sha256(), stored.storageKey(), userId,
                clock.instant()));
        audit.record(SanctionEvents.AGGREGATE, p.getId(), "FILE_" + kind.name(), null,
                Map.of("fileId", f.getId(), "sha256", stored.sha256()));
        return f;
    }

    private LocalDate addDays(ParameterKey key) {
        return calendar.calendar().addBusinessDays(calendar.today(), parameters.integer(key));
    }

    private SanctionProcess process(long id) {
        return processes.findById(id).orElseThrow(() -> new NotFoundException("Processo"));
    }

    private SanctionProcess current(long id, long version) {
        SanctionProcess p = process(id);
        if (p.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Este processo foi alterado por outra pessoa. Recarregue a página.", HttpStatus.CONFLICT);
        }
        return p;
    }

    private static String br(LocalDate d) {
        return "%02d/%02d/%d".formatted(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
