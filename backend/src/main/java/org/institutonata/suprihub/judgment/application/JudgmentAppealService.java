package org.institutonata.suprihub.judgment.application;

import java.util.Optional;

import org.institutonata.suprihub.judgment.infra.JudgmentEntryRepository;
import org.institutonata.suprihub.judgment.infra.JudgmentRepository;
import org.institutonata.suprihub.judgment.model.Judgment;
import org.institutonata.suprihub.judgment.model.JudgmentEntry;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.Status;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.supplier.application.AppealService;
import org.institutonata.suprihub.supplier.domain.Appeal;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.infra.AppealRepository;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recurso contra o resultado do julgamento (Anexo X, etapa "seleção/julgamento" — D-049): o
 * proponente que não venceu recorre em 3 d.u. da homologação. As decisões seguem o mesmo rito
 * do recurso de habilitação ({@link AppealService}); provido, o julgamento é reaberto.
 */
@Service
public class JudgmentAppealService {

    private final JudgmentRepository judgments;
    private final JudgmentEntryRepository entries;
    private final TenderRepository tenders;
    private final SupplierApplicationRepository applications;
    private final AppealRepository appealRepository;
    private final AppealService appeals;
    private final CalendarService calendar;

    public JudgmentAppealService(JudgmentRepository judgments, JudgmentEntryRepository entries,
            TenderRepository tenders, SupplierApplicationRepository applications, AppealRepository appealRepository,
            AppealService appeals, CalendarService calendar) {
        this.judgments = judgments;
        this.entries = entries;
        this.tenders = tenders;
        this.applications = applications;
        this.appealRepository = appealRepository;
        this.appeals = appeals;
        this.calendar = calendar;
    }

    /** Pode recorrer: participou, não venceu, prazo aberto e ainda não recorreu deste resultado. */
    @Transactional(readOnly = true)
    public boolean canAppeal(long tenderId, long applicationId) {
        return eligibility(tenderId, applicationId) == null;
    }

    @Transactional
    public Appeal file(long userId, long tenderId, String reasons, String request, MultipartFile file) {
        SupplierApplication app = applications.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Requerimento"));
        String problem = eligibility(tenderId, app.getId());
        if (problem != null) {
            throw new BusinessException("APPEAL_NOT_ALLOWED", problem, HttpStatus.CONFLICT);
        }
        Judgment j = homologated(tenderId).orElseThrow();
        Tender t = tenders.findById(tenderId).orElseThrow(() -> new NotFoundException("Oportunidade"));
        String subject = (t.getNumber() + " — " + t.getTitle());
        return appeals.fileAgainstJudgment(userId, app, j.getId(), tenderId,
                subject.length() > 300 ? subject.substring(0, 300) : subject, reasons, request, file);
    }

    /** Último recurso do fornecedor nesta oportunidade (para a tela dele). */
    @Transactional(readOnly = true)
    public Optional<Appeal> mine(long tenderId, long applicationId) {
        return appealRepository.findFirstByTenderIdAndApplicationIdOrderByFiledAtDesc(tenderId, applicationId);
    }

    private String eligibility(long tenderId, long applicationId) {
        Optional<Judgment> live = homologated(tenderId);
        if (live.isEmpty()) {
            return "Só cabe recurso depois da homologação do resultado.";
        }
        Judgment j = live.get();
        if (!j.canAppeal(calendar.today())) {
            return "O prazo para recorrer do resultado terminou.";
        }
        Optional<JudgmentEntry> mine = entries.findByJudgment(j.getId()).stream()
                .filter(e -> e.getApplicationId().equals(applicationId)).findFirst();
        if (mine.isEmpty()) {
            return "Só quem enviou proposta pode recorrer do resultado.";
        }
        if (mine.get().getProposalId().equals(j.getWinnerProposalId())) {
            return "A proposta vencedora não recorre do resultado.";
        }
        if (appeals.forJudgment(j.getId(), applicationId).isPresent()) {
            return "Você já recorreu deste resultado.";
        }
        return null;
    }

    private Optional<Judgment> homologated(long tenderId) {
        return judgments.findFirstByTenderIdAndStatusNot(tenderId, Status.ANNULLED)
                .filter(j -> j.getStatus() == Status.HOMOLOGATED);
    }
}
