package org.institutonata.suprihub.contract.infra;

import java.util.List;

import org.institutonata.suprihub.contract.domain.PriceBankEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceBankRepository extends JpaRepository<PriceBankEntry, Long> {

    List<PriceBankEntry> findByServiceOrderId(Long serviceOrderId);
}
