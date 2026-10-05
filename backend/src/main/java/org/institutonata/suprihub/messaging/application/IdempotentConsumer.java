package org.institutonata.suprihub.messaging.application;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante processamento único por (consumidor, mensagem). Deve ser chamado dentro da
 * transação do consumidor: se o processamento falhar, o registro é revertido junto e a
 * mensagem pode ser reprocessada.
 */
@Component
public class IdempotentConsumer {

    private final JdbcTemplate jdbc;

    public IdempotentConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code true} se é a primeira vez; {@code false} se já foi processada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, UUID messageId) {
        int inserted = jdbc.update("""
                INSERT INTO processed_message (consumer, message_id) VALUES (?, ?)
                ON CONFLICT DO NOTHING""", consumer, messageId);
        return inserted == 1;
    }
}
