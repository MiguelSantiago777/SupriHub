package org.institutonata.suprihub.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.auth.infra.RoleRepository;
import org.institutonata.suprihub.catalog.infra.MunicipalityRepository;
import org.institutonata.suprihub.sanction.application.SanctionService;
import org.institutonata.suprihub.support.Auth;
import org.institutonata.suprihub.support.IntegrationTest;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Fase 3B: Banco de Preços, dossiê, indicadores e processo sancionador. */
class ControlFlowIT extends IntegrationTest {

    static final String PASSWORD = "Senha2026";
    static final byte[] PDF = "%PDF-1.4\n% doc\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    AppUserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    Clock clock;
    @Autowired
    MunicipalityRepository municipalities;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SanctionService sanctions;

    record Supplier(String token, long applicationId) {
    }

    String token(AppRole role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@teste.local";
        tx.executeWithoutResult(s -> {
            AppUser u = new AppUser(email, encoder.encode(PASSWORD), "Pessoa " + role.name(), clock.instant());
            u.replaceRoles(roles.findByCodeIn(Set.of(role)), clock.instant());
            u.confirmEmail(clock.instant());
            users.save(u);
        });
        return Auth.bearer(mvc, email, PASSWORD);
    }

    static long num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    long mesquita() {
        return municipalities.findByIbgeCode("3302858").orElseThrow().getId();
    }

