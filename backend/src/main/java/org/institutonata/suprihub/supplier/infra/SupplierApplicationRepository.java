package org.institutonata.suprihub.supplier.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.catalog.domain.Territory;
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

    /** Lista pública: habilitados com credenciamento vigente, por linha, território e nome. */
    @Query("""
            SELECT DISTINCT a FROM SupplierApplication a LEFT JOIN a.lines l
            WHERE a.status = :status
              AND a.accreditationValidUntil >= :today
              AND (:line = '' OR l.code = :line)
              AND (:territory IS NULL OR :territory MEMBER OF a.territories)
              AND (:q = ''
                   OR lower(coalesce(a.legalName, '')) LIKE lower(concat('%', :q, '%'))
                   OR lower(coalesce(a.tradeName, '')) LIKE lower(concat('%', :q, '%')))
            """)
    Page<SupplierApplication> searchPublic(@Param("status") ApplicationStatus status, @Param("today") LocalDate today,
            @Param("line") String line, @Param("territory") Territory territory, @Param("q") String q,
            Pageable pageable);

    @Query("SELECT a.status, COUNT(a) FROM SupplierApplication a GROUP BY a.status")
    List<Object[]> countByStatus();

    @Query(value = "SELECT nextval('supplier_application_protocol_seq')", nativeQuery = true)
    long nextProtocolNumber();
}
