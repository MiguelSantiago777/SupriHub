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
import org.institutonata.suprihub.supplier.domain.DocumentType;
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
import org.institutonata.suprihub.sanction.domain.SanctionEvents;
import org.institutonata.suprihub.sanction.domain.SanctionProcess;
import org.institutonata.suprihub.sanction.infra.SanctionProcessRepository;
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
    private final SanctionProcessRepository sanctionProcesses;
    private final Clock clock;
    private final String frontendUrl;

    public NotificationEventListener(IdempotentConsumer idempotency, NotificationService notifications,
            AppUserRepository users, VerificationTokenService tokens, SupplierApplicationRepository applications,
            AppealRepository appeals, TenderRepository tenders, TenderInvitationRepository invitations,
            ProposalRepository proposals, EligibleSuppliers eligibleSuppliers, JudgmentRepository judgments,
            JudgmentEntryRepository judgmentEntries, CoverOfferRepository coverOffers, JudgmentService judgmentService,
            ServiceOrderRepository serviceOrders, SanctionProcessRepository sanctionProcesses, Clock clock,
            SupriHubProperties props) {
        this.serviceOrders = serviceOrders;
        this.sanctionProcesses = sanctionProcesses;
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
                email(user, "appeal-filed", "Recebemos seu recurso — " + appealRef(app, appeal), event,
                        Map.of("protocol", appealRef(app, appeal), "dueDate", date(appeal.getReconsiderationDue()),
                                "link", frontendUrl + supplierAppealPath(appeal)));
                notifyRole(AppRole.COMMISSION, event, "appeal-received", "Recurso a analisar: "
                                + appealRef(app, appeal),
                        Map.of("protocol", appeal.getKind().label() + " — " + appealRef(app, appeal),
                                "supplierName", nz(app.getLegalName()),
                                "stage", "Comissão (reconsiderar ou encaminhar à Diretoria)",
                                "dueDate", date(appeal.getReconsiderationDue()),
                                "link", frontendUrl + "/recursos/" + appeal.getId()));
            });
            case SupplierEvents.APPEAL_FORWARDED -> withAppeal(event, (user, app, appeal) -> {
                email(user, "appeal-forwarded", "Seu recurso foi encaminhado à Diretoria", event,
                        Map.of("protocol", appealRef(app, appeal), "dueDate", date(appeal.getBoardDue()),
                                "link", frontendUrl + supplierAppealPath(appeal)));
                notifyRole(AppRole.BOARD, event, "appeal-received", "Recurso para decidir: " + appealRef(app, appeal),
                        Map.of("protocol", appeal.getKind().label() + " — " + appealRef(app, appeal),
                                "supplierName", nz(app.getLegalName()),
                                "stage", "Diretoria (decisão final)", "dueDate", date(appeal.getBoardDue()),
                                "link", frontendUrl + "/recursos/" + appeal.getId()));
            });
            case SupplierEvents.APPEAL_GRANTED -> withAppeal(event, (user, app, appeal) -> {
                if (appeal.getKind() == Appeal.Kind.JUDGMENT) {
                    email(user, "notice", "Recurso provido — " + appeal.getSubject(), event, notice(
                            "O seu recurso contra o resultado de " + appeal.getSubject() + " foi provido.",
                            "O julgamento foi reaberto e a Comissão vai julgar de novo. Você recebe o novo "
                                    + "resultado por e-mail.",
                            "Ver a oportunidade", supplierAppealPath(appeal)));
                    notifyRole(AppRole.COMMISSION, event, "notice", "Julgamento reaberto — " + appeal.getSubject(),
                            notice("Recurso provido contra o resultado de " + appeal.getSubject()
                                            + ". O julgamento foi anulado e a ordem ainda não executada, cancelada.",
                                    "Corrija o que motivou o recurso e julgue de novo.", "Abrir a oportunidade",
                                    "/comissao/editais/" + appeal.getTenderId()));
                } else {
                    email(user, "appeal-granted", "Recurso aceito: você está credenciado", event,
                            Map.of("protocol", app.getProtocol(), "reason", nz(app.getDecisionReason()),
                                    "validUntil", date(app.getAccreditationValidUntil()),
                                    "link", frontendUrl + "/fornecedor"));
                }
            });
            case SupplierEvents.APPEAL_DENIED -> withAppeal(event, (user, app, appeal) ->
                    email(user, "appeal-denied", "Decisão do seu recurso — " + appealRef(app, appeal), event,
                            Map.of("protocol", appealRef(app, appeal), "reason", nz(appeal.getBoardReason()),
                                    "link", frontendUrl + supplierAppealPath(appeal))));
            case SupplierEvents.CERTIFICATE_EXPIRING -> withApplication(event, (user, app) ->
                    email(user, "notice", "Certidão perto do vencimento — renove", event, notice(
                            "Sua certidão vence em breve: " + certificates(event) + ".",
                            "Emita a nova certidão no site do órgão e envie pela sua área, com a data de validade. "
                                    + "Assim você continua recebendo oportunidades.",
                            "Enviar a certidão", "/fornecedor")));
            case SupplierEvents.CERTIFICATE_EXPIRED -> withApplication(event, (user, app) -> {
                email(user, "notice", "Certidão vencida: envie até " + date(app.getRegularizationDue()), event,
                        notice("Venceu (ou falta) a certidão: " + certificates(event) + ".",
                                "Envie a certidão atualizada até " + date(app.getRegularizationDue())
                                        + " (5 dias úteis). Sem ela, o seu cadastro fica inativo temporariamente "
                                        + "e deixa de receber oportunidades (Política art. 21, § 2º).",
                                "Enviar a certidão", "/fornecedor"));
                notifyRole(AppRole.COMMISSION, event, "notice", "Certidão vencida: " + nz(app.getLegalName()),
                        notice("O credenciado " + nz(app.getLegalName()) + " está com certidão vencida ou faltando: "
                                        + certificates(event) + ". Ele foi notificado para apresentar até "
                                        + date(app.getRegularizationDue()) + ".",
                                "Sempre que possível, renove por consulta direta no site oficial e anexe a certidão "
                                        + "na tela do credenciado (Política art. 21, § 2º).",
                                "Ver o credenciado", "/comissao/requerimentos/" + app.getId()));
            });
            case SupplierEvents.INACTIVATED -> withApplication(event, (user, app) -> {
                email(user, "notice", "Seu cadastro está inativo temporariamente", event, notice(
                        "A certidão " + certificates(event) + " não foi regularizada no prazo. O seu cadastro "
                                + "ficou inativo temporariamente e não recebe oportunidades (Política art. 22).",
                        "Envie a certidão atualizada pela sua área: a reativação é automática.",
                        "Regularizar", "/fornecedor"));
                notifyRole(AppRole.COMMISSION, event, "notice", "Credenciado inativado: " + nz(app.getLegalName()),
                        notice("O credenciado " + nz(app.getLegalName()) + " ficou inativo temporariamente: "
                                        + certificates(event) + " sem regularização no prazo.",
                                "Ele volta a ficar ativo assim que a certidão for regularizada.",
                                "Ver o credenciado", "/comissao/requerimentos/" + app.getId()));
            });
            case SupplierEvents.REACTIVATED -> withApplication(event, (user, app) ->
                    email(user, "notice", "Seu cadastro está ativo de novo", event, notice(
                            "As certidões foram regularizadas e o seu cadastro voltou a ficar ativo.",
                            "Você volta a receber as oportunidades das suas linhas e territórios.",
                            "Ver meu credenciamento", "/fornecedor")));
            case SupplierEvents.DEREGISTRATION_ALERT -> withApplication(event, (user, app) ->
                    notifyRole(AppRole.COMMISSION, event, "notice",
                            "Irregular há 30 dias: " + nz(app.getLegalName()), notice(
                                    "O credenciado " + nz(app.getLegalName()) + " está com certidão irregular desde "
                                            + date(app.getIrregularSince()) + ".",
                                    "Pela Política (art. 23), a perda de habilitação não regularizada em 30 dias leva "
                                            + "ao descredenciamento, por decisão motivada da Comissão.",
                                    "Ver o credenciado", "/comissao/requerimentos/" + app.getId())));
            case SupplierEvents.REGISTRATION_UPDATED -> withApplication(event, (user, app) -> {
                String changes = nz(event.payloadString("changes"));
                Map<String, Object> vars = notice(nz(app.getLegalName()) + " atualizou o cadastro: " + changes + ".",
                        "Confira as alterações na tela do credenciado.", "Ver o credenciado",
                        "/comissao/requerimentos/" + app.getId());
                notifyRole(AppRole.COMMISSION, event, "notice", "Cadastro atualizado: " + nz(app.getLegalName()),
                        vars);
                if (changes.contains("Dados bancários")) {
                    notifyRole(AppRole.FINANCE, event, "notice",
                            "Conta bancária alterada: " + nz(app.getLegalName()), vars);
                }
            });
            case SupplierEvents.RENEWAL_DUE -> withApplication(event, (user, app) ->
                    email(user, "notice", "Renove o seu credenciamento", event, notice(
                            "O seu credenciamento vale até " + date(app.getAccreditationValidUntil()) + ".",
                            "Atualize as certidões e os dados e peça a renovação pela sua área "
                                    + "(Política art. 22).",
                            "Renovar", "/fornecedor")));
            case SupplierEvents.RENEWAL_REQUESTED -> withApplication(event, (user, app) ->
                    notifyRole(AppRole.COMMISSION, event, "notice", "Renovação para analisar: "
                            + nz(app.getLegalName()), notice(
                                    nz(app.getLegalName()) + " pediu a renovação do credenciamento (vale até "
                                            + date(app.getAccreditationValidUntil()) + ").",
                                    "Confira as certidões e os dados e renove ou devolva com o que falta.",
                                    "Analisar", "/comissao/requerimentos/" + app.getId())));
            case SupplierEvents.RENEWED -> withApplication(event, (user, app) ->
                    email(user, "notice", "Credenciamento renovado", event, notice(
                            "O seu credenciamento foi renovado até " + date(app.getAccreditationValidUntil()) + ".",
                            "Mantenha as certidões, o contato e a conta em dia.", "Ver meu credenciamento",
                            "/fornecedor")));
            case SupplierEvents.RENEWAL_RETURNED -> withApplication(event, (user, app) ->
                    email(user, "notice", "Renovação: a Comissão pediu ajustes", event, notice(
                            "A Comissão devolveu o seu pedido de renovação: " + nz(app.getRenewalReturnMessage()),
                            "Faça os ajustes pela sua área e peça a renovação de novo.", "Ver meu credenciamento",
                            "/fornecedor")));
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
            case TenderEvents.URGENT_CONTRACTED -> tenders.findById(event.payloadLong("tenderId")).ifPresent(t ->
                    notifyRole(AppRole.COMMISSION, event, "notice", "Contratação de urgência: " + t.getNumber(),
                            notice("Foi feita uma contratação de urgência (" + t.getTitle() + ", "
                                            + money(t.getEstimatedValue()) + "). Justificativa: "
                                            + t.getUrgencyJustification(),
                                    "Tome ciência até " + date(t.getUrgencyAckDue()) + " (Política, art. 13, § 3º). "
                                            + "Urgência não pode virar rotina: acompanhe a frequência por projeto.",
                                    "Ver a contratação", "/contratos?urgencias=1")));
            case TenderEvents.ROTATION_DISPLACED -> tenders.findById(event.payloadLong("tenderId")).ifPresent(t -> {
                boolean recidivism = "true".equals(event.payloadString("recidivism"));
                Long applicationId = event.payloadLong("applicationId");
                toApplication(applicationId, event, "Você foi para o final do rodízio", notice(
                        "Você recusou sem motivo (ou não respondeu) duas oportunidades seguidas do rodízio, a "
                                + "última sendo " + t.getNumber() + ". Por isso, passou para o final da fila "
                                + "(Política, art. 13, § 4º).",
                        "Se não puder atender, recuse o convite informando a indisponibilidade: isso afasta a "
                                + "infração.",
                        "Ver oportunidades", "/fornecedor/editais"));
                applications.findById(applicationId).ifPresent(app -> notifyRole(AppRole.COMMISSION, event, "notice",
                        "Deslocado no rodízio: " + nz(app.getLegalName()), notice(
                                nz(app.getLegalName()) + " foi para o final do rodízio por duas recusas "
                                        + "injustificadas seguidas (última: " + t.getNumber() + ").",
                                recidivism ? "É reincidência: pelo Anexo XII, cabe advertência em processo "
                                        + "sancionador." : "É a primeira vez: o deslocamento basta (Anexo XII).",
                                "Ver o credenciado", "/comissao/requerimentos/" + app.getId())));
            });
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
                            "Reemita com novo prazo ou convoque o próximo classificado (Edital 9.1).", "Ver a ordem",
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
            case ContractEvents.UPFRONT_PAID -> withOrder(event, (o, t) -> toSupplier(o, event, "notice",
                    "Sinal pago — " + o.getNumber(), notice(
                            "O Instituto pagou " + money(o.getUpfrontPaidValue()) + " de sinal referente à ordem "
                                    + o.getNumber() + " em " + date(o.getUpfrontPaidOn()) + ".",
                            "O saldo (" + money(o.remainingValue()) + ") é pago depois do recebimento definitivo e "
                                    + "da nota fiscal do valor total.",
                            "Ver a ordem", "/fornecedor/contratos/" + o.getId())));
            case ContractEvents.LOW_RATING_ALERT -> withOrder(event, (o, t) -> notifyRole(AppRole.COMMISSION, event,
                    "notice", "Avaliações insuficientes — fornecedor da ordem " + o.getNumber(), notice(
                            "O fornecedor da ordem " + o.getNumber() + " teve avaliações abaixo de 3 em duas "
                                    + "contratações nos últimos 12 meses.",
                            "Pelo Edital 9.5, cabe abrir processo para suspensão do cadastro.", "Ver a ordem",
                            "/contratos/" + o.getId())));
            case SanctionEvents.OPENED -> withSanction(event, p -> toApplication(p.getApplicationId(), event,
                    "Notificação: processo " + p.getNumber(), notice(
                            "O Instituto abriu o processo " + p.getNumber() + " sobre: " + p.getInfraction().label()
                                    + ". Fatos: " + p.getFacts(),
                            "Você tem até " + date(p.getDefenseDue()) + " (5 dias úteis) para apresentar defesa."
                                    + (p.isInterimBlock()
                                            ? " Por risco iminente, o seu cadastro fica inativo até a decisão."
                                            : ""),
                            "Apresentar defesa", "/fornecedor/sancoes/" + p.getId())));
            case SanctionEvents.DEFENDED -> withSanction(event, p -> notifyRole(AppRole.COMMISSION, event, "notice",
                    "Defesa recebida — " + p.getNumber(), notice(
                            "O fornecedor apresentou defesa no processo " + p.getNumber() + ".",
                            "Decida de forma motivada até " + date(p.getDecisionDue()) + ".", "Ver o processo",
                            "/sancoes/" + p.getId())));
            case SanctionEvents.DECIDED -> withSanction(event, p -> toApplication(p.getApplicationId(), event,
                    "Decisão do processo " + p.getNumber(), notice(
                            "A Comissão decidiu o processo " + p.getNumber() + ": " + p.getSanction().label() + ".",
                            p.getSanction().name().equals("NONE") ? "O processo será arquivado."
                                    : "Você pode recorrer à Diretoria até " + date(p.getAppealDue())
                                            + " (5 dias úteis).",
                            "Ver a decisão", "/fornecedor/sancoes/" + p.getId())));
            case SanctionEvents.APPEALED -> withSanction(event, p -> notifyRole(AppRole.BOARD, event, "notice",
                    "Recurso em processo sancionador — " + p.getNumber(), notice(
                            "O fornecedor recorreu da decisão da Comissão no processo " + p.getNumber() + ".",
                            "Decisão final da Diretoria até " + date(p.getBoardDue()) + ".", "Ver o recurso",
                            "/sancoes/" + p.getId())));
            case SanctionEvents.FINAL -> withSanction(event, p -> toApplication(p.getApplicationId(), event,
                    "Processo encerrado — " + p.getNumber(), notice(
                            "O processo " + p.getNumber() + " foi encerrado: " + p.getFinalSanction().label() + ".",
                            p.getEffectiveUntil() == null ? ""
                                    : "O seu cadastro fica sem receber oportunidades até "
                                            + date(p.getEffectiveUntil()) + ".",
                            "Ver o processo", "/fornecedor/sancoes/" + p.getId())));
            default -> log.debug("Evento {} sem notificação associada", event.type());
        }
    }

    private void withOrder(EventEnvelope event, BiConsumer<ServiceOrder, Tender> action) {
        serviceOrders.findById(event.payloadLong("serviceOrderId"))
                .ifPresent(o -> tenders.findById(o.getTenderId()).ifPresent(t -> action.accept(o, t)));
    }

    private void withSanction(EventEnvelope event, Consumer<SanctionProcess> action) {
        sanctionProcesses.findById(event.payloadLong("processId")).ifPresent(action);
    }

    private void toApplication(Long applicationId, EventEnvelope event, String subject, Map<String, Object> vars) {
        applications.findById(applicationId)
                .flatMap(app -> users.findById(app.getUserId()))
                .filter(AppUser::isEnabled)
                .ifPresent(user -> email(user, "notice", subject, event, vars));
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

    /** Como o recurso aparece nos e-mails: protocolo do requerimento ou a oportunidade recorrida. */
    private static String appealRef(SupplierApplication app, Appeal appeal) {
        return appeal.getKind() == Appeal.Kind.JUDGMENT ? nz(appeal.getSubject()) : nz(app.getProtocol());
    }

    private static String supplierAppealPath(Appeal appeal) {
        return appeal.getKind() == Appeal.Kind.JUDGMENT ? "/fornecedor/editais/" + appeal.getTenderId()
                : "/fornecedor";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Nomes das certidões do evento (tipos separados por vírgula no payload). */
    private static String certificates(EventEnvelope event) {
        String names = event.payloadString("certificates");
        if (names == null || names.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(names.split(","))
                .map(n -> DocumentType.valueOf(n.trim()).label())
                .collect(java.util.stream.Collectors.joining("; "));
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
