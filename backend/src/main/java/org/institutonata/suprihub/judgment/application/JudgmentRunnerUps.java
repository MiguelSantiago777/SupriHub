package org.institutonata.suprihub.judgment.application;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.institutonata.suprihub.contract.application.NextRankedSource;
import org.institutonata.suprihub.judgment.infra.JudgmentEntryRepository;
import org.institutonata.suprihub.judgment.infra.JudgmentRepository;
import org.institutonata.suprihub.judgment.model.JudgmentEntry;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.Status;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Próximo classificado do mapa homologado (Edital 9.1): a menor posição ainda não convocada,
 * sem desclassificação e com o fornecedor ativo hoje. O valor é o da própria proposta (o
 * direito de cobrir valeu só para quem foi convocado a cobrir).
 */
@Component
public class JudgmentRunnerUps implements NextRankedSource {

    private final JudgmentRepository judgments;
    private final JudgmentEntryRepository entries;

    public JudgmentRunnerUps(JudgmentRepository judgments, JudgmentEntryRepository entries) {
        this.judgments = judgments;
        this.entries = entries;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ranked> nextAfter(long tenderId, Set<Long> excluded, Predicate<Long> active) {
        return judgments.findByTenderIdOrderByStartedAtDesc(tenderId).stream()
                .filter(j -> j.getStatus() == Status.HOMOLOGATED)
                .findFirst()
                .flatMap(j -> entries.findByJudgment(j.getId()).stream()
                        .filter(e -> e.getPosition() != null && !e.isDisqualified())
                        .filter(e -> !excluded.contains(e.getApplicationId()))
                        .sorted(Comparator.comparing(JudgmentEntry::getPosition))
                        .filter(e -> active.test(e.getApplicationId()))
                        .findFirst()
                        .map(e -> new Ranked(e.getApplicationId(), e.getProposalId(), e.getTotalValue(),
                                e.getPosition())));
    }
}
