package org.institutonata.suprihub.supplier.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.validation.DocumentNumbers;
import org.institutonata.suprihub.supplier.domain.ApplicationEvent;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.institutonata.suprihub.supplier.domain.SupplierEvents;
import org.institutonata.suprihub.supplier.infra.ApplicationEventRepository;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.supplier.infra.SupplierDocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Análise de habilitação pela Comissão de Credenciamento (Edital 4.2 a 4.6; Manual, item 3):
 * fila por situação, pedido de ajuste com prazo, habilitação e inabilitação motivada.
 */
@Service
public class ApplicationReviewService {

    private final SupplierApplicationRepository applications;
    private final SupplierDocumentRepository documents;
    private final ApplicationEventRepository history;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;

    public ApplicationReviewService(SupplierApplicationRepository applications, SupplierDocumentRepository documents,
            ApplicationEventRepository history, ParameterService parameters, CalendarService calendar,
            DomainEvents events, AuditService audit, Clock clock) {
        this.applications = applications;
        this.documents = documents;
        this.history = history;
        this.parameters = parameters;
        this.calendar = calendar;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<SupplierApplication> search(ApplicationStatus status, String q, Pageable pageable) {
        String term = q == null ? "" : q.trim();
        String digits = DocumentNumbers.digits(term);
        return applications.search(status, term, digits, pageable);
    }

    /** Quantidade por situação, para os contadores das abas. */
    @Transactional(readOnly = true)
    public Map<ApplicationStatus, Long> counts() {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : applications.countByStatus()) {
            counts.put((ApplicationStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public SupplierApplication get(long id) {
        return applications.findById(id).orElseThrow(() -> new NotFoundException("Requerimento"));
    }

    @Transactional(readOnly = true)
    public SupplierDocument document(long applicationId, long documentId) {
        return documents.findByIdAndApplicationId(documentId, applicationId)
                .orElseThrow(() -> new NotFoundException("Documento"));
    }

    @Transactional
    public SupplierApplication requestAdjustment(long id, long reviewerId, String message, long version) {
        SupplierApplication app = underAnalysis(id, version);
        Instant now = clock.instant();
        LocalDate due = calendar.calendar().addBusinessDays(calendar.today(),
                parameters.integer(ParameterKey.DILIGENCE_BUSINESS_DAYS));
        app.requestAdjustment(message, due, now);
        history.save(new ApplicationEvent(app.getId(), app.getStatus(), app.getAdjustmentMessage(), reviewerId, now));
        audit.record(SupplierEvents.AGGREGATE, id, "REQUEST_ADJUSTMENT", Map.of("status", "SUBMITTED"),
                Map.of("status", app.getStatus().name(), "adjustmentDueDate", due.toString()));
        publish(SupplierEvents.ADJUSTMENT_REQUESTED, app);
        return app;
    }

    @Transactional
    public SupplierApplication approve(long id, long reviewerId, String notes, long version) {
        SupplierApplication app = underAnalysis(id, version);
        Instant now = clock.instant();
        LocalDate validUntil = calendar.today()
                .plusMonths(parameters.integer(ParameterKey.ACCREDITATION_VALIDITY_MONTHS));
        app.approve(reviewerId, notes, validUntil, now);
        history.save(new ApplicationEvent(app.getId(), app.getStatus(), app.getDecisionReason(), reviewerId, now));
        audit.record(SupplierEvents.AGGREGATE, id, "APPROVE", Map.of("status", "SUBMITTED"),
                Map.of("status", app.getStatus().name(), "validUntil", validUntil.toString()));
        publish(SupplierEvents.APPLICATION_APPROVED, app);
        return app;
    }

    @Transactional
    public SupplierApplication reject(long id, long reviewerId, String reason, long version) {
        SupplierApplication app = underAnalysis(id, version);
        Instant now = clock.instant();
        app.reject(reviewerId, reason, now);
        history.save(new ApplicationEvent(app.getId(), app.getStatus(), app.getDecisionReason(), reviewerId, now));
        audit.record(SupplierEvents.AGGREGATE, id, "REJECT", Map.of("status", "SUBMITTED"),
                Map.of("status", app.getStatus().name()), app.getDecisionReason());
        publish(SupplierEvents.APPLICATION_REJECTED, app);
        return app;
    }

    private SupplierApplication underAnalysis(long id, long version) {
        SupplierApplication app = get(id);
        if (app.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Este requerimento foi alterado por outra pessoa. Recarregue a página.", HttpStatus.CONFLICT);
        }
        if (app.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new BusinessException("APPLICATION_NOT_UNDER_ANALYSIS",
                    "Este requerimento não está em análise (situação: " + app.getStatus().label() + ").",
                    HttpStatus.CONFLICT);
        }
        return app;
    }

    private void publish(String type, SupplierApplication app) {
        events.publish(DomainEvent.of(type, SupplierEvents.AGGREGATE, app.getId(),
                Map.of("applicationId", app.getId(), "userId", app.getUserId()), clock));
    }
}
