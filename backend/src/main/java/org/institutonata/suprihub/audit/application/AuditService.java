package org.institutonata.suprihub.audit.application;

import java.time.Clock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.institutonata.suprihub.audit.domain.AuditLog;
import org.institutonata.suprihub.audit.infra.AuditLogRepository;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trilha de auditoria: quem, quando, o quê (antes/depois). Chamado explicitamente pelos
 * services, na mesma transação da mudança. Os snapshots devem ser DTOs de auditoria
 * (records) — nunca entidades JPA, para não serializar relacionamentos nem dados de senha.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String entityType, Object entityId, String action, Object before, Object after) {
        record(entityType, entityId, action, before, after, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String entityType, Object entityId, String action, Object before, Object after,
            String details) {
        repository.save(new AuditLog(entityType, String.valueOf(entityId), action,
                CurrentUser.id().orElse(null), CurrentUser.label(), clock.instant(),
                toJson(before), toJson(after), details));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String entityType, String entityId, Pageable pageable) {
        if (entityType != null && entityId != null) {
            return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId, pageable);
        }
        if (entityType != null) {
            return repository.findByEntityTypeOrderByOccurredAtDesc(entityType, pageable);
        }
        return repository.findAllByOrderByOccurredAtDesc(pageable);
    }

    private String toJson(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Snapshot de auditoria não serializável", e);
        }
    }
}
