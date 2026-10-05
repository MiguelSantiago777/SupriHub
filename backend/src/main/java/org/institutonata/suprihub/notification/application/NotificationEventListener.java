package org.institutonata.suprihub.notification.application;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.institutonata.suprihub.auth.application.VerificationTokenService;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.domain.AuthEvents;
import org.institutonata.suprihub.auth.domain.VerificationToken;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.messaging.application.IdempotentConsumer;
import org.institutonata.suprihub.messaging.domain.EventEnvelope;
import org.institutonata.suprihub.messaging.infra.RabbitTopology;
import org.institutonata.suprihub.notification.application.NotificationService.Recipient;
import org.institutonata.suprihub.notification.domain.MessageChannel.ChannelType;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consome eventos de domínio e dispara as notificações correspondentes. Idempotente:
 * cada mensagem é processada uma única vez por este consumidor.
 */
@Component
public class NotificationEventListener {

    static final String CONSUMER = "notifications";
    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final IdempotentConsumer idempotency;
    private final NotificationService notifications;
    private final AppUserRepository users;
    private final VerificationTokenService tokens;
    private final String frontendUrl;

    public NotificationEventListener(IdempotentConsumer idempotency, NotificationService notifications,
            AppUserRepository users, VerificationTokenService tokens, SupriHubProperties props) {
        this.idempotency = idempotency;
        this.notifications = notifications;
        this.users = users;
        this.tokens = tokens;
        this.frontendUrl = props.frontendUrl();
    }

    @RabbitListener(queues = RabbitTopology.NOTIFICATIONS_QUEUE)
    @Transactional
    public void onEvent(EventEnvelope event) {
        if (!idempotency.firstTime(CONSUMER, event.id())) {
            log.debug("Evento {} já processado — ignorado", event.id());
            return;
        }
        switch (event.type()) {
            case AuthEvents.USER_REGISTERED, AuthEvents.EMAIL_CONFIRMATION_REQUESTED -> withUser(event, user -> {
                String token = tokens.issue(user.getId(), VerificationToken.Purpose.EMAIL_CONFIRMATION,
                        VerificationTokenService.EMAIL_CONFIRMATION_TTL);
                email(user, "email-confirmation", "Confirme seu e-mail — Contrata + NATA", event,
                        Map.of("link", frontendUrl + "/confirmar-email?token=" + token));
            });
            case AuthEvents.REGISTRATION_DUPLICATE -> withUser(event, user ->
                    email(user, "registration-duplicate", "Você já tem cadastro no SupriHub", event,
                            Map.of("loginLink", frontendUrl + "/entrar",
                                    "resetLink", frontendUrl + "/esqueci-senha")));
            case AuthEvents.PASSWORD_RESET_REQUESTED -> withUser(event, user -> {
                String token = tokens.issue(user.getId(), VerificationToken.Purpose.PASSWORD_RESET,
                        VerificationTokenService.PASSWORD_RESET_TTL);
                email(user, "password-reset", "Criar nova senha — SupriHub", event,
                        Map.of("link", frontendUrl + "/nova-senha?token=" + token));
            });
            case AuthEvents.USER_INVITED -> withUser(event, user -> {
                String token = tokens.issue(user.getId(), VerificationToken.Purpose.PASSWORD_RESET,
                        VerificationTokenService.INVITATION_TTL);
                email(user, "user-invited", "Seu acesso ao SupriHub", event,
                        Map.of("link", frontendUrl + "/nova-senha?token=" + token));
            });
            default -> log.debug("Evento {} sem notificação associada", event.type());
        }
    }

    private void withUser(EventEnvelope event, Consumer<AppUser> action) {
        Long userId = event.payloadLong("userId");
        users.findById(userId).filter(AppUser::isEnabled).ifPresentOrElse(action,
                () -> log.info("Usuário #{} inexistente ou inativo — notificação {} ignorada", userId, event.type()));
    }

    private void email(AppUser user, String template, String subject, EventEnvelope event,
            Map<String, Object> vars) {
        Map<String, Object> all = new HashMap<>(vars);
        all.put("name", user.getFullName());
        notifications.send(ChannelType.EMAIL, new Recipient(user.getId(), user.getEmail()), template, subject, all,
                event.id());
    }
}
