package org.institutonata.suprihub.supplier.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.storage.FileScanner;
import org.institutonata.suprihub.shared.storage.FileStorage;
import org.institutonata.suprihub.shared.storage.FileStorage.StoredFile;
import org.institutonata.suprihub.shared.storage.UploadedFiles;
import org.institutonata.suprihub.supplier.domain.Appeal;
import org.institutonata.suprihub.supplier.domain.ApplicationEvent;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierEvents;
import org.institutonata.suprihub.supplier.infra.AppealRepository;
import org.institutonata.suprihub.supplier.infra.ApplicationEventRepository;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recursos do Anexo X. Contra a inabilitação (Edital 5.2) ou contra o resultado do julgamento
 * (D-049): o fornecedor recorre em 3 d.u. da comunicação; a Comissão reconsidera em 3 d.u. ou
 * encaminha, motivadamente, à Diretoria, que decide em 5 d.u. Recurso provido habilita o
 * fornecedor ou, no julgamento, reabre o julgamento ({@link JudgmentAppealListener}).
 */
@Service
public class AppealService {

    /** Filas por etapa. */
    public enum Stage {
        COMMISSION(List.of(Appeal.Status.FILED)),
        BOARD(List.of(Appeal.Status.FORWARDED)),
        DECIDED(List.of(Appeal.Status.GRANTED, Appeal.Status.DENIED));

        private final List<Appeal.Status> statuses;

        Stage(List<Appeal.Status> statuses) {
            this.statuses = statuses;
        }
    }

    private final AppealRepository appeals;
    private final SupplierApplicationRepository applications;
    private final ApplicationEventRepository history;
    private final FileStorage storage;
    private final FileScanner scanner;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;
    private final List<JudgmentAppealListener> judgmentListeners;

