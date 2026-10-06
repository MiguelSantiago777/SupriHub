package org.institutonata.suprihub.contract;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import jakarta.mail.internet.MimeMessage;

import com.jayway.jsonpath.JsonPath;
import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.institutonata.suprihub.auth.infra.AppUserRepository;
import org.institutonata.suprihub.auth.infra.RoleRepository;
import org.institutonata.suprihub.catalog.infra.MunicipalityRepository;
import org.institutonata.suprihub.contract.application.ServiceOrderService;
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

/** Da adjudicação ao pagamento: OF/OS, assinatura, recebimento, nota e pagamento (Fase 3A). */
class ServiceOrderFlowIT extends IntegrationTest {

    static final String PASSWORD = "Senha2026";
    static final byte[] PDF = "%PDF-1.4\n% doc\n".getBytes(StandardCharsets.US_ASCII);
    static final String MESQUITA = "3302858";

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
    ServiceOrderService orders;

    record Supplier(String email, String token, long applicationId) {
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

    String token(AppRole role) throws Exception {
        return Auth.bearer(mvc, createUser(role), PASSWORD);
    }

    static long num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    long municipality() {
        return municipalities.findByIbgeCode(MESQUITA).orElseThrow().getId();
    }

    Supplier accredited(String commission, String line) throws Exception {
        String email = createUser(AppRole.SUPPLIER);
        String token = Auth.bearer(mvc, email, PASSWORD);
        String name = "MEI " + UUID.randomUUID().toString().substring(0, 6);
        String json = mvc.perform(get("/api/supplier/application").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/supplier/application").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"supplierType":"MEI","legalName":"%s","documentNumber":"11222333000181","street":"Rua A",
                         "addressNumber":"1","district":"Centro","municipalityId":%d,"cep":"26553000",
                         "phone":"21999990001","contactEmail":"%s","representativeName":"Titular",
                         "representativeCpf":"52998224725","lineCodes":["%s"],"linesDescription":"Lanches",
                         "territories":["T1"],"bankName":"Caixa","bankBranch":"1","bankAccount":"2",
                         "accountHolder":"%s","womanLed":false,"cadUnico":false,"declarationsAccepted":true,
                         "version":%d}""".formatted(name, municipality(), email, line, name, num(json, "$.version"))))
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
        return new Supplier(email, token, id);
    }

    /** Oportunidade publicada (prazo à frente); devolve o JSON do detalhe. */
    String published(String commission, String line, String estimated) throws Exception {
        String json = mvc.perform(post("/api/tenders").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"Kits lanche","description":"Kits para o Baile 60+","lineCode":"%s",
                                 "executionMunicipalityId":%d,"location":"Ginásio","schedule":"15/11, 14h",
                                 "estimatedValue":%s,"proposalDeadline":null,"territories":["T1"],
                                 "items":[{"description":"Kit lanche","unit":"un","quantity":200}],
                                 "version":0}""".formatted(line, municipality(), estimated)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = num(json, "$.id");
        jdbc.update("UPDATE tender SET proposal_deadline = now() + interval '20 days' WHERE id = ?", id);
        json = mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        return mvc.perform(post("/api/tenders/" + id + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(json, "$.version") + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    void propose(Supplier s, long tenderId, long itemId, String unitPrice) throws Exception {
        mvc.perform(multipart("/api/supplier/opportunities/" + tenderId + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json", """
                                {"mode":"ONLINE","unitPrices":{"%d":%s},"validityDays":30,"declarationAccepted":true}"""
                                .formatted(itemId, unitPrice).getBytes()))
                        .header("Authorization", s.token()))
                .andExpect(status().isCreated());
    }

    MimeMessage awaitMail(String to, String subjectFragment) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            relay.relayBatch();
            return sentMails.stream()
                    .filter(m -> Mails.to(m).equalsIgnoreCase(to) && Mails.subject(m).contains(subjectFragment))
                    .findFirst().orElse(null);
        }, m -> m != null);
    }

    @Test
    void faixa2DaAdjudicacaoAoPagamento() throws Exception {
        String commission = token(AppRole.COMMISSION);
        String manager = token(AppRole.DEMAND_MANAGER);
        String finance = token(AppRole.FINANCE);
        Supplier s = accredited(commission, "L01");
        String tender = published(commission, "L01", "20000");
        long tenderId = num(tender, "$.id");
        propose(s, tenderId, num(tender, "$.items[0].id"), "20");
        jdbc.update("UPDATE tender SET proposal_deadline = now() - interval '1 minute' WHERE id = ?", tenderId);
        String panel = mvc.perform(post("/api/tenders/" + tenderId + "/judgment/start")
                        .header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"Única proposta.\"}"))
                .andExpect(jsonPath("$.judgment.status").value("DECIDED")).andReturn().getResponse()
                .getContentAsString();
        mvc.perform(post("/api/tenders/" + tenderId + "/judgment/homologate").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + num(panel, "$.judgment.version") + "}"))
                .andExpect(status().isOk());

        // A ordem nasce em preparação, com os itens e o preço da proposta
        String summary = mvc.perform(get("/api/contracts/by-tender/" + tenderId).header("Authorization", manager))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalValue").value(4000.0))
                .andReturn().getResponse().getContentAsString();
        long id = num(summary, "$.id");
        String base = "/api/contracts/" + id;
        String order = mvc.perform(get(base).header("Authorization", manager))
                .andExpect(jsonPath("$.items[0].unitPrice").value(20.0))
                .andExpect(jsonPath("$.paymentDays").value(30))
                .andExpect(jsonPath("$.acceptanceBased").value(false))
                .andReturn().getResponse().getContentAsString();
        // fornecedor não vê a ordem em preparação
        mvc.perform(get("/api/supplier/contracts/" + id).header("Authorization", s.token()))
                .andExpect(status().isNotFound());

        // Sinal acima de 30% é recusado
        String terms = """
                {"kind":"SUPPLY","project":"Baile da Memória 60+ — rubrica 3.3","objectDescription":"200 kits lanche",
                 "schedule":"15/11, 14h","location":"Ginásio","paymentDays":%d,"upfrontValue":%s,"penaltyPct":5,
                 "version":%d}""";
        mvc.perform(put(base).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(terms.formatted(30, "1500", num(order, "$.version"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("upfrontValue"));
        order = mvc.perform(put(base).header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(terms.formatted(20, "1000", num(order, "$.version"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // Financeiro não emite
        mvc.perform(post(base + "/issue").header("Authorization", finance).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + num(order, "$.version") + "}"))
                .andExpect(status().isForbidden());
        order = mvc.perform(post(base + "/issue").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(order, "$.version") + "}"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.signatureDueDate").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(order, "$.number")).matches("OF-\\d{4}-\\d{4}");
        awaitMail(s.email(), "Assine a ordem");

        // Fornecedor assina
        String sup = "/api/supplier/contracts/" + id;
        mvc.perform(get("/api/supplier/contracts").header("Authorization", s.token()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post(sup + "/sign").header("Authorization", s.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarationAccepted\":false}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(sup + "/sign").header("Authorization", s.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarationAccepted\":true}"))
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signedVia").value("ELECTRONIC"));
        byte[] pdf = mvc.perform(get(sup + "/pdf").header("Authorization", s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Nota antes do recebimento: recusada
        mvc.perform(multipart(sup + "/invoice")
                        .file(new MockMultipartFile("data", "", "application/json",
                                ("{\"number\":\"123\",\"date\":\"" + LocalDate.now() + "\",\"value\":4000}").getBytes()))
                        .file(new MockMultipartFile("file", "nf.pdf", "application/pdf", PDF))
                        .header("Authorization", s.token()))
                .andExpect(status().isConflict());

        // Recebimento com ressalvas e depois definitivo
        String receipt = """
                {"kind":"%s","executionDate":"%s","reservations":"Faltaram 10 kits","correctionDays":2,
                 "scoreDeadline":%d,"scoreQuality":4,"scoreDocumentation":5,"scoreService":5,"scoreSafety":5,
                 "evidenceKinds":"fotos; lista de presença"}""";
        mvc.perform(post(base + "/receipts").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(receipt.formatted("WITH_RESERVATIONS", LocalDate.now(), 3)))
                .andExpect(jsonPath("$.status").value("RECEIVED_WITH_RESERVATIONS"))
                .andExpect(jsonPath("$.rating").doesNotExist());
        mvc.perform(post(base + "/receipts").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(receipt.formatted("DEFINITIVE", LocalDate.now(), 6)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("scoreDeadline"));
        mvc.perform(post(base + "/receipts").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(receipt.formatted("DEFINITIVE", LocalDate.now(), 4)))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.rating").value(4.6))
                .andExpect(jsonPath("$.receipts.length()").value(2))
                .andExpect(jsonPath("$.paymentDueDate").value(LocalDate.now().plusDays(20).toString()));
        mvc.perform(multipart(base + "/files").file(new MockMultipartFile("file", "fotos.pdf", "application/pdf", PDF))
                        .param("kind", "EVIDENCE")
                        .header("Authorization", manager))
                .andExpect(status().isCreated());
        // Banco de Preços: 4.000 ÷ 200 = 20,00 por kit
        assertThat(jdbc.queryForObject("SELECT unit_price FROM price_bank_entry WHERE service_order_id = ?",
                java.math.BigDecimal.class, id)).isEqualByComparingTo("20.00");
        awaitMail(s.email(), "Termo de recebimento");
        mvc.perform(post(sup + "/acknowledge").header("Authorization", s.token()))
                .andExpect(jsonPath("$.receipts[1].supplierAckAt").exists());

        // Fornecedor envia a nota (sem arquivo: recusada)
        String invoice = "{\"number\":\"NF 123\",\"date\":\"" + LocalDate.now() + "\",\"value\":4000}";
        mvc.perform(multipart(sup + "/invoice")
                        .file(new MockMultipartFile("data", "", "application/json", invoice.getBytes()))
                        .header("Authorization", s.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        order = mvc.perform(multipart(sup + "/invoice")
                        .file(new MockMultipartFile("data", "", "application/json", invoice.getBytes()))
                        .file(new MockMultipartFile("file", "nf.pdf", "application/pdf", PDF))
                        .header("Authorization", s.token()))
                .andExpect(jsonPath("$.status").value("INVOICED"))
                .andReturn().getResponse().getContentAsString();

        // Financeiro paga: exige comprovante
        String payment = "{\"paidOn\":\"" + LocalDate.now() + "\",\"value\":4000,\"method\":\"PIX\",\"version\":%d}";
        mvc.perform(post(base + "/payment").header("Authorization", finance).contentType(MediaType.APPLICATION_JSON)
                        .content(payment.formatted(num(order, "$.version"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("proof"));
        mvc.perform(post(base + "/payment").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(payment.formatted(num(order, "$.version"))))
                .andExpect(status().isForbidden());
        order = mvc.perform(multipart(base + "/files").file(new MockMultipartFile("file", "pix.pdf", "application/pdf", PDF))
                        .param("kind", "PAYMENT_PROOF")
                        .header("Authorization", finance))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post(base + "/payment").header("Authorization", finance).contentType(MediaType.APPLICATION_JSON)
                        .content(payment.formatted(num(order, "$.version"))))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMethodLabel").value("Pix"));
        awaitMail(s.email(), "Pagamento feito");

        // A avaliação passa a valer no desempate
        assertThat(jdbc.queryForObject("SELECT rating FROM service_order WHERE id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("4.60");
    }

    @Test
    void faixa1AceiteValeComoAssinaturaEPrazoVencido() throws Exception {
        String commission = token(AppRole.COMMISSION);
        // Linha usada só aqui: o rodízio convida este fornecedor
        Supplier s = accredited(commission, "L18");
        String tender = published(commission, "L18", "5000");
        long tenderId = num(tender, "$.id");
        assertThat(JsonPath.<String>read(tender, "$.band")).isEqualTo("FAIXA_1");
        String detail;
        propose(s, tenderId, num(tender, "$.items[0].id"), "20");
        detail = mvc.perform(get("/api/tenders/" + tenderId).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        long invitation = num(detail, "$.invitations[-1].id");
        mvc.perform(post("/api/tenders/invitations/" + invitation + "/confirm").header("Authorization", commission))
                .andExpect(jsonPath("$.status").value("AWARDED"));
        String summary = mvc.perform(get("/api/contracts/by-tender/" + tenderId).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        long id = num(summary, "$.id");
        String order = mvc.perform(get("/api/contracts/" + id).header("Authorization", commission))
                .andExpect(jsonPath("$.acceptanceBased").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/contracts/" + id + "/issue").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(order, "$.version") + "}"))
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signedVia").value("ACCEPTANCE"));

        // Prazo de assinatura vencido (Faixa 2/3): ordem fica "não assinada"
        jdbc.update("UPDATE service_order SET status = 'ISSUED', signature_due_date = current_date - 1 WHERE id = ?",
                id);
        assertThat(orders.expireOverdue()).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/contracts/" + id).header("Authorization", commission))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }
}
