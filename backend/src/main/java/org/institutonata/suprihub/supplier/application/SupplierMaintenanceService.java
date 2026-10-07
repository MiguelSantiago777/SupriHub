package org.institutonata.suprihub.supplier.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.catalog.domain.Municipality;
import org.institutonata.suprihub.catalog.infra.MunicipalityRepository;
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
import org.institutonata.suprihub.supplier.domain.CertificateRegularity;
import org.institutonata.suprihub.supplier.domain.CertificateRegularity.Result;
import org.institutonata.suprihub.supplier.domain.CertificateRegularity.State;
import org.institutonata.suprihub.supplier.domain.CertificateRegularity.Uploaded;
import org.institutonata.suprihub.supplier.domain.RegistrationData;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.institutonata.suprihub.supplier.domain.SupplierEvents;
import org.institutonata.suprihub.supplier.domain.SupplierType;
import org.institutonata.suprihub.supplier.infra.ApplicationEventRepository;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.supplier.infra.SupplierDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manutenção do cadastro de credenciados (Política art. 21, § 2º, 22 e 23; Manual item 3,
 * passo 8): acompanhamento das certidões, notificação e inativação temporária,
 * reconferência semestral, atualização cadastral e renovação aos 24 meses.
 */
@Service
public class SupplierMaintenanceService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Filas da Comissão na tela de manutenção. */
    public enum View { CERTIFICATES, RECHECK, RENEWAL, INACTIVE }

    /** Retrato da manutenção de um credenciado (para o fornecedor e a equipe interna). */
    public record Maintenance(
            Result certificates,
            List<SupplierDocument> documents,
            LocalDate nextRecheckDue,
            boolean recheckDue,
            LocalDate renewalOpensOn,
            boolean renewalOpen,
            int alertDays) {
    }

    private final SupplierApplicationRepository applications;
    private final SupplierDocumentRepository documents;
    private final ApplicationEventRepository history;
    private final MunicipalityRepository municipalities;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;

    public SupplierMaintenanceService(SupplierApplicationRepository applications, SupplierDocumentRepository documents,
            ApplicationEventRepository history, MunicipalityRepository municipalities, ParameterService parameters,
            CalendarService calendar, DomainEvents events, AuditService audit, Clock clock) {
        this.applications = applications;
        this.documents = documents;
        this.history = history;
        this.municipalities = municipalities;
        this.parameters = parameters;
        this.calendar = calendar;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public Maintenance of(SupplierApplication app) {
        return of(app, documents.findByApplicationIdOrderByUploadedAt(app.getId()), calendar.today());
    }

    private Maintenance of(SupplierApplication app, List<SupplierDocument> docs, LocalDate today) {
        int alertDays = parameters.integer(ParameterKey.CERTIFICATE_ALERT_DAYS);
        Result certs = CertificateRegularity.evaluate(app.getSupplierType(), Uploaded.of(docs), today, alertDays);
        LocalDate nextRecheck = app.getLastRecheckOn() == null ? today
                : app.getLastRecheckOn().plusMonths(parameters.integer(ParameterKey.CERTIFICATE_RECHECK_MONTHS));
        LocalDate renewalOpens = app.getAccreditationValidUntil() == null ? null
                : app.getAccreditationValidUntil().minusDays(parameters.integer(ParameterKey.RENEWAL_NOTICE_DAYS));
        boolean approved = app.getStatus() == ApplicationStatus.APPROVED;
        return new Maintenance(certs, docs, nextRecheck, approved && !nextRecheck.isAfter(today), renewalOpens,
                approved && renewalOpens != null && !today.isBefore(renewalOpens)
                        && app.getRenewalRequestedAt() == null,
                alertDays);
    }

    /** Situação regular para contratar e pagar: credenciamento ativo e certidões em dia. */
    @Transactional(readOnly = true)
    public Result certificatesOf(SupplierApplication app) {
        return of(app).certificates();
    }

    /** Fila da Comissão. Os habilitados são poucos (centenas): filtragem em memória. */
    @Transactional(readOnly = true)
    public List<Map.Entry<SupplierApplication, Maintenance>> list(View view) {
        LocalDate today = calendar.today();
        List<SupplierApplication> approved = applications.findByStatus(ApplicationStatus.APPROVED);
        Map<Long, List<SupplierDocument>> docs = documentsOf(approved.stream().map(SupplierApplication::getId)
                .toList());
        List<Map.Entry<SupplierApplication, Maintenance>> rows = new ArrayList<>();
        for (SupplierApplication app : approved) {
            Maintenance m = of(app, docs.getOrDefault(app.getId(), List.of()), today);
            if (matches(view, app, m, today)) {
                rows.add(Map.entry(app, m));
            }
        }
        rows.sort((a, b) -> compare(view, a, b));
        return rows;
    }

    @Transactional(readOnly = true)
    public Map<View, Long> counts() {
        LocalDate today = calendar.today();
        List<SupplierApplication> approved = applications.findByStatus(ApplicationStatus.APPROVED);
        Map<Long, List<SupplierDocument>> docs = documentsOf(approved.stream().map(SupplierApplication::getId)
                .toList());
        Map<View, Long> counts = new EnumMap<>(View.class);
        for (View v : View.values()) {
            counts.put(v, 0L);
        }
        for (SupplierApplication app : approved) {
            Maintenance m = of(app, docs.getOrDefault(app.getId(), List.of()), today);
            for (View v : View.values()) {
                if (matches(v, app, m, today)) {
                    counts.merge(v, 1L, Long::sum);
                }
            }
        }
        return counts;
    }

    private static boolean matches(View view, SupplierApplication app, Maintenance m, LocalDate today) {
        boolean vigente = app.getAccreditationValidUntil() != null && !app.getAccreditationValidUntil().isBefore(today);
        return switch (view) {
            case CERTIFICATES -> vigente && (app.getIrregularSince() != null
                    || m.certificates().checks().stream().anyMatch(c -> c.state() != State.VALID));
            case RECHECK -> vigente && m.recheckDue();
            case RENEWAL -> app.getRenewalRequestedAt() != null;
            case INACTIVE -> app.isTemporarilyInactive();
        };
    }

    private static int compare(View view, Map.Entry<SupplierApplication, Maintenance> a,
            Map.Entry<SupplierApplication, Maintenance> b) {
        return switch (view) {
            case CERTIFICATES -> nullsLast(a.getValue().certificates().nextExpiry(),
                    b.getValue().certificates().nextExpiry());
            case RECHECK -> nullsLast(a.getValue().nextRecheckDue(), b.getValue().nextRecheckDue());
            case RENEWAL -> a.getKey().getRenewalRequestedAt().compareTo(b.getKey().getRenewalRequestedAt());
            case INACTIVE -> a.getKey().getInactiveSince().compareTo(b.getKey().getInactiveSince());
        };
    }

    private static int nullsLast(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : 1) : -1;
        }
        return a.compareTo(b);
    }

    private Map<Long, List<SupplierDocument>> documentsOf(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return documents.findByApplicationIdIn(ids).stream()
                .collect(Collectors.groupingBy(SupplierDocument::getApplicationId));
    }

    // --------------------------------------------------------- rotina diária

    /**
     * Rotina diária (Política art. 21, § 2º, 22 e 23; D-013 e D-014):
     * <ol>
     * <li>aviso "RENOVAR" ao fornecedor {@code certificate_alert_days} antes do vencimento;</li>
     * <li>certidão vencida: notifica o fornecedor para apresentar em
     * {@code certificate_regularization_business_days} e a Comissão para tentar a consulta direta;</li>
     * <li>prazo vencido sem regularização: inativação temporária;</li>
     * <li>{@code deregistration_after_irregular_days} irregular: alerta à Comissão;</li>
     * <li>aviso de renovação {@code renewal_notice_days} antes do fim do credenciamento.</li>
     * </ol>
     *
     * @return quantidade de credenciados com alguma mudança
     */
    @Transactional
    public int runDaily() {
        LocalDate today = calendar.today();
        Instant now = clock.instant();
        int deregistrationDays = parameters.integer(ParameterKey.DEREGISTRATION_AFTER_IRREGULAR_DAYS);
        List<SupplierApplication> approved = applications.findByStatus(ApplicationStatus.APPROVED);
        Map<Long, List<SupplierDocument>> docs = documentsOf(approved.stream().map(SupplierApplication::getId)
                .toList());
        int changed = 0;
        for (SupplierApplication app : approved) {
            boolean touched = false;
            boolean vigente = app.getAccreditationValidUntil() != null
                    && !app.getAccreditationValidUntil().isBefore(today);
            List<SupplierDocument> own = docs.getOrDefault(app.getId(), List.of());
            Maintenance m = of(app, own, today);
            if (vigente) {
                touched |= alertExpiring(app, own, m, today);
                touched |= followIrregularity(app, m.certificates(), today, now, deregistrationDays);
            }
            if (m.renewalOpen() && app.getRenewalNoticeSentAt() == null) {
                app.markRenewalNoticeSent(now);
                publish(SupplierEvents.RENEWAL_DUE, app, Map.of());
                touched = true;
            }
            if (touched) {
                changed++;
            }
        }
        return changed;
    }

    private boolean alertExpiring(SupplierApplication app, List<SupplierDocument> own, Maintenance m,
            LocalDate today) {
        List<SupplierDocument> toAlert = own.stream()
                .filter(d -> d.getType().isCertificate() && d.getAlertSentOn() == null && d.getValidUntil() != null
                        && CertificateRegularity.state(d.getValidUntil(), today, m.alertDays()) == State.EXPIRING)
                .toList();
        if (toAlert.isEmpty()) {
            return false;
        }
        toAlert.forEach(d -> d.markAlerted(today));
        publish(SupplierEvents.CERTIFICATE_EXPIRING, app, Map.of("certificates",
                toAlert.stream().map(d -> d.getType().name()).collect(Collectors.joining(","))));
        return true;
    }

    private boolean followIrregularity(SupplierApplication app, Result certs, LocalDate today, Instant now,
            int deregistrationDays) {
        if (certs.regular()) {
            return regularizeIfNeeded(app, null, now);
        }
        boolean touched = false;
        if (app.getIrregularSince() == null) {
            LocalDate due = calendar.calendar().addBusinessDays(today,
                    parameters.integer(ParameterKey.CERTIFICATE_REGULARIZATION_BUSINESS_DAYS));
            app.markIrregular(today, due, now);
            String which = irregularLabels(certs);
            history.save(new ApplicationEvent(app.getId(), app.getStatus(),
                    "Certidão vencida ou faltando: " + which + ". Prazo para regularizar: " + date(due) + ".",
                    null, now));
            audit.record(SupplierEvents.AGGREGATE, app.getId(), "CERTIFICATE_IRREGULAR", null,
                    Map.of("irregularSince", today.toString(), "regularizationDue", due.toString()));
            publish(SupplierEvents.CERTIFICATE_EXPIRED, app, Map.of("certificates", irregularNames(certs)));
            touched = true;
        } else if (!app.isTemporarilyInactive() && today.isAfter(app.getRegularizationDue())) {
            app.inactivate(today, now);
            history.save(new ApplicationEvent(app.getId(), app.getStatus(),
                    "Inativo temporariamente: a certidão não foi regularizada no prazo (Política art. 22).", null,
                    now));
            audit.record(SupplierEvents.AGGREGATE, app.getId(), "INACTIVATE", null,
                    Map.of("inactiveSince", today.toString()));
            publish(SupplierEvents.INACTIVATED, app, Map.of("certificates", irregularNames(certs)));
            touched = true;
        }
        if (app.getDeregistrationAlertedAt() == null
                && !today.isBefore(app.getIrregularSince().plusDays(deregistrationDays))) {
            app.markDeregistrationAlerted(now);
            publish(SupplierEvents.DEREGISTRATION_ALERT, app, Map.of());
            touched = true;
        }
        return touched;
    }

    /**
     * Confere de novo depois de uma certidão nova ou de uma validade corrigida: em dia,
     * encerra a irregularidade e a inativação na hora (Política art. 22: "cessará com a
     * regularização").
     */
    @Transactional
    public void reevaluate(SupplierApplication app, Long actorId) {
        if (app.getStatus() != ApplicationStatus.APPROVED || app.getIrregularSince() == null) {
            return;
        }
        if (of(app).certificates().regular()) {
            regularizeIfNeeded(app, actorId, clock.instant());
        }
    }

    private boolean regularizeIfNeeded(SupplierApplication app, Long actorId, Instant now) {
        if (app.getIrregularSince() == null) {
            return false;
        }
        boolean wasInactive = app.isTemporarilyInactive();
        app.regularize(now);
        history.save(new ApplicationEvent(app.getId(), app.getStatus(), wasInactive
                ? "Certidões regularizadas: o credenciado voltou a ficar ativo."
                : "Certidões regularizadas.", actorId, now));
        audit.record(SupplierEvents.AGGREGATE, app.getId(), "REGULARIZE",
                Map.of("inactive", wasInactive), Map.of("inactive", false));
        if (wasInactive) {
            publish(SupplierEvents.REACTIVATED, app, Map.of());
        }
        return true;
    }

    // ----------------------------------------------------- lado do fornecedor

    /** Atualização cadastral do credenciado. Avisa a Comissão do que mudou. */
    @Transactional
    public SupplierApplication updateRegistration(long userId, RegistrationData d, long version) {
        SupplierApplication app = approvedOf(userId, version);
        Municipality municipality = d.municipalityId() == null ? null
                : municipalities.findById(d.municipalityId())
                        .orElseThrow(() -> new BusinessException("MUNICIPALITY_NOT_FOUND", "Município não encontrado."));
        List<String> issues = registrationIssues(app, d, municipality);
        if (!issues.isEmpty()) {
            throw new BusinessException("REGISTRATION_INVALID", String.join(" ", issues),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> before = snapshot(app);
        app.updateRegistration(d, municipality, clock.instant());
        Map<String, Object> after = snapshot(app);
        List<String> changed = changedGroups(before, after);
        if (changed.isEmpty()) {
            return app;
        }
        Instant now = clock.instant();
        String summary = String.join(", ", changed);
        history.save(new ApplicationEvent(app.getId(), app.getStatus(), "Dados atualizados: " + summary + ".",
                userId, now));
        // Só os grupos alterados: os valores (endereço, conta) não vão para a trilha.
        audit.record(SupplierEvents.AGGREGATE, app.getId(), "UPDATE_REGISTRATION", null,
                Map.of("changed", changed));
        publish(SupplierEvents.REGISTRATION_UPDATED, app, Map.of("changes", summary));
        return app;
    }

    /** Novo documento ou certidão do credenciado: registra no andamento e avisa a Comissão. */
    void documentReplaced(SupplierApplication app, SupplierDocument doc, long actorId, boolean bySupplier) {
        Instant now = clock.instant();
        String what = doc.getType().label() + (doc.getValidUntil() == null ? ""
                : " (válida até " + date(doc.getValidUntil()) + ")");
        history.save(new ApplicationEvent(app.getId(), app.getStatus(),
                (bySupplier ? "Documento atualizado: " : "Certidão obtida pela Comissão: ") + what + ".", actorId,
                now));
        if (bySupplier) {
            publish(SupplierEvents.REGISTRATION_UPDATED, app, Map.of("changes", "documento — " + what));
        }
        reevaluate(app, actorId);
    }

    /** Pedido de renovação (Política art. 22), aberto {@code renewal_notice_days} antes do fim. */
    @Transactional
    public SupplierApplication requestRenewal(long userId, boolean declarationsAccepted, long version) {
        SupplierApplication app = approvedOf(userId, version);
        Maintenance m = of(app);
        if (app.getRenewalRequestedAt() != null) {
            throw new BusinessException("RENEWAL_ALREADY_REQUESTED", "Seu pedido de renovação já está com a Comissão.",
                    HttpStatus.CONFLICT);
        }
        if (!m.renewalOpen()) {
            throw new BusinessException("RENEWAL_NOT_OPEN", "A renovação abre em " + date(m.renewalOpensOn())
                    + ", " + parameters.integer(ParameterKey.RENEWAL_NOTICE_DAYS)
                    + " dias antes do fim do credenciamento.", HttpStatus.CONFLICT);
        }
        if (!declarationsAccepted) {
            throw new BusinessException("DECLARATIONS_REQUIRED",
                    "Confirme de novo as declarações e o termo de adesão (Anexos III e V).",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        requireCertificatesInOrder(m.certificates(),
                "Antes de pedir a renovação, envie as certidões atualizadas");
        Instant now = clock.instant();
        app.requestRenewal(now);
        history.save(new ApplicationEvent(app.getId(), app.getStatus(),
                "Pedido de renovação do credenciamento, com as declarações confirmadas.", userId, now));
        audit.record(SupplierEvents.AGGREGATE, app.getId(), "REQUEST_RENEWAL", null,
                Map.of("renewalRequestedAt", now.toString()));
        publish(SupplierEvents.RENEWAL_REQUESTED, app, Map.of());
        return app;
    }

    // ----------------------------------------------------------- lado da Comissão

    /** Reconferência semestral: a Comissão conferiu todas as certidões nos sítios oficiais. */
    @Transactional
    public SupplierApplication recheck(long applicationId, long reviewerId, String note, long version) {
        SupplierApplication app = approved(applicationId, version);
        Maintenance m = of(app);
        List<String> noDate = m.certificates().withState(State.NO_DATE).stream()
                .map(c -> c.type().label()).toList();
        if (!noDate.isEmpty()) {
            throw new BusinessException("CERTIFICATE_WITHOUT_DATE",
                    "Informe a validade de: " + String.join("; ", noDate) + ".", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        m.documents().stream().filter(d -> d.getType().isCertificate())
                .forEach(d -> d.setValidity(d.getValidUntil(), reviewerId, now));
        app.recordRecheck(today, reviewerId, now);
        String result = m.certificates().regular() ? "certidões em dia"
                : "pendente: " + irregularLabels(m.certificates());
        history.save(new ApplicationEvent(app.getId(), app.getStatus(), "Reconferência semestral das certidões ("
                + result + ")" + (note == null || note.isBlank() ? "." : ": " + note.trim()), reviewerId, now));
        audit.record(SupplierEvents.AGGREGATE, app.getId(), "RECHECK", null,
                Map.of("lastRecheckOn", today.toString(), "regular", m.certificates().regular()));
        reevaluate(app, reviewerId);
        return app;
    }

    /** Validade de uma certidão, informada ou corrigida (pela Comissão, vale como conferida). */
    @Transactional
    public SupplierDocument setValidity(SupplierApplication app, long documentId, LocalDate validUntil, long actorId,
            boolean byCommission) {
        SupplierDocument doc = documents.findByIdAndApplicationId(documentId, app.getId())
                .orElseThrow(() -> new NotFoundException("Documento"));
        if (!doc.getType().isCertificate()) {
            throw new BusinessException("NOT_A_CERTIFICATE", "Este documento não tem validade.");
        }
        requireValidityInRange(validUntil, !byCommission);
        LocalDate before = doc.getValidUntil();
        doc.setValidity(validUntil, byCommission ? actorId : null, clock.instant());
        audit.record("supplier_document", doc.getId(), "SET_VALIDITY",
                before == null ? null : Map.of("validUntil", before.toString()),
                Map.of("validUntil", validUntil.toString(), "verified", byCommission));
        if (byCommission && app.getStatus() == ApplicationStatus.APPROVED && !Objects.equals(before, validUntil)) {
            history.save(new ApplicationEvent(app.getId(), app.getStatus(), "Validade conferida pela Comissão: "
                    + doc.getType().label() + " até " + date(validUntil) + ".", actorId, clock.instant()));
        }
        reevaluate(app, actorId);
        return doc;
    }

    @Transactional
    public SupplierApplication renew(long applicationId, long reviewerId, String note, long version) {
        SupplierApplication app = approved(applicationId, version);
        if (app.getRenewalRequestedAt() == null) {
            throw new BusinessException("RENEWAL_NOT_REQUESTED", "Não há pedido de renovação deste credenciado.",
                    HttpStatus.CONFLICT);
        }
        requireCertificatesInOrder(of(app).certificates(), "Para renovar, as certidões precisam estar em dia");
        LocalDate today = calendar.today();
        LocalDate base = app.getAccreditationValidUntil() == null || app.getAccreditationValidUntil().isBefore(today)
                ? today : app.getAccreditationValidUntil();
        LocalDate validUntil = base.plusMonths(parameters.integer(ParameterKey.ACCREDITATION_VALIDITY_MONTHS));
        LocalDate previous = app.getAccreditationValidUntil();
        Instant now = clock.instant();
        app.renew(validUntil, today, reviewerId, now);
        history.save(new ApplicationEvent(app.getId(), app.getStatus(), "Credenciamento renovado até "
                + date(validUntil) + (note == null || note.isBlank() ? "." : ". " + note.trim()), reviewerId, now));
        audit.record(SupplierEvents.AGGREGATE, app.getId(), "RENEW",
                previous == null ? null : Map.of("validUntil", previous.toString()),
                Map.of("validUntil", validUntil.toString()));
        publish(SupplierEvents.RENEWED, app, Map.of());
        return app;
    }

    @Transactional
    public SupplierApplication returnRenewal(long applicationId, long reviewerId, String message, long version) {
        SupplierApplication app = approved(applicationId, version);
        if (app.getRenewalRequestedAt() == null) {
            throw new BusinessException("RENEWAL_NOT_REQUESTED", "Não há pedido de renovação deste credenciado.",
                    HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        app.returnRenewal(message, now);
        history.save(new ApplicationEvent(app.getId(), app.getStatus(), "Renovação devolvida: "
                + app.getRenewalReturnMessage(), reviewerId, now));
        audit.record(SupplierEvents.AGGREGATE, app.getId(), "RETURN_RENEWAL", null, null,
                app.getRenewalReturnMessage());
        publish(SupplierEvents.RENEWAL_RETURNED, app, Map.of());
        return app;
    }

    // ------------------------------------------------------------------ apoio

    /**
     * Validade plausível: não vencida (o fornecedor não envia certidão vencida) e no máximo
     * um ano à frente (as certidões valem de 30 a 180 dias; protege contra erro de digitação).
     */
    void requireValidityInRange(LocalDate validUntil, boolean mustNotBeExpired) {
        LocalDate today = calendar.today();
        if (validUntil == null) {
            throw new BusinessException("VALIDITY_REQUIRED", "Informe até quando a certidão é válida.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (mustNotBeExpired && validUntil.isBefore(today)) {
            throw new BusinessException("CERTIFICATE_EXPIRED",
                    "Esta certidão já venceu. Emita uma nova no site do órgão e envie de novo.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (validUntil.isAfter(today.plusYears(1))) {
            throw new BusinessException("VALIDITY_TOO_FAR",
                    "Confira a data: as certidões valem no máximo alguns meses.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void requireCertificatesInOrder(Result certs, String prefix) {
        List<String> pending = certs.checks().stream()
                .filter(c -> c.state() == State.EXPIRED || c.state() == State.MISSING || c.state() == State.NO_DATE)
                .map(c -> c.type().label() + " (" + c.state().label().toLowerCase() + ")").toList();
        if (!pending.isEmpty()) {
            throw new BusinessException("CERTIFICATES_PENDING", prefix + ": " + String.join("; ", pending) + ".",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private SupplierApplication approvedOf(long userId, long version) {
        SupplierApplication app = applications.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Requerimento"));
        return requireApproved(app, version);
    }

    private SupplierApplication approved(long applicationId, long version) {
        return requireApproved(applications.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Requerimento")), version);
    }

    private static SupplierApplication requireApproved(SupplierApplication app, long version) {
        if (app.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Este cadastro foi alterado em outra janela. Recarregue a página.", HttpStatus.CONFLICT);
        }
        if (app.getStatus() != ApplicationStatus.APPROVED) {
            throw new BusinessException("NOT_ACCREDITED", "Só credenciados habilitados.", HttpStatus.CONFLICT);
        }
        return app;
    }

    private static List<String> registrationIssues(SupplierApplication app, RegistrationData d,
            Municipality municipality) {
        List<String> issues = new ArrayList<>();
        if (blank(d.street()) || blank(d.addressNumber()) || blank(d.district()) || municipality == null
                || !DocumentNumbers.isValidCep(DocumentNumbers.digits(nz(d.cep())))) {
            issues.add("Confira o endereço (rua, número, bairro, município e CEP).");
        }
        if (!DocumentNumbers.isValidBrazilianPhone(DocumentNumbers.digits(nz(d.phone())))) {
            issues.add("Informe um telefone com DDD.");
        }
        if (blank(d.contactEmail()) || !d.contactEmail().contains("@")) {
            issues.add("Informe um e-mail de contato válido.");
        }
        if (app.getSupplierType() != SupplierType.INDIVIDUAL
                && (blank(d.representativeName())
                        || !DocumentNumbers.isValidCpf(DocumentNumbers.digits(nz(d.representativeCpf()))))) {
            issues.add("Informe o nome e o CPF válido do representante.");
        }
        if (blank(d.bankName()) || blank(d.bankBranch()) || blank(d.bankAccount()) || blank(d.accountHolder())) {
            issues.add("Informe banco, agência, conta e titular (a conta deve ser do próprio credenciado).");
        }
        return issues;
    }

    /** Valores atuais por grupo (para descobrir o que mudou; nunca vai para evento). */
    private static Map<String, Object> snapshot(SupplierApplication a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Endereço", List.of(nz(a.getStreet()), nz(a.getAddressNumber()), nz(a.getAddressComplement()),
                nz(a.getDistrict()), a.getMunicipality() == null ? "" : a.getMunicipality().getName(), nz(a.getCep())));
        m.put("Contato", List.of(nz(a.getPhone()), nz(a.getContactEmail()), nz(a.getWebsite())));
        m.put("Representante", List.of(nz(a.getRepresentativeName()), nz(a.getRepresentativeCpf()),
                nz(a.getRepresentativeRole())));
        m.put("Dados bancários", List.of(nz(a.getBankName()), nz(a.getBankBranch()), nz(a.getBankAccount()),
                a.getBankAccountType() == null ? "" : a.getBankAccountType().name(), nz(a.getPixKey()),
                nz(a.getAccountHolder())));
        return m;
    }

    private static List<String> changedGroups(Map<String, Object> before, Map<String, Object> after) {
        return before.keySet().stream().filter(k -> !Objects.equals(before.get(k), after.get(k))).toList();
    }

    private static String irregularLabels(Result certs) {
        return certs.checks().stream().filter(c -> c.state() == State.EXPIRED || c.state() == State.MISSING)
                .map(c -> c.type().label()).collect(Collectors.joining("; "));
    }

    private static String irregularNames(Result certs) {
        return certs.checks().stream().filter(c -> c.state() == State.EXPIRED || c.state() == State.MISSING)
                .map(c -> c.type().name()).collect(Collectors.joining(","));
    }

    private void publish(String type, SupplierApplication app, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>(extra);
        payload.put("applicationId", app.getId());
        payload.put("userId", app.getUserId());
        events.publish(DomainEvent.of(type, SupplierEvents.AGGREGATE, app.getId(), payload, clock));
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
