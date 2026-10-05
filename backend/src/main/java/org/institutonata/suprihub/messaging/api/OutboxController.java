package org.institutonata.suprihub.messaging.api;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.messaging.application.OutboxMaintenance;
import org.institutonata.suprihub.messaging.domain.OutboxEvent;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Mensageria (outbox)")
public class OutboxController {

    private final OutboxMaintenance maintenance;

    public OutboxController(OutboxMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    public record OutboxEventDto(UUID id, String eventType, String aggregateType, String aggregateId,
            OutboxEvent.Status status, int attempts, Instant occurredAt, Instant nextAttemptAt, Instant publishedAt,
            String lastError) {
        static OutboxEventDto of(OutboxEvent e) {
            return new OutboxEventDto(e.getId(), e.getEventType(), e.getAggregateType(), e.getAggregateId(),
                    e.getStatus(), e.getAttempts(), e.getOccurredAt(), e.getNextAttemptAt(), e.getPublishedAt(),
                    e.getLastError());
        }
    }

    @GetMapping("/api/admin/outbox")
    @Operation(summary = "Lista eventos do outbox por status")
    public PageResponse<OutboxEventDto> list(@RequestParam(defaultValue = "FAILED") OutboxEvent.Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(maintenance.list(status,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "occurredAt"))),
                OutboxEventDto::of);
    }

    @PostMapping("/api/admin/outbox/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reenfileira um evento que falhou")
    public void retry(@PathVariable UUID id) {
        maintenance.retry(id);
    }
}
