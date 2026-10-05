package org.institutonata.suprihub.notification.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.institutonata.suprihub.notification.domain.MessageChannel;
import org.institutonata.suprihub.notification.domain.MessageChannel.ChannelType;
import org.institutonata.suprihub.notification.domain.MessageChannel.OutgoingMessage;
import org.institutonata.suprihub.notification.domain.NotificationRecord;
import org.institutonata.suprihub.notification.infra.NotificationRecordRepository;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renderiza templates (Thymeleaf, pt-BR) e entrega pelo canal. Registra o resultado em
 * {@code notification}. Falha de envio é relançada para o consumidor reprocessar.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final Map<ChannelType, MessageChannel> channels = new HashMap<>();
    private final TemplateEngine templateEngine;
    private final NotificationRecordRepository records;
    private final SupriHubProperties props;
    private final Clock clock;

    public NotificationService(List<MessageChannel> channelBeans, TemplateEngine templateEngine,
            NotificationRecordRepository records, SupriHubProperties props, Clock clock) {
        channelBeans.forEach(c -> channels.put(c.type(), c));
        this.templateEngine = templateEngine;
        this.records = records;
        this.props = props;
        this.clock = clock;
    }

    public record Recipient(Long userId, String address) {
        @Override
        public String toString() {
            return "Recipient[user=" + userId + "]";
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void send(ChannelType channelType, Recipient recipient, String template, String subject,
            Map<String, Object> variables, UUID sourceEvent) {
        MessageChannel channel = channels.get(channelType);
        if (channel == null || !channel.isEnabled()) {
            records.save(new NotificationRecord(recipient.userId(), channelType, template, subject,
                    NotificationRecord.Status.SKIPPED, "Canal indisponível", sourceEvent, clock.instant()));
            return;
        }
        Context ctx = new Context(PT_BR);
        ctx.setVariables(variables);
        ctx.setVariable("subject", subject);
        ctx.setVariable("frontendUrl", props.frontendUrl());
        String html = templateEngine.process("mail/" + template, ctx);
        String text = htmlToText(html);
        try {
            channel.send(new OutgoingMessage(recipient.address(), subject, html, text));
            records.save(new NotificationRecord(recipient.userId(), channelType, template, subject,
                    NotificationRecord.Status.SENT, null, sourceEvent, clock.instant()));
        } catch (RuntimeException e) {
            log.warn("Falha ao enviar notificação '{}' ao usuário #{}: {}", template, recipient.userId(),
                    e.getMessage());
            throw e;
        }
    }

    private static String htmlToText(String html) {
        return html.replaceAll("(?is)<(style|head)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>|</p>|</h[1-6]>|</li>", "\n")
                .replaceAll("(?i)<a [^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "$2: $1")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .trim();
    }
}
