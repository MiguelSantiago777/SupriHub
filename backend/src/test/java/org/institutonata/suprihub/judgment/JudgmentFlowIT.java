package org.institutonata.suprihub.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import jakarta.mail.internet.MimeMessage;

import com.jayway.jsonpath.JsonPath;
import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.auth.infra.RoleRepository;
import org.institutonata.suprihub.catalog.infra.MunicipalityRepository;
import org.institutonata.suprihub.judgment.application.JudgmentService;
import org.institutonata.suprihub.messaging.application.OutboxRelay;
import org.institutonata.suprihub.support.Auth;
import org.institutonata.suprihub.support.IntegrationTest;
import org.institutonata.suprihub.support.Mails;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Julgamento das Faixas 2 e 3: mapa, direito de cobrir, homologação e ata (Fase 2, parte B). */
class JudgmentFlowIT extends IntegrationTest {

    static final String PASSWORD = "Senha2026";
    static final byte[] PDF = "%PDF-1.4\n% doc\n".getBytes(StandardCharsets.US_ASCII);
    static final String MESQUITA = "3302858";
    static final String NOVA_IGUACU = "3303500";
    static final String CAMPOS = "3301009";

    @Autowired
    AppUserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    OutboxRelay relay;
    @Autowired
    Clock clock;
    @Autowired
    MunicipalityRepository municipalities;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JudgmentService judgments;

    record Supplier(String email, String token, long applicationId, String name) {
    }

    String createUser(AppRole role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@teste.local";
        tx.executeWithoutResult(s -> {
            AppUser u = new AppUser(email, encoder.encode(PASSWORD), "Pessoa " + role.name(), clock.instant());
            u.replaceRoles(roles.findByCodeIn(Set.of(role)), clock.instant());
            u.confirmEmail(clock.instant());
            users.save(u);
        });
        return email;
    }

    static long num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    long municipality(String ibge) {
        return municipalities.findByIbgeCode(ibge).orElseThrow().getId();
    }