    public AppealService(AppealRepository appeals, SupplierApplicationRepository applications,
            ApplicationEventRepository history, FileStorage storage, FileScanner scanner,
            ParameterService parameters, CalendarService calendar, DomainEvents events, AuditService audit,
            Clock clock, List<JudgmentAppealListener> judgmentListeners) {
        this.judgmentListeners = judgmentListeners;
        this.appeals = appeals;
        this.applications = applications;
        this.history = history;
        this.storage = storage;
        this.scanner = scanner;
        this.parameters = parameters;
        this.calendar = calendar;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Appeal file(long userId, String reasons, String request, MultipartFile file) {
        SupplierApplication app = applications.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Requerimento"));
        if (app.getStatus() != ApplicationStatus.REJECTED) {
            throw new BusinessException("APPEAL_NOT_ALLOWED", "Só cabe recurso contra a inabilitação.",
                    HttpStatus.CONFLICT);
        }
        Optional<Appeal> last = latestFor(app.getId());
        if (last.isPresent() && (last.get().getStatus().isOpen()
                || !last.get().getFiledAt().isBefore(app.getDecidedAt()))) {
            throw new BusinessException("APPEAL_ALREADY_FILED", "Já existe recurso contra esta decisão.",
                    HttpStatus.CONFLICT);
        }
        LocalDate today = calendar.today();
        if (!app.canAppeal(today)) {
            throw new BusinessException("APPEAL_DEADLINE_EXPIRED",
                    "O prazo para recorrer terminou. Você ainda pode corrigir e reenviar o requerimento.",
                    HttpStatus.CONFLICT);
        }
        Appeal.Attachment attachment = file == null || file.isEmpty() ? null : store(app.getId(), file);
        Instant now = clock.instant();
        LocalDate due = calendar.calendar().addBusinessDays(today,
                parameters.integer(ParameterKey.APPEAL_RECONSIDERATION_BUSINESS_DAYS));
        Appeal appeal = appeals.save(new Appeal(app.getId(), userId, reasons, request, attachment, due, now));
        history.save(new ApplicationEvent(app.getId(), ApplicationStatus.REJECTED,
                "Recurso apresentado à Comissão.", userId, now));
        audit.record(SupplierEvents.APPEAL_AGGREGATE, appeal.getId(), "FILE", null,
                Map.of("applicationId", app.getId(), "reconsiderationDue", due.toString()));
        publish(SupplierEvents.APPEAL_FILED, appeal, app);
        return appeal;
    }

    /**
     * Recurso contra o resultado do julgamento. Quem pode recorrer e o prazo são conferidos
     * pelo módulo de julgamento antes de chamar aqui.
     */
    @Transactional
    public Appeal fileAgainstJudgment(long userId, SupplierApplication app, long judgmentId, long tenderId,
            String subject, String reasons, String request, MultipartFile file) {
        appeals.findFirstByJudgmentIdAndApplicationIdOrderByFiledAtDesc(judgmentId, app.getId()).ifPresent(a -> {
            throw new BusinessException("APPEAL_ALREADY_FILED", "Você já recorreu deste resultado.",
                    HttpStatus.CONFLICT);
        });
        Appeal.Attachment attachment = file == null || file.isEmpty() ? null : store(app.getId(), file);
        Instant now = clock.instant();
        LocalDate due = calendar.calendar().addBusinessDays(calendar.today(),
                parameters.integer(ParameterKey.APPEAL_RECONSIDERATION_BUSINESS_DAYS));
        Appeal appeal = appeals.save(Appeal.againstJudgment(app.getId(), userId, judgmentId, tenderId, subject,
                reasons, request, attachment, due, now));
        audit.record(SupplierEvents.APPEAL_AGGREGATE, appeal.getId(), "FILE", null,
                Map.of("applicationId", app.getId(), "judgmentId", judgmentId, "kind", "JUDGMENT",
                        "reconsiderationDue", due.toString()));
        publish(SupplierEvents.APPEAL_FILED, appeal, app);
        return appeal;
    }

    @Transactional(readOnly = true)
    public Optional<Appeal> forJudgment(long judgmentId, long applicationId) {
        return appeals.findFirstByJudgmentIdAndApplicationIdOrderByFiledAtDesc(judgmentId, applicationId);
    }

    @Transactional(readOnly = true)
    public Optional<Appeal> latestFor(long applicationId) {
        return appeals.findFirstByApplicationIdAndKindOrderByFiledAtDesc(applicationId, Appeal.Kind.HABILITATION);
    }

    @Transactional(readOnly = true)
    public boolean hasOpenAppeal(long applicationId) {
        return latestFor(applicationId).map(a -> a.getStatus().isOpen()).orElse(false);
    }

    @Transactional(readOnly = true)
    public Page<Appeal> search(Stage stage, Pageable pageable) {
        return appeals.findByStatusIn(stage.statuses, pageable);
    }

    @Transactional(readOnly = true)
    public Map<Stage, Long> counts() {
        Map<Appeal.Status, Long> byStatus = new EnumMap<>(Appeal.Status.class);
        for (Object[] row : appeals.countByStatus()) {
            byStatus.put((Appeal.Status) row[0], (Long) row[1]);
        }
        Map<Stage, Long> counts = new EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) {
            counts.put(s, s.statuses.stream().mapToLong(st -> byStatus.getOrDefault(st, 0L)).sum());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Appeal get(long id) {
        return appeals.findById(id).orElseThrow(() -> new NotFoundException("Recurso"));
    }

    @Transactional(readOnly = true)
    public SupplierApplication applicationOf(Appeal appeal) {
        return applications.findById(appeal.getApplicationId())
                .orElseThrow(() -> new NotFoundException("Requerimento"));
    }

    /** Comissão reconsidera: recurso provido, fornecedor habilitado. */
    @Transactional
    public Appeal reconsider(long id, long memberId, String reason, long version) {
        Appeal appeal = current(id, version, Appeal.Status.FILED);
        Instant now = clock.instant();
        appeal.reconsider(memberId, reason, now);
        grant(appeal, memberId, "Recurso provido pela Comissão (reconsideração): " + reason.trim(), now);
        audit.record(SupplierEvents.APPEAL_AGGREGATE, id, "RECONSIDER", Map.of("status", "FILED"),
                Map.of("status", appeal.getStatus().name()));
        return appeal;
    }

    /** Comissão mantém a inabilitação e encaminha à Diretoria, com motivação. */
    @Transactional
    public Appeal forward(long id, long memberId, String reason, long version) {
        Appeal appeal = current(id, version, Appeal.Status.FILED);
        Instant now = clock.instant();
        LocalDate due = calendar.calendar().addBusinessDays(calendar.today(),
                parameters.integer(ParameterKey.BOARD_APPEAL_DECISION_BUSINESS_DAYS));
        appeal.forward(memberId, reason, due, now);
        if (appeal.getKind() == Appeal.Kind.HABILITATION) {
            history.save(new ApplicationEvent(appeal.getApplicationId(), ApplicationStatus.REJECTED,
                    "Recurso encaminhado à Diretoria: " + appeal.getCommissionReason(), memberId, now));
        }
        audit.record(SupplierEvents.APPEAL_AGGREGATE, id, "FORWARD", Map.of("status", "FILED"),
                Map.of("status", appeal.getStatus().name(), "boardDue", due.toString()));
        publish(SupplierEvents.APPEAL_FORWARDED, appeal, applicationOf(appeal));
        return appeal;
    }

    @Transactional
    public Appeal boardDecision(long id, long boardMemberId, boolean granted, String reason, long version) {
        Appeal appeal = current(id, version, Appeal.Status.FORWARDED);
        Instant now = clock.instant();
        appeal.boardDecision(boardMemberId, granted, reason, now);
        if (granted) {
            grant(appeal, boardMemberId, "Recurso provido pela Diretoria: " + reason.trim(), now);
        } else {
            SupplierApplication app = applicationOf(appeal);
            if (appeal.getKind() == Appeal.Kind.HABILITATION) {
                history.save(new ApplicationEvent(app.getId(), ApplicationStatus.REJECTED,
                        "Recurso não provido pela Diretoria: " + appeal.getBoardReason(), boardMemberId, now));
            }
            publish(SupplierEvents.APPEAL_DENIED, appeal, app);
        }
        audit.record(SupplierEvents.APPEAL_AGGREGATE, id, granted ? "BOARD_GRANT" : "BOARD_DENY",
                Map.of("status", "FORWARDED"), Map.of("status", appeal.getStatus().name()));
        return appeal;
    }

    public InputStream openAttachment(Appeal appeal) {
        if (!appeal.hasAttachment()) {
            throw new NotFoundException("Anexo");
        }
        return storage.load(appeal.attachment().storageKey());
    }

    private void grant(Appeal appeal, long deciderId, String message, Instant now) {
        SupplierApplication app = applicationOf(appeal);
        if (appeal.getKind() == Appeal.Kind.JUDGMENT) {
            // Provido contra o julgamento: o julgamento é reaberto para a Comissão julgar de novo.
            judgmentListeners.forEach(l -> l.onGranted(appeal, deciderId, message));
            publish(SupplierEvents.APPEAL_GRANTED, appeal, app);
            return;
        }
        LocalDate validUntil = calendar.today()
                .plusMonths(parameters.integer(ParameterKey.ACCREDITATION_VALIDITY_MONTHS));
        app.approveOnAppeal(deciderId, message, validUntil, now);
        app.recordRecheck(calendar.today(), deciderId, now);
        history.save(new ApplicationEvent(app.getId(), ApplicationStatus.APPROVED, message, deciderId, now));
        audit.record(SupplierEvents.AGGREGATE, app.getId(), "APPROVE_ON_APPEAL", Map.of("status", "REJECTED"),
                Map.of("status", app.getStatus().name(), "validUntil", validUntil.toString()));
        publish(SupplierEvents.APPEAL_GRANTED, appeal, app);
    }

    private Appeal current(long id, long version, Appeal.Status expected) {
        Appeal appeal = get(id);
        if (appeal.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Este recurso foi alterado por outra pessoa. Recarregue a página.", HttpStatus.CONFLICT);
        }
        if (appeal.getStatus() != expected) {
            throw new BusinessException("APPEAL_WRONG_STAGE",
                    "Este recurso não está nesta etapa (situação: " + appeal.getStatus().label() + ").",
                    HttpStatus.CONFLICT);
        }
        return appeal;
    }

    private Appeal.Attachment store(long applicationId, MultipartFile file) {
        long maxBytes = parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "O anexo é maior que "
                    + parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) + " MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String contentType = UploadedFiles.detect(file, false);
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store("appeal-" + applicationId, file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o anexo", e);
        }
        if (scanner.scan(stored.storageKey()) == FileScanner.ScanResult.INFECTED) {
            storage.delete(stored.storageKey());
            throw new BusinessException("FILE_INFECTED", "O anexo foi recusado pela verificação de segurança.");
        }
        return new Appeal.Attachment(UploadedFiles.safeFilename(file.getOriginalFilename()),
                contentType, stored.sizeBytes(), stored.sha256(), stored.storageKey());
    }

    private void publish(String type, Appeal appeal, SupplierApplication app) {
        events.publish(DomainEvent.of(type, SupplierEvents.APPEAL_AGGREGATE, appeal.getId(),
                Map.of("appealId", appeal.getId(), "applicationId", app.getId(), "userId", app.getUserId()),
                clock));
    }
}
