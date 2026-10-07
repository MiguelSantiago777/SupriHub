package org.institutonata.suprihub.supplier.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.catalog.domain.Municipality;
import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.catalog.infra.MunicipalityRepository;
import org.institutonata.suprihub.catalog.infra.SupplyLineRepository;
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
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness;
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness.Issue;
import org.institutonata.suprihub.supplier.domain.ApplicationData;
import org.institutonata.suprihub.supplier.domain.ApplicationEvent;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.CertificateRegularity;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.institutonata.suprihub.supplier.domain.SupplierEvents;
import org.institutonata.suprihub.supplier.infra.AppealRepository;
import org.institutonata.suprihub.supplier.infra.ApplicationEventRepository;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.supplier.infra.SupplierDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lado do fornecedor: preencher o requerimento (rascunho), enviar documentos e enviar
 * para análise. Cada conta tem um único requerimento.
 */
@Service
public class SupplierApplicationService {

    private final SupplierApplicationRepository applications;
    private final SupplierDocumentRepository documents;
    private final ApplicationEventRepository history;
    private final AppUserRepository users;
    private final MunicipalityRepository municipalities;
    private final SupplyLineRepository supplyLines;
    private final FileStorage storage;
    private final FileScanner scanner;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final DomainEvents events;
    private final AuditService audit;
    private final AppealRepository appeals;
    private final SupplierMaintenanceService maintenance;
    private final Clock clock;

    public SupplierApplicationService(SupplierApplicationRepository applications, SupplierDocumentRepository documents,
            ApplicationEventRepository history, AppUserRepository users, MunicipalityRepository municipalities,
            SupplyLineRepository supplyLines, FileStorage storage, FileScanner scanner, ParameterService parameters,
            CalendarService calendar, DomainEvents events, AuditService audit, AppealRepository appeals,
            SupplierMaintenanceService maintenance, Clock clock) {
        this.maintenance = maintenance;
        this.applications = applications;
        this.documents = documents;
        this.history = history;
        this.users = users;
        this.municipalities = municipalities;
        this.supplyLines = supplyLines;
        this.storage = storage;
        this.scanner = scanner;
        this.parameters = parameters;
        this.calendar = calendar;
        this.events = events;
        this.audit = audit;
        this.appeals = appeals;
        this.clock = clock;
    }

    /** Requerimento do usuário; na primeira visita cria o rascunho com nome e e-mail da conta. */
    @Transactional
    public SupplierApplication getOrCreate(long userId) {
        return applications.findByUserId(userId).orElseGet(() -> {
            AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuário"));
            Instant now = clock.instant();
            SupplierApplication created = applications.save(
                    new SupplierApplication(userId, user.getFullName(), user.getEmail(), now));
            history.save(new ApplicationEvent(created.getId(), ApplicationStatus.DRAFT, null, userId, now));
            return created;
        });
    }

    @Transactional
    public SupplierApplication saveDraft(long userId, ApplicationData data, long version) {
        SupplierApplication app = editable(userId, version);
        Municipality municipality = data.municipalityId() == null ? null
                : municipalities.findById(data.municipalityId())
                        .orElseThrow(() -> new BusinessException("MUNICIPALITY_NOT_FOUND", "Município não encontrado."));
        Set<SupplyLine> lines = new HashSet<>();
        for (String code : data.lineCodes()) {
            lines.add(supplyLines.findByCode(code).orElseThrow(() ->
                    new BusinessException("SUPPLY_LINE_NOT_FOUND", "Linha de fornecimento " + code + " não existe.")));
        }
        app.update(data, municipality, lines, clock.instant());
        return app;
    }

    /**
     * Envia (ou substitui) um documento. Certidões exigem a validade. Com o requerimento em
     * edição vale como rascunho; já credenciado, é a atualização documental (Anexo V) e a
     * Comissão é avisada.
     */
    @Transactional
    public SupplierDocument upload(long userId, DocumentType type, MultipartFile file, LocalDate validUntil) {
        SupplierApplication app = getOrCreate(userId);
        boolean accredited = app.getStatus() == ApplicationStatus.APPROVED;
        if (!accredited) {
            editable(userId, null);
        }
        if (app.getSupplierType() != null && !type.appliesTo(app.getSupplierType())) {
            throw new BusinessException("DOCUMENT_NOT_APPLICABLE",
                    "Este documento não se aplica ao tipo de negócio escolhido.");
        }
        if (type.isCertificate()) {
            maintenance.requireValidityInRange(validUntil, true);
        }
        SupplierDocument doc = store(app, type, file, type.isCertificate() ? validUntil : null, null);
        if (accredited) {
            maintenance.documentReplaced(app, doc, userId, true);
        }
        return doc;
    }

