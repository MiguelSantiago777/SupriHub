package org.institutonata.suprihub.judgment.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.judgment.model.Judgment;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.Status;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JudgmentRepository extends JpaRepository<Judgment, Long> {

    /** O julgamento vivo (não anulado) da oportunidade, se houver. */
    Optional<Judgment> findFirstByTenderIdAndStatusNot(Long tenderId, Status status);

    List<Judgment> findByTenderIdOrderByStartedAtDesc(Long tenderId);

    List<Judgment> findByStatusInOrderByDecidedAtAsc(Collection<Status> statuses);
}
