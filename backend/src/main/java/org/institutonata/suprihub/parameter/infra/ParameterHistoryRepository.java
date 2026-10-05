package org.institutonata.suprihub.parameter.infra;

import java.util.List;

import org.institutonata.suprihub.parameter.domain.ParameterHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterHistoryRepository extends JpaRepository<ParameterHistory, Long> {

    List<ParameterHistory> findByKeyOrderByChangedAtDesc(String key);
}
