package org.institutonata.suprihub.catalog.infra;

import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.catalog.domain.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipalityRepository extends JpaRepository<Municipality, Integer> {

    Optional<Municipality> findByIbgeCode(String ibgeCode);

    List<Municipality> findByUfOrderByName(String uf);
}
