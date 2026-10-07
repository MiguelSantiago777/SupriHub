package org.institutonata.suprihub.contract.infra;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.contract.domain.ContractEnums.Status;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

    /** A ordem viva da oportunidade (as anteriores, não assinadas, ficam canceladas). */
    Optional<ServiceOrder> findFirstByTenderIdAndStatusNot(Long tenderId, Status status);

    /** Todas as ordens da oportunidade, inclusive as canceladas (convocações anteriores). */
    List<ServiceOrder> findByTenderIdOrderByCreatedAt(Long tenderId);

    Optional<ServiceOrder> findFirstByTenderIdOrderByCreatedAtDesc(Long tenderId);

    Page<ServiceOrder> findByStatusIn(Collection<Status> statuses, Pageable pageable);

    List<ServiceOrder> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<ServiceOrder> findByStatusAndSignatureDueDateBefore(Status status, LocalDate date);

    /** Contratações avaliadas do fornecedor (nota do termo final). */
    List<ServiceOrder> findByApplicationIdAndRatedAtAfter(Long applicationId, Instant since);

    @Query("SELECT o.status, COUNT(o) FROM ServiceOrder o GROUP BY o.status")
    List<Object[]> countByStatus();

    @Query(value = "SELECT nextval('service_order_number_seq')", nativeQuery = true)
    long nextNumber();
}
