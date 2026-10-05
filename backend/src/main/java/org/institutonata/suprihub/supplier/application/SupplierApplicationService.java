package org.institutonata.suprihub.supplier.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness;
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness.Issue;
import org.institutonata.suprihub.supplier.domain.ApplicationData;
import org.institutonata.suprihub.supplier.domain.ApplicationEvent;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.institutonata.suprihub.supplier.domain.SupplierEvents;
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
    private final Clock clock;

    public SupplierApplicationService(SupplierApplicationRepository applications, SupplierDocumentRepository documents,
            ApplicationEventRepository history, AppUserRepository users, MunicipalityRepository municipalities,
            SupplyLineRepository supplyLines, FileStorage storage, FileScanner scanner, ParameterService parameters,
            CalendarService calendar, DomainEvents events, AuditService audit, Clock clock) {
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

    @Transactional
    public SupplierDocument upload(long userId, DocumentType type, MultipartFile file) {
        SupplierApplication app = editable(userId, null);
        if (app.getSupplierType() != null && !type.appliesTo(app.getSupplierType())) {
            throw new BusinessException("DOCUMENT_NOT_APPLICABLE",
                    "Este documento não se aplica ao tipo de negócio escolhido.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "Escolha um arquivo.");
        }
        long maxBytes = parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "O arquivo é maior que "
                    + parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) + " MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String contentType = detectContentType(file);
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
                safeFilename(file.getOriginalFilename()), contentType, stored.sizeBytes(), stored.sha256(),
                stored.storageKey(), clock.instant()));
        audit.record("supplier_document", doc.getId(), "UPLOAD", null,
                Map.of("applicationId", app.getId(), "type", type.name(), "sha256", stored.sha256()));
        return doc;
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
        Set<DocumentType> uploaded = documents.findByApplicationIdOrderByUploadedAt(app.getId()).stream()
                .map(SupplierDocument::getType).collect(Collectors.toSet());
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
                    ? "Seu credenciamento já foi aprovado. Para atualizar dados, fale com a Comissão."
                    : "Seu requerimento está em análise e não pode ser alterado agora.", HttpStatus.CONFLICT);
        }
        return app;
    }

    /** Tipo real pelo conteúdo (não confia na extensão nem no Content-Type do navegador). */
    static String detectContentType(MultipartFile file) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado", e);
        }
        if (read >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return "application/pdf";
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "image/png";
        }
        throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "Envie o documento em PDF, JPG ou PNG.");
    }

    static String safeFilename(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|*?:]", "").trim();
        if (name.isEmpty()) {
            name = "documento";
        }
        return name.length() > 150 ? name.substring(name.length() - 150) : name;
    }
}
