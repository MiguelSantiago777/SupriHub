package org.institutonata.suprihub.judgment.application;

import java.util.List;

import org.institutonata.suprihub.contract.application.AwardChallenges;
import org.institutonata.suprihub.supplier.domain.Appeal;
import org.institutonata.suprihub.supplier.infra.AppealRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Com recurso contra o resultado em análise, a ordem não é emitida (o resultado pode mudar — D-049). */
@Component
public class PendingJudgmentAppeals implements AwardChallenges {

    private final AppealRepository appeals;

    public PendingJudgmentAppeals(AppealRepository appeals) {
        this.appeals = appeals;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean pending(long tenderId) {
        return appeals.existsByTenderIdAndStatusIn(tenderId, List.of(Appeal.Status.FILED, Appeal.Status.FORWARDED));
    }
}
