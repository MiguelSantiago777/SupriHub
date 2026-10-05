package org.institutonata.suprihub.audit.infra;

import org.institutonata.suprihub.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId,
            Pageable pageable);

    Page<AuditLog> findByEntityTypeOrderByOccurredAtDesc(String entityType, Pageable pageable);

    Page<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
