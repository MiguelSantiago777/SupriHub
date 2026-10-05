package org.institutonata.suprihub.proposal.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.messaging.application.DomainEvents;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.proposal.domain.Proposal;
import org.institutonata.suprihub.proposal.domain.ProposalRules;
import org.institutonata.suprihub.proposal.domain.ProposalRules.Issue;
import org.institutonata.suprihub.proposal.domain.ProposalRules.Mode;
import org.institutonata.suprihub.proposal.domain.ProposalRules.RequestedItem;
import org.institutonata.suprihub.proposal.infra.ProposalRepository;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.storage.FileScanner;
import org.institutonata.suprihub.shared.storage.FileStorage;
import org.institutonata.suprihub.shared.storage.FileStorage.StoredFile;
import org.institutonata.suprihub.shared.storage.UploadedFiles;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.tender.application.OpportunityService;
import org.institutonata.suprihub.tender.application.OpportunityService.View;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEvents;
import org.institutonata.suprihub.tender.domain.TenderInvitation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Propostas (Anexo VII). Faixas 2 e 3: envio e substituição até o prazo, em sigilo. Faixa 1:
 * enviar o preço é o aceite do convite do rodízio; a Comissão confere e confirma.
 */
@Service
public class ProposalService {

    /** Dados da proposta (sem o arquivo). */
    public record Input(Mode mode, Map<Long, BigDecimal> unitPrices, BigDecimal totalValue, String executionDeadline,
            int validityDays, String specialConditions, String notes, boolean declarationAccepted) {
    }

    private final ProposalRepository proposals;
    private final OpportunityService opportunities;
    private final FileStorage storage;
    private final FileScanner scanner;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final DomainEvents events;
    private final AuditService audit;
    private final Clock clock;

    public ProposalService(ProposalRepository proposals, OpportunityService opportunities, FileStorage storage,
            FileScanner scanner, ParameterService parameters, CalendarService calendar, DomainEvents events,
            AuditService audit, Clock clock) {
        this.proposals = proposals;
        this.opportunities = opportunities;
        this.storage = storage;
        this.scanner = scanner;
        this.parameters = parameters;
        this.calendar = calendar;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Proposal submit(long userId, long tenderId, Input in, MultipartFile file) {
        View view = opportunities.get(userId, tenderId);
        if (!view.canSubmit()) {
            throw new BusinessException("PROPOSAL_CLOSED", view.tender().getBand() == Band.FAIXA_1
                    ? "Este convite não está mais aguardando resposta."
                    : "O prazo para propostas desta oportunidade terminou.", HttpStatus.CONFLICT);
        }
        Tender tender = view.tender();
        SupplierApplication app = opportunities.application(userId);
        Proposal proposal = view.proposal();
        boolean hasNewFile = file != null && !file.isEmpty();
        boolean keepsFile = proposal != null && proposal.hasFile() && in.mode() != Mode.ONLINE;
        List<RequestedItem> items = tender.getItems().stream()
                .map(i -> new RequestedItem(i.getId(), i.getQuantity())).toList();
        ProposalRules.Input rulesIn = new ProposalRules.Input(in.mode(), in.unitPrices(), in.totalValue(),
                in.validityDays(), hasNewFile || keepsFile, in.declarationAccepted());
        List<Issue> issues = ProposalRules.check(rulesIn, items, parameters.integer(ParameterKey.PROPOSAL_VALIDITY_DAYS));
        if (!issues.isEmpty()) {
            throw new BusinessException("PROPOSAL_INVALID", "Corrija os itens da proposta.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    issues.stream().map(i -> Map.of("field", i.field(), "message", i.message())).toList());
        }
        Proposal.File stored = hasNewFile && in.mode() != Mode.ONLINE ? store(tenderId, app.getId(), file) : null;
        Instant now = clock.instant();
        boolean first = proposal == null;
        if (first) {
            proposal = new Proposal(tenderId, app.getId(), now);
        }
        String replaced = proposal.fill(in.mode(), in.unitPrices(), ProposalRules.total(rulesIn, items),
                in.executionDeadline(), in.validityDays(), in.specialConditions(), in.notes(), stored, now);
        proposal = proposals.save(proposal);
        if (replaced != null) {
            storage.delete(replaced);
        }
        TenderInvitation inv = view.invitation();
        if (tender.getBand() == Band.FAIXA_1 && inv != null) {
            inv.accept(now);
        }
        audit.record("proposal", proposal.getId(), first ? "SUBMIT" : "REPLACE", null,
                Map.of("tenderId", tenderId, "mode", in.mode().name()));
        events.publish(DomainEvent.of(TenderEvents.PROPOSAL_SUBMITTED, TenderEvents.AGGREGATE, tenderId,
                Map.of("tenderId", tenderId, "proposalId", proposal.getId(), "applicationId", app.getId()), clock));
        return proposal;
    }

    /** Retira a proposta (Faixas 2 e 3, antes do prazo). */
    @Transactional
    public void withdraw(long userId, long tenderId) {
        View view = opportunities.get(userId, tenderId);
        if (!view.canWithdraw() || view.proposal() == null) {
            throw new BusinessException("PROPOSAL_CLOSED", "Não é possível retirar esta proposta.",
                    HttpStatus.CONFLICT);
        }
        Proposal p = view.proposal();
        proposals.delete(p);
        if (p.hasFile()) {
            storage.delete(p.file().storageKey());
        }
        audit.record("proposal", p.getId(), "WITHDRAW", Map.of("tenderId", tenderId), null);
    }

    @Transactional(readOnly = true)
    public List<Proposal> forTender(long tenderId) {
        return proposals.findByTenderIdOrderByTotalValueAscSubmittedAtAsc(tenderId);
    }

    @Transactional(readOnly = true)
    public long countForTender(long tenderId) {
        return proposals.countByTenderId(tenderId);
    }

    @Transactional(readOnly = true)
    public Proposal get(long id) {
        return proposals.findById(id).orElseThrow(() -> new NotFoundException("Proposta"));
    }

    public InputStream openFile(Proposal p) {
        if (!p.hasFile()) {
            throw new NotFoundException("Arquivo da proposta");
        }
        return storage.load(p.file().storageKey());
    }

    public LocalDate today() {
        return calendar.today();
    }

    private Proposal.File store(long tenderId, long applicationId, MultipartFile file) {
        long maxBytes = parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "O arquivo é maior que "
                    + parameters.integer(ParameterKey.UPLOAD_MAX_SIZE_MB) + " MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String type = UploadedFiles.detect(file, true);
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store("proposal-" + tenderId + "-" + applicationId, file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo", e);
        }
        if (scanner.scan(stored.storageKey()) == FileScanner.ScanResult.INFECTED) {
            storage.delete(stored.storageKey());
            throw new BusinessException("FILE_INFECTED", "O arquivo foi recusado pela verificação de segurança.");
        }
        return new Proposal.File(UploadedFiles.safeFilename(file.getOriginalFilename()), type, stored.sizeBytes(),
                stored.sha256(), stored.storageKey());
    }
}
