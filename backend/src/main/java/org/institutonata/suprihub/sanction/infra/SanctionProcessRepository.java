package org.institutonata.suprihub.sanction.infra;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.institutonata.suprihub.sanction.domain.SanctionEnums.Status;
import org.institutonata.suprihub.sanction.domain.SanctionProcess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SanctionProcessRepository extends JpaRepository<SanctionProcess, Long> {

    Page<SanctionProcess> findByStatusIn(Collection<Status> statuses, Pageable pageable);

    List<SanctionProcess> findByApplicationIdOrderByOpenedAtDesc(Long applicationId);

    List<SanctionProcess> findByStatusAndDefenseDueBefore(Status status, LocalDate date);

    List<SanctionProcess> findByStatusAndAppealDueBefore(Status status, LocalDate date);

    @Query("SELECT p.status, COUNT(p) FROM SanctionProcess p GROUP BY p.status")
    List<Object[]> countByStatus();

    @Query(value = "SELECT nextval('sanction_number_seq')", nativeQuery = true)
    long nextNumber();
}
