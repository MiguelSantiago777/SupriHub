package org.institutonata.suprihub.judgment.infra;

import java.util.List;

import org.institutonata.suprihub.judgment.model.JudgmentEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JudgmentEntryRepository extends JpaRepository<JudgmentEntry, JudgmentEntry.Key> {

    /** Classificadas pela posição; desclassificadas por último. */
    @Query("select e from JudgmentEntry e where e.judgmentId = ?1 order by e.position asc nulls last, e.totalValue")
    List<JudgmentEntry> findByJudgment(Long judgmentId);
}
