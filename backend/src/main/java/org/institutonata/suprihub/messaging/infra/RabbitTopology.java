package org.institutonata.suprihub.messaging.infra;

import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topologia do RabbitMQ:
 * <pre>
 *  suprihub.events (topic) ──auth.# / supplier.# / tender.# / judgment.#──▶ suprihub.notifications
 *                                                                              │ (rejeitada após retries)
 *  suprihub.events.dlx (topic) ◀───────────────────────────────────────────────┘
 *        └──notifications──▶ suprihub.notifications.dlq
 * </pre>
 */
@Configuration
public class RabbitTopology {

    public static final String DLX = "suprihub.events.dlx";
    public static final String NOTIFICATIONS_QUEUE = "suprihub.notifications";
    public static final String NOTIFICATIONS_DLQ = "suprihub.notifications.dlq";

    @Bean
    public Declarables suprihubTopology(SupriHubProperties props) {
        TopicExchange events = new TopicExchange(props.messaging().exchange(), true, false);
        TopicExchange dlx = new TopicExchange(DLX, true, false);

        Queue notifications = QueueBuilder.durable(NOTIFICATIONS_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey("notifications")
                .build();
        Queue notificationsDlq = QueueBuilder.durable(NOTIFICATIONS_DLQ).build();

        return new Declarables(events, dlx, notifications, notificationsDlq,
                bind(notifications, events, "auth.#"),
                bind(notifications, events, "supplier.#"),
                bind(notifications, events, "tender.#"),
                bind(notifications, events, "judgment.#"),
                bind(notificationsDlq, dlx, "notifications"));
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
