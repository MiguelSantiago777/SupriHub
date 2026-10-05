package org.institutonata.suprihub.notification.application;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.institutonata.suprihub.auth.application.VerificationTokenService;
import org.institutonata.suprihub.auth.domain.AppRole;
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
import org.institutonata.suprihub.supplier.domain.Appeal;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.domain.SupplierEvents;
import org.institutonata.suprihub.proposal.infra.ProposalRepository;
import org.institutonata.suprihub.supplier.infra.AppealRepository;
import org.institutonata.suprihub.tender.application.EligibleSuppliers;
import org.institutonata.suprihub.tender.domain.TenderEvents;
import org.institutonata.suprihub.tender.infra.TenderInvitationRepository;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.Pageable;
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
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final IdempotentConsumer idempotency;
    private final NotificationService notifications;
    private final AppUserRepository users;
    private final VerificationTokenService tokens;
    private final SupplierApplicationRepository applications;
    private final AppealRepository appeals;
    private final TenderRepository tenders;
    private final TenderInvitationRepository invitations;
    private final ProposalRepository proposals;
    private final EligibleSuppliers eligibleSuppliers;
    private final Clock clock;
    private final String frontendUrl;

    public NotificationEventListener(IdempotentConsumer idempotency, NotificationService notifications,
            AppUserRepository users, VerificationTokenService tokens, SupplierApplicationRepository applications,
            AppealRepository appeals, TenderRepository tenders, TenderInvitationRepository invitations,
            ProposalRepository proposals, EligibleSuppliers eligibleSuppliers, Clock clock,
            SupriHubProperties props) {
        this.idempotency = idempotency;
        this.notifications = notifications;
        this.users = users;
        this.tokens = tokens;
        this.applications = applications;
        this.appeals = appeals;
        this.tenders = tenders;
        this.invitations = invitations;
        this.proposals = proposals;
        this.eligibleSuppliers = eligibleSuppliers;
        this.clock = clock;
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
            case SupplierEvents.APPLICATION_SUBMITTED -> withApplication(event, (user, app) -> {
                email(user, "application-submitted", "Recebemos seu requerimento " + app.getProtocol(), event,
                        Map.of("protocol", app.getProtocol(), "dueDate", date(app.getAnalysisDueDate()),
                                "link", frontendUrl + "/fornecedor"));
                notifyCommission(event, app);
            });
            case SupplierEvents.ADJUSTMENT_REQUESTED -> withApplication(event, (user, app) ->
                    email(user, "application-adjustment", "Ajuste no seu requerimento " + app.getProtocol(), event,
                            Map.of("protocol", app.getProtocol(), "message", app.getAdjustmentMessage(),
                                    "dueDate", date(app.getAdjustmentDueDate()),
                                    "link", frontendUrl + "/fornecedor")));
            case SupplierEvents.APPLICATION_APPROVED -> withApplication(event, (user, app) ->
                    email(user, "application-approved", "Você está credenciado no Contrata + NATA", event,
                            Map.of("protocol", app.getProtocol(), "validUntil", date(app.getAccreditationValidUntil()),
                                    "link", frontendUrl + "/fornecedor")));
            case SupplierEvents.APPLICATION_REJECTED -> withApplication(event, (user, app) ->
                    email(user, "application-rejected", "Resultado do seu requerimento " + app.getProtocol(), event,
                            Map.of("protocol", app.getProtocol(), "reason", app.getDecisionReason(),
                                    "link", frontendUrl + "/fornecedor")));
            case SupplierEvents.APPEAL_FILED -> withAppeal(event, (user, app, appeal) -> {
                email(user, "appeal-filed", "Recebemos seu recurso — " + app.getProtocol(), event,
                        Map.of("protocol", app.getProtocol(), "dueDate", date(appeal.getReconsiderationDue()),
                                "link", frontendUrl + "/fornecedor"));
                notifyRole(AppRole.COMMISSION, event, "appeal-received", "Recurso a analisar: " + app.getProtocol(),
                        Map.of("protocol", app.getProtocol(), "supplierName", nz(app.getLegalName()),
                                "stage", "Comissão (reconsiderar ou encaminhar à Diretoria)",
                                "dueDate", date(appeal.getReconsiderationDue()),
                                "link", frontendUrl + "/recursos/" + appeal.getId()));
            });
            case SupplierEvents.APPEAL_FORWARDED -> withAppeal(event, (user, app, appeal) -> {
                email(user, "appeal-forwarded", "Seu recurso foi encaminhado à Diretoria", event,
                        Map.of("protocol", app.getProtocol(), "dueDate", date(appeal.getBoardDue()),
                                "link", frontendUrl + "/fornecedor"));
                notifyRole(AppRole.BOARD, event, "appeal-received", "Recurso para decidir: " + app.getProtocol(),
                        Map.of("protocol", app.getProtocol(), "supplierName", nz(app.getLegalName()),
                                "stage", "Diretoria (decisão final)", "dueDate", date(appeal.getBoardDue()),
                                "link", frontendUrl + "/recursos/" + appeal.getId()));
            });
            case SupplierEvents.APPEAL_GRANTED -> withAppeal(event, (user, app, appeal) ->
                    email(user, "appeal-granted", "Recurso aceito: você está credenciado", event,
                            Map.of("protocol", app.getProtocol(), "reason", nz(app.getDecisionReason()),
                                    "validUntil", date(app.getAccreditationValidUntil()),
                                    "link", frontendUrl + "/fornecedor")));
            case SupplierEvents.APPEAL_DENIED -> withAppeal(event, (user, app, appeal) ->
                    email(user, "appeal-denied", "Decisão do seu recurso — " + app.getProtocol(), event,
                            Map.of("protocol", app.getProtocol(), "reason", nz(appeal.getBoardReason()),
                                    "link", frontendUrl + "/fornecedor")));
            case TenderEvents.PUBLISHED -> tenders.findById(event.payloadLong("tenderId")).ifPresent(t -> {
                Map<String, Object> vars = Map.of("number", t.getNumber(), "title", t.getTitle(),
                        "line", t.getSupplyLine().getCode() + " · " + t.getSupplyLine().getShortName(),
                        "band", t.getBand().label() + " — " + t.getBand().selectionMode(),
                        "deadline", dateTime(t.getProposalDeadline()),
                        "link", frontendUrl + "/fornecedor/editais/" + t.getId());
                for (SupplierApplication app : eligibleSuppliers.of(t, LocalDate.now(clock))) {
                    users.findById(app.getUserId()).filter(AppUser::isEnabled).ifPresent(user ->
                            email(user, "opportunity-published", "Nova oportunidade: " + t.getTitle(), event, vars));
                }
            });
            case TenderEvents.INVITATION_CREATED -> invitations.findById(event.payloadLong("invitationId"))
                    .ifPresent(inv -> tenders.findById(inv.getTenderId()).ifPresent(t ->
                            applications.findById(inv.getApplicationId())
                                    .flatMap(app -> users.findById(app.getUserId()))
                                    .filter(AppUser::isEnabled)
                                    .ifPresent(user -> email(user, "opportunity-invitation",
                                            "Você foi convidado: " + t.getTitle(), event,
                                            Map.of("number", t.getNumber(), "title", t.getTitle(),
                                                    "dueDate", date(inv.getDueDate()),
                                                    "link", frontendUrl + "/fornecedor/editais/" + t.getId())))));
            case TenderEvents.PROPOSAL_SUBMITTED -> proposals.findById(event.payloadLong("proposalId"))
                    .ifPresent(pr -> tenders.findById(pr.getTenderId()).ifPresent(t ->
                            applications.findById(pr.getApplicationId())
                                    .flatMap(app -> users.findById(app.getUserId()))
                                    .filter(AppUser::isEnabled)
                                    .ifPresent(user -> email(user, "proposal-received",
                                            "Recebemos sua proposta — " + t.getNumber(), event,
                                            Map.of("number", t.getNumber(), "title", t.getTitle(),
                                                    "total", money(pr.getTotalValue()),
                                                    "link", frontendUrl + "/fornecedor/editais/" + t.getId())))));
            default -> log.debug("Evento {} sem notificação associada", event.type());
        }
    }

    @FunctionalInterface
    private interface AppealAction {
        void accept(AppUser user, SupplierApplication app, Appeal appeal);
    }

    private void withAppeal(EventEnvelope event, AppealAction action) {
        Long appealId = event.payloadLong("appealId");
        appeals.findById(appealId).ifPresentOrElse(
                appeal -> withApplication(event, (user, app) -> action.accept(user, app, appeal)),
                () -> log.info("Recurso #{} inexistente — notificação {} ignorada", appealId, event.type()));
    }

    private void notifyRole(AppRole role, EventEnvelope event, String template, String subject,
            Map<String, Object> vars) {
        users.searchByRole(role, "", Pageable.unpaged()).stream()
                .filter(AppUser::isEnabled)
                .forEach(member -> email(member, template, subject, event, vars));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void withApplication(EventEnvelope event, BiConsumer<AppUser, SupplierApplication> action) {
        Long applicationId = event.payloadLong("applicationId");
        applications.findById(applicationId).ifPresentOrElse(app -> withUser(event, user -> action.accept(user, app)),
                () -> log.info("Requerimento #{} inexistente — notificação {} ignorada", applicationId, event.type()));
    }

    /** Avisa cada membro ativo da Comissão de que há requerimento novo na fila. */
    private void notifyCommission(EventEnvelope event, SupplierApplication app) {
        Map<String, Object> vars = Map.of("protocol", app.getProtocol(),
                "supplierName", app.getLegalName() == null ? "" : app.getLegalName(),
                "dueDate", date(app.getAnalysisDueDate()),
                "link", frontendUrl + "/comissao/requerimentos/" + app.getId());
        users.searchByRole(AppRole.COMMISSION, "", Pageable.unpaged()).stream()
                .filter(AppUser::isEnabled)
                .forEach(member -> email(member, "application-received-commission",
                        "Novo requerimento na fila: " + app.getProtocol(), event, vars));
    }

    private String dateTime(Instant i) {
        return i == null ? "" : DATE_TIME.format(i.atZone(clock.getZone()));
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v);
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
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
