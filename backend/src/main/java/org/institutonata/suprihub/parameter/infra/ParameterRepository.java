package org.institutonata.suprihub.parameter.infra;

import java.util.List;

import org.institutonata.suprihub.parameter.domain.Parameter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterRepository extends JpaRepository<Parameter, String> {

    List<Parameter> findAllByOrderByCategoryAscKeyAsc();
}
