package org.institutonata.suprihub.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/** Recurso contra inabilitação (Edital 5.2) e lista pública de credenciados (Política art. 27). */
class AppealAndPublicListIT extends IntegrationTest {

    static final String PASSWORD = "Senha2026";
    static final byte[] PDF = "%PDF-1.4\n% recurso\n".getBytes(StandardCharsets.US_ASCII);

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

    MimeMessage awaitMail(String to, String subjectFragment) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            relay.relayBatch();
            return sentMails.stream()
                    .filter(m -> Mails.to(m).equalsIgnoreCase(to) && Mails.subject(m).contains(subjectFragment))
                    .findFirst().orElse(null);
        }, m -> m != null);
    }

    static long version(String json) {
        return ((Number) JsonPath.read(json, "$.version")).longValue();
    }

    static long num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    /** Fornecedor MEI com requerimento enviado; devolve o JSON do requerimento. */
    String submittedApplication(String supplier, String name, String line, boolean publishWomanLed)
            throws Exception {
        String json = mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andReturn().getResponse().getContentAsString();
        String body = """
                {"supplierType":"MEI","legalName":"%s","documentNumber":"11222333000181",
                 "street":"Rua A","addressNumber":"1","district":"Centro","municipalityId":%d,"cep":"26553000",
                 "phone":"21999990001","contactEmail":"contato@exemplo.com","representativeName":"Maria",
                 "representativeCpf":"52998224725","lineCodes":["%s"],"linesDescription":"Descrição",
                 "territories":["T1","T2"],"bankName":"Caixa","bankBranch":"1","bankAccount":"2",
                 "accountHolder":"%s","womanLed":true,"publishWomanLed":%s,"race":"PRETA","cadUnico":true,
                 "nis":"12345678901","declarationsAccepted":true,"version":%d}
                """.formatted(name, municipalities.findByIbgeCode("3302858").orElseThrow().getId(), line, name,
                publishWomanLed, version(json));
        mvc.perform(put("/api/supplier/application").header("Authorization", supplier)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        for (DocumentType type : DocumentType.values()) {
            if (type.requirementFor(SupplierType.MEI) == DocumentType.Requirement.REQUIRED) {
                mvc.perform(multipart("/api/supplier/application/documents/" + type.name())
                                .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF))
                                .header("Authorization", supplier))
                        .andExpect(status().isCreated());
            }
        }
        json = mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andReturn().getResponse().getContentAsString();
        return mvc.perform(post("/api/supplier/application/submit").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    String reject(String commission, String app) throws Exception {
        return mvc.perform(post("/api/commission/applications/" + num(app, "$.id") + "/reject")
                        .header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Certidão federal com restrição.\",\"version\":" + version(app)
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appealDeadline").isNotEmpty())
                .andExpect(jsonPath("$.canAppeal").value(true))
                .andReturn().getResponse().getContentAsString();
    }

    String fileAppeal(String supplier) throws Exception {
        return mvc.perform(multipart("/api/supplier/application/appeal")
                        .file(new MockMultipartFile("file", "certidao-regular.pdf", "application/pdf", PDF))
                        .param("reasons", "A certidão foi regularizada antes da decisão (Edital 4.4).")
                        .param("request", "Reconsiderar e habilitar.")
                        .header("Authorization", supplier))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void comissaoEncaminhaEDiretoriaNega() throws Exception {
        String supplierEmail = createUser(AppRole.SUPPLIER);
        String supplier = Auth.bearer(mvc, supplierEmail, PASSWORD);
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        String boardEmail = createUser(AppRole.BOARD);
        String board = Auth.bearer(mvc, boardEmail, PASSWORD);

        String app = submittedApplication(supplier, "Recorrente Ltda ME", "L01", false);
        reject(commission, app);

        // Sem razões, o recurso é recusado
        mvc.perform(multipart("/api/supplier/application/appeal").param("reasons", " ").param("request", "x")
                        .header("Authorization", supplier))
                .andExpect(status().isBadRequest());

        String filed = fileAppeal(supplier);
        assertThat(JsonPath.<String>read(filed, "$.appeal.status")).isEqualTo("FILED");
        assertThat(JsonPath.<Boolean>read(filed, "$.editable")).isFalse();
        assertThat(JsonPath.<Boolean>read(filed, "$.canAppeal")).isFalse();
        long appealId = num(filed, "$.appeal.id");
        awaitMail(supplierEmail, "Recebemos seu recurso");

        // Com recurso em andamento, o fornecedor não altera o requerimento nem recorre de novo
        mvc.perform(post("/api/supplier/application/submit").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(filed) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_UNDER_APPEAL"));
        mvc.perform(multipart("/api/supplier/application/appeal").param("reasons", "de novo")
                        .param("request", "x").header("Authorization", supplier))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPEAL_ALREADY_FILED"));

        // Permissões por papel
        mvc.perform(get("/api/appeals").header("Authorization", supplier)).andExpect(status().isForbidden());
        mvc.perform(get("/api/appeals").param("stage", "COMMISSION").header("Authorization", commission))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + appealId + ")].protocol").exists());
        String detail = mvc.perform(get("/api/appeals/" + appealId).header("Authorization", board))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.attachment.filename").value("certidao-regular.pdf"))
                .andExpect(jsonPath("$.application.decisionReason").value("Certidão federal com restrição."))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/appeals/" + appealId + "/attachment").header("Authorization", board))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        long v = num(detail, "$.appeal.version");
        mvc.perform(post("/api/appeals/" + appealId + "/reconsider").header("Authorization", board)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\",\"version\":" + v + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/appeals/" + appealId + "/board-decision").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true,\"reason\":\"x\",\"version\":" + v + "}"))
                .andExpect(status().isForbidden());

        // Comissão encaminha à Diretoria (motivação obrigatória)
        mvc.perform(post("/api/appeals/" + appealId + "/forward").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\",\"version\":" + v + "}"))
                .andExpect(status().isBadRequest());
        detail = mvc.perform(post("/api/appeals/" + appealId + "/forward").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"A restrição persiste na consulta à PGFN.\",\"version\":" + v + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.status").value("FORWARDED"))
                .andExpect(jsonPath("$.appeal.boardDue").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(Mails.html(awaitMail(boardEmail, "Recurso para decidir"))).contains("/recursos/" + appealId);
        awaitMail(supplierEmail, "encaminhado à Diretoria");

        // Diretoria nega: inabilitação mantida; fornecedor pode corrigir e reenviar
        mvc.perform(post("/api/appeals/" + appealId + "/board-decision").header("Authorization", board)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":false,\"reason\":\"Mantida a decisão da Comissão.\",\"version\":"
                                + num(detail, "$.appeal.version") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.status").value("DENIED"))
                .andExpect(jsonPath("$.application.status").value("REJECTED"));
        assertThat(Mails.html(awaitMail(supplierEmail, "Decisão do seu recurso"))).contains("Mantida a decisão");
        mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.canAppeal").value(false))
                .andExpect(jsonPath("$.appeal.status").value("DENIED"));
    }

    @Test
    void comissaoReconsideraEFornecedorEntraNaListaPublica() throws Exception {
        String supplierEmail = createUser(AppRole.SUPPLIER);
        String supplier = Auth.bearer(mvc, supplierEmail, PASSWORD);
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        String name = "Buffet Reconsiderado " + UUID.randomUUID().toString().substring(0, 8);

        String app = submittedApplication(supplier, name, "L02", true);
        reject(commission, app);
        long appealId = num(fileAppeal(supplier), "$.appeal.id");
        String detail = mvc.perform(get("/api/appeals/" + appealId).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/appeals/" + appealId + "/reconsider").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Certidão regular comprovada.\",\"version\":"
                                + num(detail, "$.appeal.version") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appeal.status").value("GRANTED"))
                .andExpect(jsonPath("$.application.status").value("APPROVED"))
                .andExpect(jsonPath("$.application.accreditationValidUntil").isNotEmpty());
        awaitMail(supplierEmail, "Recurso aceito");

        // Lista pública: sem login, filtra por linha e território, sem dados sensíveis
        String list = mvc.perform(get("/api/public/suppliers").param("line", "L02").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value(name))
                .andExpect(jsonPath("$.content[0].womanLed").value(true))
                .andExpect(jsonPath("$.content[0].documentMasked").value("11.222.***/****-81"))
                .andExpect(jsonPath("$.content[0].municipalityName").value("Mesquita"))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("contato@exemplo.com", "21999990001", "PRETA", "12345678901", "Caixa");
        mvc.perform(get("/api/public/suppliers").param("line", "L01").param("q", name))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/public/suppliers").param("territory", "T3").param("q", name))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/public/suppliers").param("territory", "T2").param("q", name))
                .andExpect(jsonPath("$.content.length()").value(1));

        // Credenciamento vencido sai da lista
        jdbc.update("UPDATE supplier_application SET accreditation_valid_until = current_date - 1 WHERE id = ?",
                num(app, "$.id"));
        mvc.perform(get("/api/public/suppliers").param("q", name))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void semAutorizacaoNaoPublicaLideradoPorMulherEPrazoDeRecursoVence() throws Exception {
        String supplier = Auth.bearer(mvc, createUser(AppRole.SUPPLIER), PASSWORD);
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        String name = "Sem Autorizacao " + UUID.randomUUID().toString().substring(0, 8);

        String app = submittedApplication(supplier, name, "L07", false);
        mvc.perform(post("/api/commission/applications/" + num(app, "$.id") + "/approve")
                        .header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version(app) + "}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/suppliers").param("q", name))
                .andExpect(jsonPath("$.content[0].womanLed").value(false));

        // Prazo de recurso vencido
        String other = Auth.bearer(mvc, createUser(AppRole.SUPPLIER), PASSWORD);
        String app2 = submittedApplication(other, "Fora do Prazo ME", "L07", false);
        reject(commission, app2);
        jdbc.update("UPDATE supplier_application SET appeal_deadline = current_date - 1 WHERE id = ?",
                num(app2, "$.id"));
        mvc.perform(get("/api/supplier/application").header("Authorization", other))
                .andExpect(jsonPath("$.canAppeal").value(false));
        mvc.perform(multipart("/api/supplier/application/appeal").param("reasons", "r").param("request", "p")
                        .header("Authorization", other))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPEAL_DEADLINE_EXPIRED"));
    }
}