    Supplier accredited(String commission, SupplierType type, String ibge, boolean womanLed) throws Exception {
        String email = createUser(AppRole.SUPPLIER);
        String token = Auth.bearer(mvc, email, PASSWORD);
        String name = type + " " + UUID.randomUUID().toString().substring(0, 6);
        String json = mvc.perform(get("/api/supplier/application").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/supplier/application").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"supplierType":"%s","legalName":"%s","documentNumber":"11222333000181","street":"Rua A",
                         "addressNumber":"1","district":"Centro","municipalityId":%d,"cep":"26553000",
                         "phone":"21999990001","contactEmail":"%s","representativeName":"Titular",
                         "representativeCpf":"52998224725","lineCodes":["L01"],"linesDescription":"Lanches",
                         "territories":["T1","T2","T3"],"bankName":"Caixa","bankBranch":"1","bankAccount":"2",
                         "accountHolder":"%s","womanLed":%s,"cadUnico":false,"declarationsAccepted":true,
                         "version":%d}""".formatted(type, name, municipality(ibge), email, name, womanLed,
                        num(json, "$.version"))))
                .andExpect(status().isOk());
        for (DocumentType doc : DocumentType.values()) {
            if (doc.requirementFor(type) == DocumentType.Requirement.REQUIRED) {
                mvc.perform(multipart("/api/supplier/application/documents/" + doc.name())
                        .file(new MockMultipartFile("file", "d.pdf", "application/pdf", PDF))
                        .header("Authorization", token)).andExpect(status().isCreated());
            }
        }
        json = mvc.perform(get("/api/supplier/application").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        json = mvc.perform(post("/api/supplier/application/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(json, "$.version") + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = num(json, "$.id");
        mvc.perform(post("/api/commission/applications/" + id + "/approve").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(json, "$.version") + "}"))
                .andExpect(status().isOk());
        return new Supplier(email, token, id, name);
    }

    /** Publica (prazo bem à frente), recebe as propostas e encerra o prazo. */
    long tenderWithProposals(String commission, String estimated, Object[][] bids) throws Exception {
        String json = mvc.perform(post("/api/tenders").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"200 kits lanche — Baile da Memória 60+","description":"Kits lanche",
                                 "lineCode":"L01","executionMunicipalityId":%d,"estimatedValue":%s,
                                 "proposalDeadline":null,"territories":["T1","T2","T3"],
                                 "items":[{"description":"Kit lanche","unit":"un","quantity":200}],
                                 "version":0}""".formatted(municipality(MESQUITA), estimated)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = num(json, "$.id");
        jdbc.update("UPDATE tender SET proposal_deadline = now() + interval '20 days' WHERE id = ?", id);
        json = mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/tenders/" + id + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(json, "$.version") + "}"))
                .andExpect(status().isOk());
        for (Object[] b : bids) {
            Supplier s = (Supplier) b[0];
            mvc.perform(multipart("/api/supplier/opportunities/" + id + "/proposal")
                            .file(new MockMultipartFile("data", "", "application/json", """
                                    {"mode":"OWN_FILE","totalValue":%s,"validityDays":30,"declarationAccepted":true}"""
                                    .formatted(b[1]).getBytes()))
                            .file(new MockMultipartFile("file", "p.pdf", "application/pdf", PDF))
                            .header("Authorization", s.token()))
                    .andExpect(status().isCreated());
        }
        jdbc.update("UPDATE tender SET proposal_deadline = now() - interval '1 minute' WHERE id = ?", id);
        return id;
    }

    MimeMessage awaitMail(String to, String subjectFragment) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            relay.relayBatch();
            return sentMails.stream()
                    .filter(m -> Mails.to(m).equalsIgnoreCase(to) && Mails.subject(m).contains(subjectFragment))
                    .findFirst().orElse(null);
        }, m -> m != null);
    }

    /** Manual 4.2: A (EPP, T2) 3.900; B (MEI, mulher, T1) 4.200; C (ME, T3) 3.850. */
    @Test
    void exemploDoManualComDireitoDeCobrir() throws Exception {
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        Supplier a = accredited(commission, SupplierType.EPP, NOVA_IGUACU, false);
        Supplier b = accredited(commission, SupplierType.MEI, MESQUITA, true);
        Supplier c = accredited(commission, SupplierType.ME, CAMPOS, false);
        long id = tenderWithProposals(commission, "20000", new Object[][] {{a, 3900}, {b, 4200}, {c, 3850}});
        String base = "/api/tenders/" + id + "/judgment";

        mvc.perform(get(base).header("Authorization", commission))
                .andExpect(jsonPath("$.canStart").value(true))
                .andExpect(jsonPath("$.minValidRequired").value(3))
                .andExpect(jsonPath("$.judgment").doesNotExist());
        // Fornecedor não acessa o julgamento da Comissão
        mvc.perform(get(base).header("Authorization", a.token())).andExpect(status().isForbidden());

        String panel = mvc.perform(post(base + "/start").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.judgment.status").value("AWAITING_COVER"))
                .andExpect(jsonPath("$.judgment.lowestValue").value(3850.0))
                .andExpect(jsonPath("$.judgment.entries[0].applicationId").value(b.applicationId()))
                .andExpect(jsonPath("$.judgment.entries[0].territory").value("T1"))
                .andExpect(jsonPath("$.judgment.entries[0].fictitiousTie").value(true))
                .andExpect(jsonPath("$.judgment.entries[0].mustCover").value(true))
                .andExpect(jsonPath("$.judgment.entries[1].applicationId").value(a.applicationId()))
                .andExpect(jsonPath("$.judgment.entries[2].applicationId").value(c.applicationId()))
                .andExpect(jsonPath("$.judgment.covers.length()").value(1))
                .andExpect(jsonPath("$.judgment.covers[0].status").value("OFFERED"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andExpect(jsonPath("$.status").value("JUDGING"));
        // Julgamento em curso: não se julga de novo nem se desclassifica
        mvc.perform(post(base + "/start").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        // B foi convocada a cobrir; os demais não veem convocação nem dados alheios
        awaitMail(b.email(), "Você pode cobrir o menor preço");
        String mine = "/api/supplier/opportunities/" + id + "/judgment";
        mvc.perform(get(mine).header("Authorization", b.token()))
                .andExpect(jsonPath("$.judging").value(true))
                .andExpect(jsonPath("$.cover.ceiling").value(3850.0))
                .andExpect(jsonPath("$.cover.canRespond").value(true))
                .andExpect(jsonPath("$.cover.strictlyLower").value(true))
                .andExpect(jsonPath("$.result").doesNotExist());
        String other = mvc.perform(get(mine).header("Authorization", c.token()))
                .andExpect(jsonPath("$.cover").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(other).doesNotContain("blackWomanOrCadUnico").doesNotContain(b.name());
        // Igual ao menor preço não cobre (Edital 8.2 — "valor inferior")
        mvc.perform(post(mine + "/cover").header("Authorization", b.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":3850}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COVER_TOO_HIGH"));
        mvc.perform(post(mine + "/cover").header("Authorization", a.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":3000}"))
                .andExpect(status().isNotFound());
        // B recusa → A (T2) é convocada e cobre
        mvc.perform(post(mine + "/cover/decline").header("Authorization", b.token()))
                .andExpect(jsonPath("$.cover.status").value("DECLINED"));
        mvc.perform(post(mine + "/cover").header("Authorization", a.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":3800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover.status").value("COVERED"));

        panel = mvc.perform(get(base).header("Authorization", commission))
                .andExpect(jsonPath("$.judgment.status").value("DECIDED"))
                .andExpect(jsonPath("$.judgment.awardedValue").value(3800.0))
                .andExpect(jsonPath("$.judgment.winnerProposalId").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(panel, "$.judgment.winnerReason"))
                .contains("Empate ficto").contains("preferência local").contains("3.800,00");
        long judgmentId = num(panel, "$.judgment.id");

        // Minuta da ata disponível antes da homologação
        byte[] draft = mvc.perform(get("/api/judgments/" + judgmentId + "/ata").header("Authorization", commission))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(draft, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Diretoria não homologa Faixa 2
        String board = Auth.bearer(mvc, createUser(AppRole.BOARD), PASSWORD);
        mvc.perform(post("/api/board/judgments/" + judgmentId + "/approve").header("Authorization", board)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + num(panel, "$.judgment.version") + "}"))
                .andExpect(status().isForbidden());

        mvc.perform(post(base + "/homologate").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Conferido.\",\"version\":" + num(panel, "$.judgment.version") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgment.status").value("HOMOLOGATED"))
                .andExpect(jsonPath("$.judgment.ataSha256").isNotEmpty());
        mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andExpect(jsonPath("$.status").value("AWARDED"));
        byte[] ata = mvc.perform(get("/api/judgments/" + judgmentId + "/ata").header("Authorization", commission))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(ata).isNotEqualTo(draft);
        assertThat(new String(ata, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Resultado aos proponentes, sem dados de equidade de ninguém
        mvc.perform(get(mine).header("Authorization", a.token()))
                .andExpect(jsonPath("$.result.winner").value(true))
                .andExpect(jsonPath("$.result.awardedValue").value(3800.0));
        mvc.perform(get(mine).header("Authorization", c.token()))
                .andExpect(jsonPath("$.result.winner").value(false))
                .andExpect(jsonPath("$.result.myPosition").value(3))
                .andExpect(jsonPath("$.result.reason").value("empate ficto com preferência local, após cobrir o menor preço"));
        awaitMail(a.email(), "Sua proposta foi selecionada");
        String html = Mails.html(awaitMail(c.email(), "Resultado da oportunidade"));
        assertThat(html).contains("3.800,00").contains("3º lugar");

        // O mapa é imutável
        assertThatThrownBy(() -> jdbc.update("UPDATE judgment_entry SET total_value = 1 WHERE judgment_id = ?",
                judgmentId)).hasMessageContaining("imut");
    }

    @Test
    void faixa2AbaixoDoMinimoExigeJustificativaEDesclassificacao() throws Exception {
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        Supplier a = accredited(commission, SupplierType.MEI, MESQUITA, false);
        Supplier b = accredited(commission, SupplierType.MEI, MESQUITA, false);
        long id = tenderWithProposals(commission, "20000", new Object[][] {{a, 5000}, {b, 4000}});
        String base = "/api/tenders/" + id + "/judgment";

        String detail = mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        long cheaper = num(detail, "$.proposals[0].id");
        mvc.perform(post(base + "/proposals/" + cheaper + "/disqualify").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Item fora da especificação\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/start").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BELOW_MINIMUM_PROPOSALS"));
        String panel = mvc.perform(post(base + "/start").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"Só dois credenciados na linha e território.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgment.status").value("DECIDED"))
                .andExpect(jsonPath("$.judgment.validCount").value(1))
                .andExpect(jsonPath("$.judgment.awardedValue").value(5000.0))
                .andExpect(jsonPath("$.judgment.winnerReason").value("Menor preço."))
                .andExpect(jsonPath("$.judgment.entries[1].disqualifiedReason").value("Item fora da especificação"))
                .andExpect(jsonPath("$.judgment.entries[1].position").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Anular libera a desclassificação e um novo julgamento
        mvc.perform(post(base + "/proposals/" + cheaper + "/requalify").header("Authorization", commission))
                .andExpect(status().isConflict());
        mvc.perform(post(base + "/annul").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Desclassificação revista\",\"version\":"
                                + num(panel, "$.judgment.version") + "}"))
                .andExpect(jsonPath("$.judgment.status").value("ANNULLED"))
                .andExpect(jsonPath("$.canStart").value(true));
        mvc.perform(post(base + "/proposals/" + cheaper + "/requalify").header("Authorization", commission))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/start").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"justification\":\"Idem.\"}"))
                .andExpect(jsonPath("$.judgment.awardedValue").value(4000.0))
                .andExpect(jsonPath("$.judgment.history.length()").value(1));
    }

    @Test
    void convocacaoVencidaPassaAProxima() throws Exception {
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        Supplier local = accredited(commission, SupplierType.MEI, MESQUITA, false);
        Supplier far = accredited(commission, SupplierType.MEI, CAMPOS, false);
        long id = tenderWithProposals(commission, "20000", new Object[][] {{local, 1050}, {far, 1000}});
        mvc.perform(post("/api/tenders/" + id + "/judgment/start").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"justification\":\"Teste\"}"))
                .andExpect(jsonPath("$.judgment.status").value("AWAITING_COVER"));
        jdbc.update("UPDATE cover_offer SET due_date = current_date - 1 WHERE status = 'OFFERED' AND judgment_id IN "
                + "(SELECT id FROM judgment WHERE tender_id = ?)", id);
        assertThat(judgments.expireOverdue()).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/tenders/" + id + "/judgment").header("Authorization", commission))
                .andExpect(jsonPath("$.judgment.status").value("DECIDED"))
                .andExpect(jsonPath("$.judgment.covers[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.judgment.awardedValue").value(1000.0));
    }

    @Test
    void faixa3AprovadaPelaDiretoria() throws Exception {
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        String board = Auth.bearer(mvc, createUser(AppRole.BOARD), PASSWORD);
        Supplier a = accredited(commission, SupplierType.MEI, MESQUITA, true);
        long id = tenderWithProposals(commission, "100000", new Object[][] {{a, 90000}});
        String panel = mvc.perform(post("/api/tenders/" + id + "/judgment/start").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.judgment.status").value("DECIDED"))
                .andExpect(jsonPath("$.judgment.approvalByBoard").value(true))
                .andReturn().getResponse().getContentAsString();
        long judgmentId = num(panel, "$.judgment.id");
        String version = "{\"version\":" + num(panel, "$.judgment.version") + "}";
        mvc.perform(post("/api/tenders/" + id + "/judgment/homologate").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content(version))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_APPROVER"));
        mvc.perform(get("/api/board/judgments").header("Authorization", commission)).andExpect(status().isForbidden());
        mvc.perform(get("/api/board/judgments").header("Authorization", board))
                .andExpect(jsonPath("$[?(@.judgmentId == " + judgmentId + ")].tenderId").value(org.hamcrest.Matchers
                        .contains((int) id)));
        mvc.perform(post("/api/board/judgments/" + judgmentId + "/approve").header("Authorization", board)
                        .contentType(MediaType.APPLICATION_JSON).content(version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgment.status").value("HOMOLOGATED"));
        mvc.perform(get("/api/judgments/" + judgmentId + "/ata").header("Authorization", board))
                .andExpect(status().isOk());
        mvc.perform(get("/api/judgments/" + judgmentId + "/ata").header("Authorization", a.token()))
                .andExpect(status().isForbidden());
    }
}
