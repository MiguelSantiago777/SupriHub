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

    Optional<Appeal> findFirstByApplicationIdOrderByFiledAtDesc(Long applicationId);

    Page<Appeal> findByStatusIn(Collection<Appeal.Status> statuses, Pageable pageable);

    @Query("SELECT a.status, COUNT(a) FROM Appeal a GROUP BY a.status")
    List<Object[]> countByStatus();
}
