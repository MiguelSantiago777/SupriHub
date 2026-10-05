package org.institutonata.suprihub.catalog.infra;

import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyLineRepository extends JpaRepository<SupplyLine, Short> {

    List<SupplyLine> findAllByOrderByDisplayOrder();

    List<SupplyLine> findByActiveTrueOrderByDisplayOrder();

    Optional<SupplyLine> findByCode(String code);
}
