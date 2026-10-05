package org.institutonata.suprihub.audit.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.audit.domain.AuditLog;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auditoria")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    public record AuditLogDto(Long id, String entityType, String entityId, String action, Long actorId,
            String actorLabel, Instant occurredAt, String before, String after, String details) {
        static AuditLogDto of(AuditLog a) {
            return new AuditLogDto(a.getId(), a.getEntityType(), a.getEntityId(), a.getAction(), a.getActorId(),
                    a.getActorLabel(), a.getOccurredAt(), a.getBeforeJson(), a.getAfterJson(), a.getDetails());
        }
    }

    @GetMapping("/api/admin/audit")
    @PreAuthorize("hasAnyRole('ADMIN','INTERNAL_CONTROL')")
    @Operation(summary = "Consulta a trilha de auditoria (somente leitura)")
    public PageResponse<AuditLogDto> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(audit.search(entityType, entityId, PageRequest.of(page, Math.min(size, 100))),
                AuditLogDto::of);
    }
}
