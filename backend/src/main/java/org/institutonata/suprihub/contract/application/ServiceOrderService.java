package org.institutonata.suprihub.contract.application;

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
import java.util.Optional;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.contract.domain.ContractEnums.FileKind;
import org.institutonata.suprihub.contract.domain.ContractEnums.Kind;
import org.institutonata.suprihub.contract.domain.ContractEnums.PaymentMethod;
import org.institutonata.suprihub.contract.domain.ContractEnums.PriceSource;
import org.institutonata.suprihub.contract.domain.ContractEnums.ReceiptKind;
import org.institutonata.suprihub.contract.domain.ContractEnums.Status;
import org.institutonata.suprihub.contract.domain.ContractEvents;
import org.institutonata.suprihub.contract.domain.ContractRules;
import org.institutonata.suprihub.contract.domain.ContractRules.Issue;
import org.institutonata.suprihub.contract.domain.PriceBankEntry;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.contract.domain.ServiceOrderFile;
import org.institutonata.suprihub.contract.domain.ServiceOrderReceipt;
import org.institutonata.suprihub.contract.infra.PriceBankRepository;
import org.institutonata.suprihub.contract.infra.ServiceOrderFileRepository;
import org.institutonata.suprihub.contract.infra.ServiceOrderReceiptRepository;
import org.institutonata.suprihub.contract.infra.ServiceOrderRepository;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.proposal.domain.Proposal;
import org.institutonata.suprihub.proposal.infra.ProposalRepository;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.storage.FileScanner;
import org.institutonata.suprihub.shared.storage.FileStorage;
import org.institutonata.suprihub.shared.storage.FileStorage.StoredFile;
import org.institutonata.suprihub.shared.storage.UploadedFiles;
import org.institutonata.suprihub.supplier.application.SupplierMaintenanceService;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.CertificateRegularity.State;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.tender.application.TenderAwardListener;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderItem;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Da adjudicação ao pagamento (Edital 9 e 10; Manual 4.2, passos 7 a 11): a OF/OS nasce em
 * preparação; a equipe completa e emite; o fornecedor assina (Faixa 1: o aceite já vale);
 * o gestor registra o Termo de Recebimento e Avaliação; o fornecedor envia a NF/RPA; o
 * Financeiro registra o pagamento. O recebimento definitivo alimenta o Banco de Preços.
 */
@Service
public class ServiceOrderService implements TenderAwardListener {

    /** Ordem com o que a tela precisa. */
    public record View(ServiceOrder order, Tender tender, SupplierApplication supplier,
            List<ServiceOrderReceipt> receipts, List<ServiceOrderFile> files) {

        public ServiceOrderReceipt lastReceipt() {
            return receipts.isEmpty() ? null : receipts.get(receipts.size() - 1);
        }
    }

    /** Nota fiscal / RPA. */
    public record Invoice(String number, LocalDate date, BigDecimal value) {
    }

    private final ServiceOrderRepository orders;
    private final ServiceOrderReceiptRepository receipts;
    private final ServiceOrderFileRepository files;
    private final PriceBankRepository priceBank;
    private final TenderRepository tenders;
    private final ProposalRepository proposals;
    private final SupplierApplicationRepository applications;
    private final SupplierMaintenanceService maintenance;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final FileStorage storage;
    private final FileScanner scanner;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;

