package org.institutonata.suprihub.supplier.infra;

import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierApplicationRepository extends JpaRepository<SupplierApplication, Long> {

    Optional<SupplierApplication> findByUserId(Long userId);

    /** Busca por protocolo, nome/razão social, nome fantasia ou CNPJ/CPF ({@code q} vazio casa com todos). */
    @Query("""
            SELECT a FROM SupplierApplication a
            WHERE a.status = :status
              AND (:q = ''
                   OR lower(coalesce(a.legalName, '')) LIKE lower(concat('%', :q, '%'))
                   OR lower(coalesce(a.tradeName, '')) LIKE lower(concat('%', :q, '%'))
                   OR lower(coalesce(a.protocol, '')) LIKE lower(concat('%', :q, '%'))
                   OR (:digits <> '' AND coalesce(a.documentNumber, '') LIKE concat('%', :digits, '%')))
            """)
    Page<SupplierApplication> search(@Param("status") ApplicationStatus status, @Param("q") String q,
            @Param("digits") String digits, Pageable pageable);

    @Query("SELECT a.status, COUNT(a) FROM SupplierApplication a GROUP BY a.status")
    List<Object[]> countByStatus();

    @Query(value = "SELECT nextval('supplier_application_protocol_seq')", nativeQuery = true)
    long nextProtocolNumber();
}
