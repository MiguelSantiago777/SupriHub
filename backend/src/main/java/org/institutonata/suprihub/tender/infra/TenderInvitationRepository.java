package org.institutonata.suprihub.tender.infra;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.tender.domain.TenderEnums.InvitationStatus;
import org.institutonata.suprihub.tender.domain.TenderInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenderInvitationRepository extends JpaRepository<TenderInvitation, Long> {

    List<TenderInvitation> findByTenderIdOrderByInvitedAt(Long tenderId);

    Optional<TenderInvitation> findByTenderIdAndApplicationId(Long tenderId, Long applicationId);

    List<TenderInvitation> findByApplicationIdAndStatusIn(Long applicationId, Collection<InvitationStatus> statuses);

    List<TenderInvitation> findByStatusAndDueDateBefore(InvitationStatus status, LocalDate date);

    boolean existsByTenderIdAndStatusIn(Long tenderId, Collection<InvitationStatus> statuses);

    /** Última seleção confirmada de cada fornecedor no rodízio (ordem da fila — D-034). */
    @Query("""
            SELECT i.applicationId, MAX(i.respondedAt) FROM TenderInvitation i
            WHERE i.status = :status AND i.applicationId IN :ids
            GROUP BY i.applicationId
            """)
    List<Object[]> lastSelection(@Param("status") InvitationStatus status, @Param("ids") Collection<Long> ids);
}