    public ServiceOrderService(ServiceOrderRepository orders, ServiceOrderReceiptRepository receipts,
            ServiceOrderFileRepository files, PriceBankRepository priceBank, TenderRepository tenders,
            ProposalRepository proposals, SupplierApplicationRepository applications, ParameterService parameters,
            CalendarService calendar, FileStorage storage, FileScanner scanner, DomainEvents events,
            AuditService audit, SupplierMaintenanceService maintenance, Clock clock) {
        this.maintenance = maintenance;
        this.orders = orders;
        this.receipts = receipts;
        this.files = files;
        this.priceBank = priceBank;
        this.tenders = tenders;
        this.proposals = proposals;
        this.applications = applications;
        this.parameters = parameters;
        this.calendar = calendar;
        this.storage = storage;
        this.scanner = scanner;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------- adjudicação

    @Override
    public void onAwarded(Tender tender, long applicationId, Long proposalId, BigDecimal value, Instant acceptedAt) {
        if (orders.findByTenderId(tender.getId()).isPresent()) {
            return;
        }
        Proposal proposal = proposalId == null ? null : proposals.findById(proposalId).orElse(null);
        Map<Long, BigDecimal> prices = proposal == null ? Map.of() : proposal.getUnitPrices();
        List<ServiceOrder.Item> items = new ArrayList<>();
        for (TenderItem i : tender.getItems()) {
            items.add(new ServiceOrder.Item(i.getPosition(), i.getDescription(), i.getUnit(), i.getQuantity(),
                    prices.get(i.getId())));
        }
        String where = tender.getLocation();
        if (tender.getExecutionMunicipality() != null) {
            where = (where == null ? "" : where + " — ") + tender.getExecutionMunicipality().getName();
        }
        ServiceOrder.Terms terms = new ServiceOrder.Terms(Kind.SUPPLY, null,
                tender.getTitle() + "\n" + tender.getDescription(), tender.getSchedule(), where,
                parameters.integer(ParameterKey.PAYMENT_DEADLINE_DAYS), null, null, null);
        ServiceOrder order = orders.save(new ServiceOrder(tender.getId(), applicationId, proposalId, value,
                tender.getBand() == Band.FAIXA_1, terms, items, clock.instant()));
        audit.record(ContractEvents.AGGREGATE, order.getId(), "CREATE", null,
                Map.of("tenderId", tender.getId(), "applicationId", applicationId, "value", value));
        events.publish(DomainEvent.of(ContractEvents.CREATED, ContractEvents.AGGREGATE, order.getId(),
                Map.of("serviceOrderId", order.getId()), clock));
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public Page<ServiceOrder> search(List<Status> statuses, Pageable pageable) {
        return orders.findByStatusIn(statuses, pageable);
    }

    @Transactional(readOnly = true)
    public Map<Status, Long> counts() {
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : orders.countByStatus()) {
            counts.put((Status) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public View view(long id) {
        return view(order(id));
    }

    @Transactional(readOnly = true)
    public Optional<ServiceOrder> byTender(long tenderId) {
        return orders.findByTenderId(tenderId);
    }

    @Transactional(readOnly = true)
    public List<ServiceOrder> forSupplier(long applicationId) {
        return orders.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(o -> o.getStatus() != Status.DRAFT && o.getStatus() != Status.CANCELLED).toList();
    }

    /** Ordem do próprio fornecedor (a preparação não aparece para ele). */
    @Transactional(readOnly = true)
    public View viewForSupplier(long applicationId, long id) {
        ServiceOrder o = order(id);
        if (!o.getApplicationId().equals(applicationId) || o.getStatus() == Status.DRAFT) {
            throw new NotFoundException("Ordem de fornecimento");
        }
        return view(o);
    }

    @Transactional(readOnly = true)
    public Tender tender(long tenderId) {
        return tenders.findById(tenderId).orElseThrow(() -> new NotFoundException("Oportunidade"));
    }

    private View view(ServiceOrder o) {
        return new View(o, tender(o.getTenderId()), applications.findById(o.getApplicationId()).orElse(null),
                receipts.findByServiceOrderIdOrderByReceivedAt(o.getId()),
                files.findByServiceOrderIdOrderByUploadedAt(o.getId()));
    }

    // -------------------------------------------------------- preparar / emitir

    @Transactional
    public ServiceOrder update(long id, ServiceOrder.Terms terms, long version) {
        ServiceOrder o = current(id, version);
        if (o.getStatus() != Status.DRAFT && o.getStatus() != Status.EXPIRED) {
            throw new BusinessException("ORDER_LOCKED", "A ordem já foi emitida.", HttpStatus.CONFLICT);
        }
        validate(o, terms);
        o.update(terms, clock.instant());
        return o;
    }

    private void validate(ServiceOrder o, ServiceOrder.Terms t) {
        List<Map<String, String>> issues = new ArrayList<>();
        if (t.objectDescription() == null || t.objectDescription().isBlank()) {
            issues.add(Map.of("field", "objectDescription", "message", "Descreva o objeto."));
        }
        int maxDays = parameters.integer(ParameterKey.PAYMENT_DEADLINE_DAYS);
        if (t.paymentDays() < 1 || t.paymentDays() > maxDays) {
            issues.add(Map.of("field", "paymentDays", "message",
                    "O pagamento deve ser em até " + maxDays + " dias (Edital 10.1)."));
        }
        if (!ContractRules.upfrontAllowed(t.upfrontValue(), o.getTotalValue(),
                parameters.decimal(ParameterKey.UPFRONT_PAYMENT_MAX_SHARE))) {
            issues.add(Map.of("field", "upfrontValue", "message",
                    "O sinal é limitado a 30% do valor, só para eventos e alimentação (Edital 10.4)."));
        }
        if (t.penaltyPct() != null && (t.penaltyPct().compareTo(BigDecimal.valueOf(2)) < 0
                || t.penaltyPct().compareTo(BigDecimal.TEN) > 0)) {
            issues.add(Map.of("field", "penaltyPct", "message", "A multa por inexecução vai de 2% a 10%."));
        }
        if (!issues.isEmpty()) {
            throw new BusinessException("ORDER_INVALID", "Corrija os campos da ordem.",
                    HttpStatus.UNPROCESSABLE_ENTITY, issues);
        }
    }

    /** Emite depois de conferir a habilitação do selecionado (Edital 9.1; Manual 4.2, passo 7). */
    @Transactional
    public ServiceOrder issue(long id, long userId, long version) {
        ServiceOrder o = current(id, version);
        if (o.getStatus() != Status.DRAFT && o.getStatus() != Status.EXPIRED) {
            throw new BusinessException("ORDER_ALREADY_ISSUED", "Esta ordem já foi emitida.", HttpStatus.CONFLICT);
        }
        validate(o, new ServiceOrder.Terms(o.getKind(), o.getProject(), o.getObjectDescription(), o.getSchedule(),
                o.getLocation(), o.getPaymentDays(), o.getUpfrontValue(), o.getPenaltyPct(), o.getNotes()));
        requireRegular(o, "emitir a ordem");
        Instant now = clock.instant();
        LocalDate due = calendar.calendar().addBusinessDays(calendar.today(),
                parameters.integer(ParameterKey.SERVICE_ORDER_SIGNATURE_BUSINESS_DAYS));
        String prefix = o.getKind() == Kind.SERVICE ? "OS" : "OF";
        Instant acceptedAt = o.isAcceptanceBased() ? acceptance(o) : null;
        String before = o.getStatus().name();
        o.issue("%s-%d-%04d".formatted(prefix, calendar.today().getYear(), orders.nextNumber()), userId, due,
                acceptedAt, now);
        orders.flush();
        audit.record(ContractEvents.AGGREGATE, id, "ISSUE", Map.of("status", before),
                Map.of("status", o.getStatus().name(), "number", o.getNumber()));
        events.publish(DomainEvent.of(ContractEvents.ISSUED, ContractEvents.AGGREGATE, id,
                Map.of("serviceOrderId", id), clock));
        return o;
    }

    private Instant acceptance(ServiceOrder o) {
        return o.getProposalId() == null ? null
                : proposals.findById(o.getProposalId()).map(Proposal::getSubmittedAt).orElse(null);
    }

    /** O fornecedor assina eletronicamente (Edital 9.1), com data, hora e IP. */
    @Transactional
    public ServiceOrder sign(long applicationId, long id, long userId, String ip, boolean declarationAccepted) {
        ServiceOrder o = order(id);
        if (!o.getApplicationId().equals(applicationId)) {
            throw new NotFoundException("Ordem de fornecimento");
        }
        if (!o.canSign(calendar.today())) {
            throw new BusinessException("ORDER_NOT_SIGNABLE", "Esta ordem não está aguardando a sua assinatura.",
                    HttpStatus.CONFLICT);
        }
        if (!declarationAccepted) {
            throw new BusinessException("ORDER_DECLARATION", "Confirme que leu e aceita as cláusulas da ordem.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    List.of(Map.of("field", "declarationAccepted", "message", "Marque a confirmação.")));
        }
        o.sign(userId, ip, clock.instant());
        audit.record(ContractEvents.AGGREGATE, id, "SIGN", Map.of("status", "ISSUED"),
                Map.of("status", o.getStatus().name()));
        return o;
    }

    /** Job: assinatura vencida → EXPIRED (a equipe reemite ou cancela e convoca o próximo). */
    @Transactional
    public int expireOverdue() {
        List<ServiceOrder> overdue = orders.findByStatusAndSignatureDueDateBefore(Status.ISSUED, calendar.today());
        for (ServiceOrder o : overdue) {
            o.expire(clock.instant());
            audit.record(ContractEvents.AGGREGATE, o.getId(), "EXPIRE", Map.of("status", "ISSUED"),
                    Map.of("status", o.getStatus().name()));
            events.publish(DomainEvent.of(ContractEvents.EXPIRED, ContractEvents.AGGREGATE, o.getId(),
                    Map.of("serviceOrderId", o.getId()), clock));
        }
        return overdue.size();
    }

    @Transactional
    public ServiceOrder cancel(long id, String reason, long version) {
        ServiceOrder o = current(id, version);
        String before = o.getStatus().name();
        try {
            o.cancel(reason, clock.instant());
        } catch (IllegalStateException e) {
            throw new BusinessException("ORDER_NOT_CANCELLABLE", "Esta ordem não pode mais ser cancelada.",
                    HttpStatus.CONFLICT);
        }
        audit.record(ContractEvents.AGGREGATE, id, "CANCEL", Map.of("status", before),
                Map.of("status", o.getStatus().name()), reason);
        return o;
    }

    // ------------------------------------------------------------- recebimento

    /** Termo de Recebimento e Avaliação (Anexo IX). */
    @Transactional
    public ServiceOrderReceipt receive(long id, ServiceOrderReceipt.Data data, long userId) {
        ServiceOrder o = order(id);
        if (!o.getStatus().canReceive()) {
            throw new BusinessException("ORDER_NOT_RECEIVABLE", "O recebimento vem depois da assinatura da ordem.",
                    HttpStatus.CONFLICT);
        }
        List<Map<String, String>> issues = new ArrayList<>();
        for (Issue i : ContractRules.checkScores(data.scores())) {
            issues.add(Map.of("field", i.field(), "message", i.message()));
        }
        if (data.kind() == null) {
            issues.add(Map.of("field", "kind", "message", "Escolha o tipo de recebimento."));
        } else if (data.kind() == ReceiptKind.WITH_RESERVATIONS
                && (blank(data.reservations()) || data.correctionDays() == null || data.correctionDays() < 1)) {
            issues.add(Map.of("field", "reservations", "message", "Descreva as correções e o prazo em dias."));
        } else if (data.kind() == ReceiptKind.REJECTED && blank(data.rejectReason())) {
            issues.add(Map.of("field", "rejectReason", "message", "Escreva o motivo da recusa."));
        }
        if (data.executionDate() == null || data.executionDate().isAfter(calendar.today())) {
            issues.add(Map.of("field", "executionDate", "message", "Informe a data da execução/entrega."));
        }
        if (!issues.isEmpty()) {
            throw new BusinessException("RECEIPT_INVALID", "Corrija o termo de recebimento.",
                    HttpStatus.UNPROCESSABLE_ENTITY, issues);
        }
        Instant now = clock.instant();
        ServiceOrderReceipt r = receipts.save(new ServiceOrderReceipt(id, data, userId, now));
        String before = o.getStatus().name();
        o.receive(data.kind(), r.getAverage(),
                calendar.today().plusDays(o.getPaymentDays()), now);
        orders.flush();
        audit.record(ContractEvents.AGGREGATE, id, "RECEIVE", Map.of("status", before),
                Map.of("status", o.getStatus().name(), "kind", data.kind().name(), "average", r.getAverage()));
        if (data.kind() == ReceiptKind.DEFINITIVE) {
            recordPrices(o, userId, now);
        }
        events.publish(DomainEvent.of(ContractEvents.RECEIVED, ContractEvents.AGGREGATE, id,
                Map.of("serviceOrderId", id, "receiptId", r.getId()), clock));
        if (data.kind().isFinal()) {
            checkLowRatings(o);
        }
        return r;
    }

    /** Recebimento definitivo → Banco de Preços (Manual 4.2, passo 11). */
    private void recordPrices(ServiceOrder o, long userId, Instant now) {
        Tender t = tender(o.getTenderId());
        List<ServiceOrder.Item> items = o.getItems();
        List<BigDecimal> effective = ContractRules.effectiveUnitPrices(
                items.stream().map(ServiceOrder.Item::getUnitPrice).toList(),
                items.stream().map(ServiceOrder.Item::getQuantity).toList(), o.getTotalValue());
        for (int i = 0; i < items.size(); i++) {
            BigDecimal price = effective.get(i);
            if (price != null && price.signum() > 0) {
                priceBank.save(new PriceBankEntry(t.getSupplyLine().getId(), items.get(i).getDescription(),
                        items.get(i).getUnit(), price, PriceSource.CONTRACT, o.getNumber(), o.getApplicationId(),
                        o.getId(), calendar.today(), userId, now));
            }
        }
    }

    /** Edital 9.5: duas médias abaixo de 3 em 12 meses → alerta à Comissão. */
    private void checkLowRatings(ServiceOrder o) {
        int window = parameters.integer(ParameterKey.LOW_RATING_WINDOW_MONTHS);
        LocalDate today = calendar.today();
        Instant since = today.minusMonths(window).atStartOfDay(clock.getZone()).toInstant();
        List<ContractRules.Rated> history = orders.findByApplicationIdAndRatedAtAfter(o.getApplicationId(), since)
                .stream().filter(x -> x.getRating() != null)
                .map(x -> new ContractRules.Rated(LocalDate.ofInstant(x.getRatedAt(), clock.getZone()), x.getRating()))
                .toList();
        if (o.getRating() != null && o.getRating().compareTo(parameters.decimal(ParameterKey.LOW_RATING_THRESHOLD)) < 0
                && ContractRules.suspensionAlert(history, today, parameters.decimal(ParameterKey.LOW_RATING_THRESHOLD),
                        parameters.integer(ParameterKey.LOW_RATING_OCCURRENCES), window)) {
            audit.record("supplier_application", o.getApplicationId(), "LOW_RATING_ALERT", null,
                    Map.of("serviceOrderId", o.getId(), "occurrences", history.size()));
            events.publish(DomainEvent.of(ContractEvents.LOW_RATING_ALERT, ContractEvents.AGGREGATE, o.getId(),
                    Map.of("serviceOrderId", o.getId(), "applicationId", o.getApplicationId()), clock));
        }
    }

    /** Ciência do fornecedor no último termo. */
    @Transactional
    public void acknowledge(long applicationId, long id) {
        View v = viewForSupplier(applicationId, id);
        ServiceOrderReceipt r = v.lastReceipt();
        if (r == null) {
            throw new BusinessException("NO_RECEIPT", "Ainda não há termo de recebimento.", HttpStatus.CONFLICT);
        }
        r.acknowledge(clock.instant());
        audit.record("service_order_receipt", r.getId(), "ACK", null, Map.of("serviceOrderId", id));
    }

    // ------------------------------------------------------- nota e pagamento

    /** Arquivo da contratação. O fornecedor só envia a nota fiscal/RPA. */
    @Transactional
    public ServiceOrderFile addFile(long id, FileKind kind, MultipartFile file, long userId) {
        ServiceOrder o = order(id);
        if (o.getStatus() == Status.DRAFT || o.getStatus() == Status.CANCELLED) {
            throw new BusinessException("ORDER_CLOSED", "Esta ordem não aceita arquivos.", HttpStatus.CONFLICT);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Escolha um arquivo.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        long maxBytes = parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "O arquivo é maior que "
                    + parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) + " MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String type = UploadedFiles.detect(file, false);
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store("order-" + id + "-" + kind.name().toLowerCase(), file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo", e);
        }
        if (scanner.scan(stored.storageKey()) == FileScanner.ScanResult.INFECTED) {
            storage.delete(stored.storageKey());
            throw new BusinessException("FILE_INFECTED", "O arquivo foi recusado pela verificação de segurança.");
        }
        ServiceOrderFile f = files.save(new ServiceOrderFile(id, kind,
                UploadedFiles.safeFilename(file.getOriginalFilename()), type, stored.sizeBytes(), stored.sha256(),
                stored.storageKey(), userId, clock.instant()));
        audit.record(ContractEvents.AGGREGATE, id, "FILE_" + kind.name(), null,
                Map.of("fileId", f.getId(), "sha256", stored.sha256()));
        return f;
    }

    /** Nota fiscal / RPA (Edital 10.1): exige o arquivo e o recebimento definitivo. */
    @Transactional
    public ServiceOrder invoice(long id, Invoice inv, MultipartFile file, long userId, Long applicationId) {
        ServiceOrder o = order(id);
        if (applicationId != null && !o.getApplicationId().equals(applicationId)) {
            throw new NotFoundException("Ordem de fornecimento");
        }
        if (o.getStatus() != Status.RECEIVED && o.getStatus() != Status.INVOICED) {
            throw new BusinessException("ORDER_NOT_INVOICEABLE",
                    "A nota fiscal é enviada depois do recebimento definitivo.", HttpStatus.CONFLICT);
        }
        List<Map<String, String>> issues = new ArrayList<>();
        if (blank(inv.number())) {
            issues.add(Map.of("field", "number", "message", "Informe o número da nota ou do RPA."));
        }
        if (inv.date() == null || inv.date().isAfter(calendar.today())) {
            issues.add(Map.of("field", "date", "message", "Informe a data de emissão."));
        }
        if (inv.value() == null || inv.value().signum() <= 0) {
            issues.add(Map.of("field", "value", "message", "Informe o valor."));
        }
        boolean hasFile = file != null && !file.isEmpty();
        if (!hasFile && !files.existsByServiceOrderIdAndKind(id, FileKind.INVOICE)) {
            issues.add(Map.of("field", "file", "message", "Anexe a nota fiscal ou o RPA."));
        }
        if (!issues.isEmpty()) {
            throw new BusinessException("INVOICE_INVALID", "Corrija os dados da nota fiscal.",
                    HttpStatus.UNPROCESSABLE_ENTITY, issues);
        }
        if (hasFile) {
            addFile(id, FileKind.INVOICE, file, userId);
        }
        o.invoice(inv.number(), inv.date(), inv.value(), clock.instant());
        orders.flush();
        audit.record(ContractEvents.AGGREGATE, id, "INVOICE", null,
                Map.of("number", inv.number().trim(), "value", inv.value()));
        events.publish(DomainEvent.of(ContractEvents.INVOICED, ContractEvents.AGGREGATE, id,
                Map.of("serviceOrderId", id), clock));
        return o;
    }

    /**
     * Pagamento por Pix/transferência à conta do fornecedor (Edital 10.1 e 10.3): exige
     * comprovante e confere de novo a regularidade.
     */
    @Transactional
    public ServiceOrder pay(long id, LocalDate paidOn, BigDecimal value, PaymentMethod method, long userId,
            long version) {
        ServiceOrder o = current(id, version);
        if (o.getStatus() != Status.INVOICED) {
            throw new BusinessException("ORDER_NOT_PAYABLE", "O pagamento exige a nota fiscal ou o RPA.",
                    HttpStatus.CONFLICT);
        }
        List<Map<String, String>> issues = new ArrayList<>();
        if (paidOn == null || paidOn.isAfter(calendar.today())) {
            issues.add(Map.of("field", "paidOn", "message", "Informe a data do pagamento."));
        }
        if (value == null || value.signum() <= 0) {
            issues.add(Map.of("field", "value", "message", "Informe o valor pago."));
        }
        if (method == null) {
            issues.add(Map.of("field", "method", "message", "Escolha Pix ou transferência."));
        }
        if (!files.existsByServiceOrderIdAndKind(id, FileKind.PAYMENT_PROOF)) {
            issues.add(Map.of("field", "proof", "message", "Anexe o comprovante do pagamento."));
        }
        if (!issues.isEmpty()) {
            throw new BusinessException("PAYMENT_INVALID", "Corrija os dados do pagamento.",
                    HttpStatus.UNPROCESSABLE_ENTITY, issues);
        }
        requireRegular(o, "pagar");
        o.pay(paidOn, value, method, userId, clock.instant());
        orders.flush();
        audit.record(ContractEvents.AGGREGATE, id, "PAY", Map.of("status", "INVOICED"),
                Map.of("status", o.getStatus().name(), "value", value, "method", method.name()));
        events.publish(DomainEvent.of(ContractEvents.PAID, ContractEvents.AGGREGATE, id,
                Map.of("serviceOrderId", id), clock));
        return o;
    }

    @Transactional(readOnly = true)
    public ServiceOrderFile file(long orderId, long fileId) {
        ServiceOrderFile f = files.findById(fileId).orElseThrow(() -> new NotFoundException("Arquivo"));
        if (!f.getServiceOrderId().equals(orderId)) {
            throw new NotFoundException("Arquivo");
        }
        return f;
    }

    public InputStream open(ServiceOrderFile f) {
        return storage.load(f.getFileKey());
    }

    // ------------------------------------------------------------------ apoio

    /**
     * Credenciamento vigente e certidões em dia, conferidas uma a uma (Edital 9.1 e 10.3;
     * Política art. 17 e 19, § 2º). Certidão vencida bloqueia mesmo dentro do prazo de
     * regularização: o pequeno negócio regulariza (5 + 5 d.u., Edital 4.4) e a ação é refeita.
     */
    private void requireRegular(ServiceOrder o, String action) {
        SupplierApplication app = applications.findById(o.getApplicationId()).orElse(null);
        LocalDate today = calendar.today();
        boolean ok = app != null && app.isActiveOn(today);
        if (!ok) {
            throw new BusinessException("SUPPLIER_IRREGULAR", app != null && app.isBlockedOn(today)
                    ? "O fornecedor está suspenso ou inativado até " + app.getBlockedUntil() + " ("
                            + app.getBlockReason() + "). Não é possível " + action + "."
                    : app != null && app.isTemporarilyInactive()
                            ? "O fornecedor está inativo temporariamente por certidão vencida. Peça a regularização "
                                    + "(Edital 4.4) antes de " + action + "."
                            : "O fornecedor está sem credenciamento vigente. Peça a regularização (Edital 4.4) antes de "
                                    + action + ".", HttpStatus.CONFLICT);
        }
        List<String> pending = maintenance.certificatesOf(app).checks().stream()
                .filter(c -> c.state() == State.EXPIRED || c.state() == State.MISSING)
                .map(c -> c.type().label()).toList();
        if (!pending.isEmpty()) {
            throw new BusinessException("SUPPLIER_CERTIFICATE_EXPIRED", "Certidão vencida ou faltando: "
                    + String.join("; ", pending) + ". Peça a regularização (pequeno negócio: 5 dias úteis, "
                    + "prorrogáveis por mais 5 — Edital 4.4) antes de " + action + ".", HttpStatus.CONFLICT);
        }
    }

    private ServiceOrder order(long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Ordem de fornecimento"));
    }

    private ServiceOrder current(long id, long version) {
        ServiceOrder o = order(id);
        if (o.getVersion() != version) {
            throw new BusinessException("CONCURRENT_UPDATE",
                    "Esta ordem foi alterada por outra pessoa. Recarregue a página.", HttpStatus.CONFLICT);
        }
        return o;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
