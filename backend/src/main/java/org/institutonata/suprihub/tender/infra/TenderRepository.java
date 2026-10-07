package org.institutonata.suprihub.tender.infra;

import java.util.Collection;
import java.util.List;

import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEnums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenderRepository extends JpaRepository<Tender, Long> {

    Page<Tender> findByStatusIn(Collection<Status> statuses, Pageable pageable);

    /** Contratações de urgência sem ciência da Comissão (mais antigas primeiro). */
    java.util.List<Tender> findByUrgentTrueAndUrgencyAcknowledgedAtIsNullOrderByUrgencyAckDueAsc();

    /** Abertas nas linhas informadas, exceto a faixa indicada (rodízio vai por convite). */
    @Query("""
            SELECT t FROM Tender t
            WHERE t.status = :status AND t.supplyLine.code IN :lineCodes AND t.band <> :excluded
            ORDER BY t.proposalDeadline
            """)
    List<Tender> findOpenByLines(@Param("status") Status status, @Param("lineCodes") Collection<String> lineCodes,
            @Param("excluded") Band excluded);

    @Query("SELECT t.status, COUNT(t) FROM Tender t GROUP BY t.status")
    List<Object[]> countByStatus();

    @Query(value = "SELECT nextval('tender_number_seq')", nativeQuery = true)
    long nextNumber();
}
