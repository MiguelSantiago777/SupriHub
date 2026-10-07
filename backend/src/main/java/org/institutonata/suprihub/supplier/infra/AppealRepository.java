package org.institutonata.suprihub.supplier.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.supplier.domain.Appeal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    Optional<Appeal> findFirstByApplicationIdAndKindOrderByFiledAtDesc(Long applicationId, Appeal.Kind kind);

    Optional<Appeal> findFirstByJudgmentIdAndApplicationIdOrderByFiledAtDesc(Long judgmentId, Long applicationId);

    Optional<Appeal> findFirstByTenderIdAndApplicationIdOrderByFiledAtDesc(Long tenderId, Long applicationId);

    boolean existsByTenderIdAndStatusIn(Long tenderId, Collection<Appeal.Status> statuses);

    Page<Appeal> findByStatusIn(Collection<Appeal.Status> statuses, Pageable pageable);

    @Query("SELECT a.status, COUNT(a) FROM Appeal a GROUP BY a.status")
    List<Object[]> countByStatus();
}
