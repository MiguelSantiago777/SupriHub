package org.institutonata.suprihub.messaging.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.institutonata.suprihub.messaging.domain.DomainEvent;
import org.institutonata.suprihub.messaging.domain.EventPayloadGuard;
import org.institutonata.suprihub.messaging.domain.OutboxEvent;
import org.institutonata.suprihub.messaging.infra.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponto único para publicar eventos de domínio. Grava no outbox <strong>na mesma
 * transação</strong> da mudança de negócio — exige transação ativa ({@code MANDATORY}).
 * O {@link OutboxRelay} entrega ao RabbitMQ depois do commit.
 */
@Service
public class DomainEvents {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public DomainEvents(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        EventPayloadGuard.check(event.type(), event.payload());
        try {
            repository.save(new OutboxEvent(event, objectMapper.writeValueAsString(event.payload())));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload de evento não serializável: " + event.type(), e);
        }
    }
}
