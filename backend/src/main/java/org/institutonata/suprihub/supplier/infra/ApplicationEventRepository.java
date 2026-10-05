package org.institutonata.suprihub.supplier.infra;

import java.util.List;

import org.institutonata.suprihub.supplier.domain.ApplicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {

    List<ApplicationEvent> findByApplicationIdOrderByOccurredAt(Long applicationId);
}
