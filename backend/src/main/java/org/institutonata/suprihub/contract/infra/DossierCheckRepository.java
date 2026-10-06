package org.institutonata.suprihub.contract.infra;

import java.util.List;

import org.institutonata.suprihub.contract.domain.DossierCheck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DossierCheckRepository extends JpaRepository<DossierCheck, DossierCheck.Key> {

    List<DossierCheck> findByServiceOrderId(Long serviceOrderId);
}