    Supplier accredited(String commission, String line, boolean womanLed) throws Exception {
        String email = "supplier-" + UUID.randomUUID() + "@teste.local";
        tx.executeWithoutResult(s -> {
            AppUser u = new AppUser(email, encoder.encode(PASSWORD), "Titular", clock.instant());
            u.replaceRoles(roles.findByCodeIn(Set.of(AppRole.SUPPLIER)), clock.instant());
            u.confirmEmail(clock.instant());
            users.save(u);
        });
        String token = Auth.bearer(mvc, email, PASSWORD);
        String name = "MEI " + UUID.randomUUID().toString().substring(0, 6);
        String json = mvc.perform(get("/api/supplier/application").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/supplier/application").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"supplierType":"MEI","legalName":"%s","documentNumber":"11222333000181","street":"Rua A",
                         "addressNumber":"1","district":"Centro","municipalityId":%d,"cep":"26553000",
                         "phone":"21999990001","contactEmail":"%s","representativeName":"Titular",
                         "representativeCpf":"52998224725","lineCodes":["%s"],"linesDescription":"Itens",
                         "territories":["T1"],"bankName":"Caixa","bankBranch":"1","bankAccount":"2",
                         "accountHolder":"%s","womanLed":%s,"cadUnico":false,"declarationsAccepted":true,
                         "version":%d}""".formatted(name, mesquita(), email, line, name, womanLed,
                        num(json, "$.version"))))
                .andExpect(status().isOk());
        for (DocumentType doc : DocumentType.values()) {
            if (doc.requirementFor(SupplierType.MEI) == DocumentType.Requirement.REQUIRED) {
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
        return new Supplier(token, id);
    }

    String tender(String commission, String line, String estimated) throws Exception {
        String json = mvc.perform(post("/api/tenders").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Kits lanche","description":"Kits","lineCode":"%s",
                                 "executionMunicipalityId":%d,"estimatedValue":%s,"proposalDeadline":null,
                                 "territories":["T1"],"items":[{"description":"Kit lanche","unit":"un","quantity":200}],
                                 "version":0}""".formatted(line, mesquita(), estimated)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = num(json, "$.id");
        jdbc.update("UPDATE tender SET proposal_deadline = now() + interval '20 days' WHERE id = ?", id);
        json = mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        return mvc.perform(post("/api/tenders/" + id + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(json, "$.version") + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** Faixa 2 com uma proposta, julgada, homologada, emitida, assinada, recebida e paga. */
    long paidOrder(String commission, String manager, String finance, Supplier s, String line) throws Exception {
        String t = tender(commission, line, "20000");
        long tenderId = num(t, "$.id");
        mvc.perform(multipart("/api/supplier/opportunities/" + tenderId + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json", """
                                {"mode":"ONLINE","unitPrices":{"%d":20},"validityDays":30,"declarationAccepted":true}"""
                                .formatted(num(t, "$.items[0].id")).getBytes()))
                        .header("Authorization", s.token()))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE tender SET proposal_deadline = now() - interval '1 minute' WHERE id = ?", tenderId);
        String panel = mvc.perform(post("/api/tenders/" + tenderId + "/judgment/start")
                        .header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"Única proposta.\"}"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/tenders/" + tenderId + "/judgment/homologate").header("Authorization", commission)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + num(panel, "$.judgment.version") + "}")).andExpect(status().isOk());
        long id = num(mvc.perform(get("/api/contracts/by-tender/" + tenderId).header("Authorization", manager))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String base = "/api/contracts/" + id;
        String o = mvc.perform(get(base).header("Authorization", manager)).andReturn().getResponse()
                .getContentAsString();
        o = mvc.perform(put(base).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind":"SUPPLY","project":"Baile 60+","objectDescription":"Kits","paymentDays":20,
                         "penaltyPct":10,"version":%d}""".formatted(num(o, "$.version"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post(base + "/issue").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + num(o, "$.version") + "}")).andExpect(status().isOk());
        mvc.perform(post("/api/supplier/contracts/" + id + "/sign").header("Authorization", s.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"declarationAccepted\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/receipts").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind":"DEFINITIVE","executionDate":"%s","scoreDeadline":5,"scoreQuality":5,
                         "scoreDocumentation":5,"scoreService":4,"scoreSafety":5}""".formatted(LocalDate.now())))
                .andExpect(status().isOk());
        mvc.perform(multipart(base + "/files").file(new MockMultipartFile("file", "f.pdf", "application/pdf", PDF))
                .param("kind", "EVIDENCE").header("Authorization", manager)).andExpect(status().isCreated());
        mvc.perform(multipart("/api/supplier/contracts/" + id + "/invoice")
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"number\":\"NF 1\",\"date\":\"" + LocalDate.now() + "\",\"value\":4000}").getBytes()))
                .file(new MockMultipartFile("file", "nf.pdf", "application/pdf", PDF))
                .header("Authorization", s.token())).andExpect(status().isOk());
        o = mvc.perform(multipart(base + "/files").file(new MockMultipartFile("file", "p.pdf", "application/pdf", PDF))
                        .param("kind", "PAYMENT_PROOF").header("Authorization", finance))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post(base + "/payment").header("Authorization", finance).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidOn\":\"" + LocalDate.now() + "\",\"value\":4000,\"method\":\"PIX\",\"version\":"
                                + num(o, "$.version") + "}"))
                .andExpect(jsonPath("$.status").value("PAID"));
        return id;
    }

    @Test
    void bancoDePrecosDossieEIndicadores() throws Exception {
        String commission = token(AppRole.COMMISSION);
        String manager = token(AppRole.DEMAND_MANAGER);
        String finance = token(AppRole.FINANCE);
        String control = token(AppRole.INTERNAL_CONTROL);
        Supplier s = accredited(commission, "L11", true);
        long orderId = paidOrder(commission, manager, finance, s, "L11");

        // Banco de Preços: 4.000 ÷ 200 = 20,00; teto = média + 10%
        mvc.perform(get("/api/price-bank").param("line", "L11").header("Authorization", manager))
                .andExpect(jsonPath("$[0].description").value("Kit lanche"))
                .andExpect(jsonPath("$[0].average").value(20.0))
                .andExpect(jsonPath("$[0].ceiling").value(22.0));
        mvc.perform(post("/api/price-bank").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineCode":"L11","description":"kit lanche","unit":"un","unitPrice":24,
                                 "source":"CONTRACT","observedOn":"%s"}""".formatted(LocalDate.now())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/price-bank").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineCode":"L11","description":"kit lanche","unit":"un","unitPrice":24,
                                 "source":"QUOTATION","reference":"Cotação loja X","observedOn":"%s"}"""
                                .formatted(LocalDate.now())))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/price-bank").param("line", "L11").header("Authorization", manager))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].average").value(22.0));
        // Conferência do rodízio: teto por item e total
        String f1 = tender(commission, "L11", "5000");
        mvc.perform(get("/api/tenders/" + num(f1, "$.id")).header("Authorization", commission))
                .andExpect(jsonPath("$.priceReferences[0].ceiling").value(24.2))
                .andExpect(jsonPath("$.ceilingTotal").value(4840.0));

        // Dossiê: só faltam os itens manuais (DFD com atesto e estimativa)
        String d = "/api/dossiers/" + orderId;
        mvc.perform(get(d).header("Authorization", control))
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.pending").value(2));
        mvc.perform(post(d + "/items/12").header("Authorization", finance).contentType(MediaType.APPLICATION_JSON)
                .content("{\"checked\":true}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post(d + "/items/1").header("Authorization", finance).contentType(MediaType.APPLICATION_JSON)
                .content("{\"checked\":true,\"note\":\"Atesto de 01/10\"}")).andExpect(status().isOk());
        mvc.perform(post(d + "/items/2").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checked\":true}"))
                .andExpect(jsonPath("$.complete").value(true));
        mvc.perform(post(d + "/review").header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(d + "/review").header("Authorization", control).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Amostra do semestre\"}"))
                .andExpect(jsonPath("$.reviews[0].complete").value(true));
        mvc.perform(get("/api/dossiers/sample").header("Authorization", control))
                .andExpect(jsonPath("$.minimum").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.rows[?(@.orderId == " + orderId + ")].complete").value(
                        org.hamcrest.Matchers.contains(true)));

        // Indicadores do semestre
        String report = mvc.perform(get("/api/indicators").header("Authorization", control))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contracts").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<String>>read(report, "$.indicators[*].code"))
                .contains("SMALL", "WOMEN", "T1", "DAYS_F2", "RATING", "URGENCY", "DOSSIERS");
        mvc.perform(get("/api/indicators").header("Authorization", s.token())).andExpect(status().isForbidden());
    }

    @Test
    void processoSancionadorComRecursoEBloqueio() throws Exception {
        String commission = token(AppRole.COMMISSION);
        String board = token(AppRole.BOARD);
        Supplier s = accredited(commission, "L12", false);

        // 1º processo: inativação cautelar, defesa, decisão, recurso reformado pela Diretoria
        String p = mvc.perform(post("/api/sanctions").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":%d,"infraction":"DATA_OR_SAFETY",
                                 "facts":"Equipe sem identificação no evento 60+.","interimBlock":true}"""
                                .formatted(s.applicationId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DEFENSE"))
                .andReturn().getResponse().getContentAsString();
        long id = num(p, "$.id");
        assertThat(JsonPath.<String>read(p, "$.number")).matches("PS-\\d{4}-\\d{4}");
        assertThat(jdbc.queryForObject("SELECT blocked_until FROM supplier_application WHERE id = ?", LocalDate.class,
                s.applicationId())).isEqualTo(LocalDate.of(9999, 12, 31));
        // inativado sai da lista de credenciados
        mvc.perform(get("/api/suppliers").param("line", "L12").header("Authorization", commission))
                .andExpect(jsonPath("$.content[?(@.id == " + s.applicationId() + ")]").isEmpty());

        mvc.perform(get("/api/supplier/sanctions").header("Authorization", s.token()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(multipart("/api/supplier/sanctions/" + id + "/defense")
                        .file(new MockMultipartFile("data", "", "application/json",
                                "{\"text\":\"A equipe usava crachá; segue foto.\"}".getBytes()))
                        .file(new MockMultipartFile("file", "foto.pdf", "application/pdf", PDF))
                        .header("Authorization", s.token()))
                .andExpect(jsonPath("$.status").value("AWAITING_DECISION"))
                .andExpect(jsonPath("$.files.length()").value(1));
        String decision = """
                {"sanction":"%s","months":%s,"reason":"Infração comprovada.","version":%d}""";
        p = mvc.perform(get("/api/sanctions/" + id).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/sanctions/" + id + "/decision").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision.formatted("FINE", "null", num(p, "$.version"))))
                .andExpect(status().isUnprocessableEntity());
        p = mvc.perform(post("/api/sanctions/" + id + "/decision").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision.formatted("SUSPENSION", "6", num(p, "$.version"))))
                .andExpect(jsonPath("$.status").value("APPEAL_WINDOW"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(multipart("/api/supplier/sanctions/" + id + "/appeal")
                        .file(new MockMultipartFile("data", "", "application/json",
                                "{\"text\":\"Primeira ocorrência; peço advertência.\"}".getBytes()))
                        .header("Authorization", s.token()))
                .andExpect(jsonPath("$.status").value("BOARD_REVIEW"));
        p = mvc.perform(get("/api/sanctions/" + id).header("Authorization", board))
                .andReturn().getResponse().getContentAsString();
        String boardDecision = """
                {"outcome":"REFORMED","sanction":"WARNING","reason":"Primeira ocorrência.","version":%d}"""
                .formatted(num(p, "$.version"));
        mvc.perform(post("/api/sanctions/" + id + "/board-decision").header("Authorization", commission)
                .contentType(MediaType.APPLICATION_JSON).content(boardDecision)).andExpect(status().isForbidden());
        mvc.perform(post("/api/sanctions/" + id + "/board-decision").header("Authorization", board)
                        .contentType(MediaType.APPLICATION_JSON).content(boardDecision))
                .andExpect(jsonPath("$.status").value("FINAL"))
                .andExpect(jsonPath("$.finalSanction").value("WARNING"));
        // advertência não bloqueia: a inativação cautelar cai
        assertThat(jdbc.queryForObject("SELECT blocked_until FROM supplier_application WHERE id = ?", LocalDate.class,
                s.applicationId())).isNull();

        // 2º processo: sem defesa, descredenciamento, recurso não apresentado → vale a decisão
        p = mvc.perform(post("/api/sanctions").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":%d,"infraction":"FALSE_DOCUMENTS","facts":"Certidão adulterada.",
                                 "interimBlock":false}""".formatted(s.applicationId())))
                .andReturn().getResponse().getContentAsString();
        long id2 = num(p, "$.id");
        mvc.perform(post("/api/sanctions/" + id2 + "/decision").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sanction\":\"DEREGISTRATION\",\"years\":1,\"reason\":\"x\",\"version\":"
                                + num(p, "$.version") + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEFENSE_PENDING"));
        jdbc.update("UPDATE sanction_process SET defense_due = current_date - 1 WHERE id = ?", id2);
        assertThat(sanctions.advanceDeadlines()).isGreaterThanOrEqualTo(1);
        p = mvc.perform(get("/api/sanctions/" + id2).header("Authorization", commission))
                .andExpect(jsonPath("$.status").value("AWAITING_DECISION"))
                .andExpect(jsonPath("$.recidivist").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/sanctions/" + id2 + "/decision").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sanction\":\"DEREGISTRATION\",\"years\":1,\"reason\":\"Documento falso.\","
                                + "\"version\":" + num(p, "$.version") + "}"))
                .andExpect(jsonPath("$.status").value("APPEAL_WINDOW"));
        jdbc.update("UPDATE sanction_process SET appeal_due = current_date - 1 WHERE id = ?", id2);
        sanctions.advanceDeadlines();
        mvc.perform(get("/api/sanctions/" + id2).header("Authorization", commission))
                .andExpect(jsonPath("$.status").value("FINAL"))
                .andExpect(jsonPath("$.finalSanction").value("DEREGISTRATION"));
        LocalDate until = jdbc.queryForObject("SELECT blocked_until FROM supplier_application WHERE id = ?",
                LocalDate.class, s.applicationId());
        assertThat(until).isAfter(LocalDate.now().plusMonths(11));
        // fornecedor descredenciado não recebe a oportunidade
        String t = tender(commission, "L12", "20000");
        mvc.perform(get("/api/supplier/opportunities").header("Authorization", s.token()))
                .andExpect(jsonPath("$[?(@.id == " + num(t, "$.id") + ")]").isEmpty());
    }
}
