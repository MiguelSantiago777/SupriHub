package org.institutonata.suprihub.messaging.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.institutonata.suprihub.messaging.domain.OutboxEvent;
import org.institutonata.suprihub.messaging.infra.OutboxEventRepository;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrega os eventos do outbox ao RabbitMQ (at-least-once).
 *
 * <p>Cada ciclo trava um lote com {@code FOR UPDATE SKIP LOCKED}, publica com
 * <em>publisher confirms</em> e marca como publicado na mesma transação. Se a
 * transação falhar após publicar, o evento pode ser reentregue — por isso os
 * consumidores são idempotentes ({@code processed_message}).
 */
@Component
@ConditionalOnProperty(prefix = "suprihub.messaging", name = "relay-enabled", havingValue = "true",
        matchIfMissing = true)
public class OutboxRelay implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SupriHubProperties.Messaging config;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
            TransactionTemplate tx, Clock clock, SupriHubProperties props, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.tx = tx;
        this.clock = clock;
        this.config = props.messaging();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::relaySafely, config.relayInterval());
    }

    private void relaySafely() {
        try {
            int published;
            do {
                published = relayBatch();
            } while (published == config.relayBatchSize());
        } catch (RuntimeException e) {
            log.warn("Ciclo do outbox relay falhou: {}", e.getMessage());
        }
    }

    /** Processa um lote; devolve quantos eventos foram tratados. Público para testes. */
    public int relayBatch() {
        Integer handled = tx.execute(status -> {
            Instant now = clock.instant();
            List<OutboxEvent> batch = repository.lockNextBatch(now, config.relayBatchSize());
            for (OutboxEvent event : batch) {
                publish(event, now);
            }
            return batch.size();
        });
        return handled == null ? 0 : handled;
    }

    private void publish(OutboxEvent event, Instant now) {
        try {
            CorrelationData correlation = new CorrelationData(event.getId().toString());
            rabbitTemplate.send(config.exchange(), event.getEventType(), toMessage(event), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("Broker recusou a mensagem: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                log.warn("Evento {} ({}) sem fila de destino — publicado sem consumidores",
                        event.getId(), event.getEventType());
            }
            event.markPublished(now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markFailedAttempt("Interrompido", now, config.maxAttempts());
        } catch (Exception e) {
            event.markFailedAttempt(e.getClass().getSimpleName() + ": " + e.getMessage(), now, config.maxAttempts());
            log.warn("Falha ao publicar evento {} ({}), tentativa {}: {}", event.getId(),
                    event.getEventType(), event.getAttempts(), e.getMessage());
        }
    }

    private Message toMessage(OutboxEvent event) {
        byte[] body;
        try {
            ObjectNode envelope = objectMapper.createObjectNode()
                    .put("id", event.getId().toString())
                    .put("type", event.getEventType())
                    .put("aggregateType", event.getAggregateType())
                    .put("aggregateId", event.getAggregateId())
                    .put("occurredAt", event.getOccurredAt().toString());
            envelope.set("payload", objectMapper.readTree(event.getPayload()));
            body = objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload inválido no outbox", e);
        }
        return MessageBuilder.withBody(body)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setType(event.getEventType())
                .setTimestamp(Date.from(event.getOccurredAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("x-aggregate-type", event.getAggregateType())
                .setHeader("x-aggregate-id", event.getAggregateId())
                .build();
    }
}
