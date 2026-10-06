package org.institutonata.suprihub.contract.infra;

import java.util.List;

import org.institutonata.suprihub.contract.domain.PriceBankEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceBankRepository extends JpaRepository<PriceBankEntry, Long> {

    List<PriceBankEntry> findByServiceOrderId(Long serviceOrderId);

    List<PriceBankEntry> findBySupplyLineIdAndItemDescriptionIgnoreCaseAndUnitIgnoreCase(Short supplyLineId,
            String itemDescription, String unit);

    /** Filtro da consulta: linha (opcional) e trecho da descrição. Mais recentes primeiro. */
    @Query("""
            SELECT e FROM PriceBankEntry e
            WHERE (:lineId IS NULL OR e.supplyLineId = :lineId)
              AND (:q = '' OR lower(e.itemDescription) LIKE lower(concat('%', :q, '%')))
            ORDER BY e.observedOn DESC, e.id DESC
            """)
    List<PriceBankEntry> search(@Param("lineId") Short lineId, @Param("q") String q, Pageable pageable);
}
