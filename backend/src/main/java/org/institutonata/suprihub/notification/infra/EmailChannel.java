package org.institutonata.suprihub.notification.infra;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.institutonata.suprihub.notification.domain.MessageChannel;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailChannel implements MessageChannel {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailChannel(JavaMailSender mailSender, SupriHubProperties props) {
        this.mailSender = mailSender;
        this.from = props.mail().from();
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(OutgoingMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mime);
        } catch (MessagingException e) {
            throw new IllegalStateException("Falha ao montar e-mail", e);
        }
    }
}