    /**
     * Certidão obtida pela própria Comissão no sítio oficial (Política art. 21, § 2º: "renovadas
     * pelo Instituto por consulta direta"). Já entra conferida.
     */
    @Transactional
    public SupplierDocument uploadByCommission(long applicationId, DocumentType type, MultipartFile file,
            LocalDate validUntil, long reviewerId) {
        SupplierApplication app = applications.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Requerimento"));
        if (!type.isCertificate()) {
            throw new BusinessException("NOT_A_CERTIFICATE",
                    "A Comissão anexa só certidões. Os demais documentos são do fornecedor.");
        }
        if (app.getStatus() != ApplicationStatus.APPROVED && app.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new BusinessException("APPLICATION_NOT_UNDER_COMMISSION",
                    "Só é possível anexar certidões em requerimentos em análise ou de credenciados.",
                    HttpStatus.CONFLICT);
        }
        maintenance.requireValidityInRange(validUntil, true);
        SupplierDocument doc = store(app, type, file, validUntil, reviewerId);
        if (app.getStatus() == ApplicationStatus.APPROVED) {
            maintenance.documentReplaced(app, doc, reviewerId, false);
        }
        return doc;
    }

    private SupplierDocument store(SupplierApplication app, DocumentType type, MultipartFile file,
            LocalDate validUntil, Long verifierId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "Escolha um arquivo.");
        }
        long maxBytes = parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "O arquivo é maior que "
                    + parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) + " MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String contentType = UploadedFiles.detect(file, false);
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store("supplier-" + app.getId(), file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado", e);
        }
        if (scanner.scan(stored.storageKey()) == FileScanner.ScanResult.INFECTED) {
            storage.delete(stored.storageKey());
            throw new BusinessException("FILE_INFECTED", "O arquivo foi recusado pela verificação de segurança.");
        }
        documents.findByApplicationIdAndType(app.getId(), type).ifPresent(old -> {
            documents.delete(old);
            documents.flush();
            storage.delete(old.getStorageKey());
        });
        SupplierDocument doc = documents.save(new SupplierDocument(app.getId(), type,
                UploadedFiles.safeFilename(file.getOriginalFilename()), contentType, stored.sizeBytes(), stored.sha256(),
                stored.storageKey(), clock.instant()));
        if (validUntil != null) {
            doc.setValidity(validUntil, verifierId, clock.instant());
        }
        Map<String, Object> after = new HashMap<>(
                Map.of("applicationId", app.getId(), "type", type.name(), "sha256", stored.sha256()));
        if (validUntil != null) {
            after.put("validUntil", validUntil.toString());
        }
        audit.record("supplier_document", doc.getId(), verifierId == null ? "UPLOAD" : "UPLOAD_BY_COMMISSION", null,
                after);
        return doc;
    }

    /** Validade de uma certidão já enviada (cadastros antigos ou correção de digitação). */
    @Transactional
    public SupplierDocument setValidity(long userId, long documentId, LocalDate validUntil) {
        SupplierApplication app = getOrCreate(userId);
        if (app.getStatus() != ApplicationStatus.APPROVED) {
            editable(userId, null);
        }
        return maintenance.setValidity(app, documentId, validUntil, userId, false);
    }

    @Transactional
    public void deleteDocument(long userId, long documentId) {
        SupplierApplication app = editable(userId, null);
        SupplierDocument doc = documents.findByIdAndApplicationId(documentId, app.getId())
                .orElseThrow(() -> new NotFoundException("Documento"));
        documents.delete(doc);
        storage.delete(doc.getStorageKey());
        audit.record("supplier_document", doc.getId(), "DELETE",
                Map.of("applicationId", app.getId(), "type", doc.getType().name()), null);
    }

    @Transactional(readOnly = true)
    public List<SupplierDocument> documents(long applicationId) {
        return documents.findByApplicationIdOrderByUploadedAt(applicationId);
    }

    @Transactional(readOnly = true)
    public List<ApplicationEvent> history(long applicationId) {
        return history.findByApplicationIdOrderByOccurredAt(applicationId);
    }

    /** Documento do próprio fornecedor, para download. */
    @Transactional(readOnly = true)
    public SupplierDocument ownDocument(long userId, long documentId) {
        SupplierApplication app = applications.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Requerimento"));
        return documents.findByIdAndApplicationId(documentId, app.getId())
                .orElseThrow(() -> new NotFoundException("Documento"));
    }

    public InputStream open(SupplierDocument doc) {
        return storage.load(doc.getStorageKey());
    }

    /** Pendências do requerimento atual (vazia = pronto para enviar). */
    @Transactional(readOnly = true)
    public List<Issue> pendingItems(SupplierApplication app) {
        List<CertificateRegularity.Uploaded> uploaded =
                CertificateRegularity.Uploaded.of(documents.findByApplicationIdOrderByUploadedAt(app.getId()));
        return ApplicationCompleteness.check(ApplicationCompleteness.Snapshot.of(app), uploaded, calendar.today());
    }

    /**
     * Envia para análise. O prazo de 10 dias úteis (Edital 4.3) corre do requerimento
     * completo — por isso só aceita o envio sem pendências.
     */
    @Transactional
    public SupplierApplication submit(long userId, long version) {
        SupplierApplication app = editable(userId, version);
        List<Issue> issues = pendingItems(app);
        if (!issues.isEmpty()) {
            throw new BusinessException("APPLICATION_INCOMPLETE",
                    "Faltam " + issues.size() + " item(ns) para enviar o requerimento.", HttpStatus.UNPROCESSABLE_ENTITY,
                    issues.stream().map(i -> Map.of("field", i.field(), "message", i.message())).toList());
        }
        ApplicationStatus before = app.getStatus();
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        LocalDate due = calendar.calendar().addBusinessDays(today,
                parameters.integer(ParameterKey.ANALYSIS_BUSINESS_DAYS));
        String protocol = app.getProtocol() != null ? null
                : "CR-%d-%05d".formatted(today.getYear(), applications.nextProtocolNumber());
        app.submit(protocol, due, now);
        String message = before == ApplicationStatus.DRAFT ? null : "Reenviado após " + before.label().toLowerCase();
        history.save(new ApplicationEvent(app.getId(), ApplicationStatus.SUBMITTED, message, userId, now));
        audit.record(SupplierEvents.AGGREGATE, app.getId(), "SUBMIT", Map.of("status", before.name()),
                Map.of("status", app.getStatus().name(), "protocol", app.getProtocol(),
                        "analysisDueDate", due.toString()));
        events.publish(DomainEvent.of(SupplierEvents.APPLICATION_SUBMITTED, SupplierEvents.AGGREGATE, app.getId(),
                Map.of("applicationId", app.getId(), "userId", userId), clock));
        return app;
    }

    private SupplierApplication editable(long userId, Long version) {
        SupplierApplication app = getOrCreate(userId);
        if (version != null && app.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Seu requerimento foi alterado em outra janela. Recarregue a página.", HttpStatus.CONFLICT);
        }
        if (!app.getStatus().isEditableBySupplier()) {
            throw new BusinessException("APPLICATION_LOCKED", app.getStatus() == ApplicationStatus.APPROVED
                    ? "Seu credenciamento já foi aprovado. Use \"Atualizar dados\" na sua área."
                    : "Seu requerimento está em análise e não pode ser alterado agora.", HttpStatus.CONFLICT);
        }
        if (hasOpenAppeal(app)) {
            throw new BusinessException("APPLICATION_UNDER_APPEAL",
                    "Seu recurso está em análise. Aguarde a decisão para alterar o requerimento.",
                    HttpStatus.CONFLICT);
        }
        return app;
    }

    /** Com recurso em andamento, o requerimento fica com a Comissão/Diretoria. */
    public boolean hasOpenAppeal(SupplierApplication app) {
        return app.getStatus() == ApplicationStatus.REJECTED
                && appeals.findFirstByApplicationIdAndKindOrderByFiledAtDesc(app.getId(), Appeal.Kind.HABILITATION)
                        .map(a -> a.getStatus().isOpen()).orElse(false);
    }
}
