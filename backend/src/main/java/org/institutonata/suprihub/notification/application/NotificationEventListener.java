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
import org.institutonata.suprihub.judgment.application.JudgmentService;
import org.institutonata.suprihub.judgment.domain.JudgmentEvents;
import org.institutonata.suprihub.judgment.infra.CoverOfferRepository;
import org.institutonata.suprihub.judgment.infra.JudgmentEntryRepository;
import org.institutonata.suprihub.judgment.infra.JudgmentRepository;
import org.institutonata.suprihub.judgment.model.JudgmentEntry;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.contract.domain.ContractEvents;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.contract.infra.ServiceOrderRepository;
import org.institutonata.suprihub.tender.domain.Tender;
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
    private final JudgmentRepository judgments;
    private final JudgmentEntryRepository judgmentEntries;
    private final CoverOfferRepository coverOffers;
    private final JudgmentService judgmentService;
    private final ServiceOrderRepository serviceOrders;
    private final Clock clock;
    private final String frontendUrl;

    public NotificationEventListener(IdempotentConsumer idempotency, NotificationService notifications,
            AppUserRepository users, VerificationTokenService tokens, SupplierApplicationRepository applications,
            AppealRepository appeals, TenderRepository tenders, TenderInvitationRepository invitations,
            ProposalRepository proposals, EligibleSuppliers eligibleSuppliers, JudgmentRepository judgments,
            JudgmentEntryRepository judgmentEntries, CoverOfferRepository coverOffers, JudgmentService judgmentService,
            ServiceOrderRepository serviceOrders, Clock clock, SupriHubProperties props) {
        this.serviceOrders = serviceOrders;
        this.judgments = judgments;
        this.judgmentEntries = judgmentEntries;
        this.coverOffers = coverOffers;
        this.judgmentService = judgmentService;
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
            case JudgmentEvents.COVER_OFFERED -> coverOffers.findById(event.payloadLong("coverOfferId"))
                    .ifPresent(offer -> judgments.findById(offer.getJudgmentId())
                            .flatMap(j -> tenders.findById(j.getTenderId()).map(t -> Map.entry(j, t)))
                            .ifPresent(jt -> applications.findById(offer.getApplicationId())
                                    .flatMap(app -> users.findById(app.getUserId()))
                                    .filter(AppUser::isEnabled)
                                    .ifPresent(user -> email(user, "cover-offered",
                                            "Você pode cobrir o menor preço — " + jt.getValue().getNumber(), event,
                                            Map.of("number", jt.getValue().getNumber(),
                                                    "title", jt.getValue().getTitle(),
                                                    "ceiling", money(offer.getCeiling()),
                                                    "rule", jt.getKey().rules().coverStrictlyLower()
                                                            ? "menor que" : "igual ou menor que",
                                                    "dueDate", date(offer.getDueDate()),
                                                    "link", frontendUrl + "/fornecedor/editais/"
                                                            + jt.getValue().getId())))));
            case JudgmentEvents.DECIDED -> judgments.findById(event.payloadLong("judgmentId"))
                    .ifPresent(j -> tenders.findById(j.getTenderId()).ifPresent(t -> {
                        boolean board = t.getBand() == Band.FAIXA_3;
                        String winner = judgmentEntries.findById(new JudgmentEntry.Key(j.getId(),
                                j.getWinnerProposalId())).map(JudgmentEntry::getSupplierName).orElse("");
                        Map<String, Object> vars = Map.of("number", t.getNumber(), "title", t.getTitle(),
                                "winner", winner, "value", money(j.getAwardedValue()),
                                "action", board ? "Na Faixa 3 a ata precisa da aprovação da Diretoria."
                                        : "Confira o mapa e homologue o resultado.",
                                "link", frontendUrl + (board ? "/diretoria/atas/" + j.getId()
                                        : "/comissao/editais/" + t.getId()));
                        notifyRole(board ? AppRole.BOARD : AppRole.COMMISSION, event, "judgment-decided",
                                (board ? "Ata para aprovar: " : "Resultado para homologar: ") + t.getNumber(), vars);
                    }));
            case JudgmentEvents.HOMOLOGATED -> judgments.findById(event.payloadLong("judgmentId"))
                    .ifPresent(j -> tenders.findById(j.getTenderId()).ifPresent(t -> {
                        for (JudgmentEntry e : judgmentEntries.findByJudgment(j.getId())) {
                            boolean winner = e.getProposalId().equals(j.getWinnerProposalId());
                            String reason = judgmentService.forSupplier(t.getId(), e.getApplicationId())
                                    .map(v -> v.publicReason()).orElse("");
                            String position = e.isDisqualified()
                                    ? "Sua proposta foi desclassificada: " + e.getDisqualifiedReason()
                                    : "Sua proposta ficou em " + e.getPosition() + "º lugar.";
                            applications.findById(e.getApplicationId())
                                    .flatMap(app -> users.findById(app.getUserId()))
                                    .filter(AppUser::isEnabled)
                                    .ifPresent(user -> email(user, "judgment-result",
                                            (winner ? "Sua proposta foi selecionada — " : "Resultado da oportunidade ")
                                                    + t.getNumber(), event,
                                            Map.of("number", t.getNumber(), "title", t.getTitle(),
                                                    "winner", winner, "value", money(j.getAwardedValue()),
                                                    "reason", reason, "position", position,
                                                    "link", frontendUrl + "/fornecedor/editais/" + t.getId())));
                        }
                    }));
            case ContractEvents.CREATED -> withOrder(event, (o, t) -> notifyRole(AppRole.COMMISSION, event, "notice",
                    "Ordem de fornecimento para preparar — " + t.getNumber(), notice(
                            "A oportunidade " + t.getNumber() + " (" + t.getTitle() + ") foi adjudicada por "
                                    + money(o.getTotalValue()) + ". A ordem de fornecimento está em preparação.",
                            "Confira projeto, prazos e pagamento e emita a ordem.", "Preparar a ordem",
                            "/contratos/" + o.getId())));
            case ContractEvents.ISSUED -> withOrder(event, (o, t) -> toSupplier(o, event, "notice",
                    (o.isAcceptanceBased() ? "Ordem emitida — " : "Assine a ordem — ") + o.getNumber(), notice(
                            "O Instituto emitiu a " + o.getKind().label() + " " + o.getNumber() + " para "
                                    + t.getTitle() + ", no valor de " + money(o.getTotalValue()) + ".",
                            o.isAcceptanceBased()
                                    ? "Na Faixa 1 o seu aceite já vale como assinatura. Execute conforme a ordem."
                                    : "Leia e assine pela sua área até " + date(o.getSignatureDueDate())
                                            + " (2 dias úteis). Sem assinatura, o próximo pode ser convocado.",
                            "Ver a ordem", "/fornecedor/contratos/" + o.getId())));
            case ContractEvents.EXPIRED -> withOrder(event, (o, t) -> notifyRole(AppRole.COMMISSION, event, "notice",
                    "Ordem não assinada no prazo — " + o.getNumber(), notice(
                            "O fornecedor não assinou a ordem " + o.getNumber() + " (" + t.getTitle() + ") no prazo.",
                            "Reemita com novo prazo ou cancele e convoque o próximo (Edital 9.1).", "Ver a ordem",
                            "/contratos/" + o.getId())));
            case ContractEvents.RECEIVED -> withOrder(event, (o, t) -> toSupplier(o, event, "notice",
                    "Termo de recebimento — " + o.getNumber(), notice(
                            "O Instituto registrou o recebimento da ordem " + o.getNumber() + ": "
                                    + o.getStatus().label() + ".",
                            o.getStatus().name().equals("RECEIVED")
                                    ? "Veja a sua avaliação, dê ciência e envie a nota fiscal ou o RPA pela sua área."
                                    : "Veja o termo e a avaliação na sua área e dê ciência.",
                            "Ver o termo", "/fornecedor/contratos/" + o.getId())));
            case ContractEvents.INVOICED -> withOrder(event, (o, t) -> notifyRole(AppRole.FINANCE, event, "notice",
                    "Nota fiscal para pagar — " + o.getNumber(), notice(
                            "Chegou a nota fiscal/RPA da ordem " + o.getNumber() + " (" + money(o.getTotalValue())
                                    + ").",
                            "Pagar até " + date(o.getPaymentDueDate()) + ", conferindo de novo a regularidade.",
                            "Ver a ordem", "/contratos/" + o.getId())));
            case ContractEvents.PAID -> withOrder(event, (o, t) -> toSupplier(o, event, "notice",
                    "Pagamento feito — " + o.getNumber(), notice(
                            "O Instituto pagou " + money(o.getPaidValue()) + " referente à ordem " + o.getNumber()
                                    + " em " + date(o.getPaidOn()) + ".",
                            "O comprovante está na sua área. Obrigado!", "Ver o comprovante",
                            "/fornecedor/contratos/" + o.getId())));
            case ContractEvents.LOW_RATING_ALERT -> withOrder(event, (o, t) -> notifyRole(AppRole.COMMISSION, event,
                    "notice", "Avaliações insuficientes — fornecedor da ordem " + o.getNumber(), notice(
                            "O fornecedor da ordem " + o.getNumber() + " teve avaliações abaixo de 3 em duas "
                                    + "contratações nos últimos 12 meses.",
                            "Pelo Edital 9.5, cabe abrir processo para suspensão do cadastro.", "Ver a ordem",
                            "/contratos/" + o.getId())));
            default -> log.debug("Evento {} sem notificação associada", event.type());
        }
    }

    private void withOrder(EventEnvelope event, BiConsumer<ServiceOrder, Tender> action) {
        serviceOrders.findById(event.payloadLong("serviceOrderId"))
                .ifPresent(o -> tenders.findById(o.getTenderId()).ifPresent(t -> action.accept(o, t)));
    }

    private void toSupplier(ServiceOrder o, EventEnvelope event, String template, String subject,
            Map<String, Object> vars) {
        applications.findById(o.getApplicationId())
                .flatMap(app -> users.findById(app.getUserId()))
                .filter(AppUser::isEnabled)
                .ifPresent(user -> email(user, template, subject, event, vars));
    }

    private Map<String, Object> notice(String message, String detail, String button, String path) {
        return Map.of("message", message, "detail", detail, "button", button, "link", frontendUrl + path);
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
