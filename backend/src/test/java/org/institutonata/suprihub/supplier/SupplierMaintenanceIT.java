package org.institutonata.suprihub.supplier;

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
import org.institutonata.suprihub.messaging.application.OutboxRelay;
import org.institutonata.suprihub.supplier.application.SupplierMaintenanceService;
import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierType;
import org.institutonata.suprihub.support.Auth;
import org.institutonata.suprihub.support.IntegrationTest;
import org.institutonata.suprihub.support.Mails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manutenção do cadastro: validade das certidões, aviso "RENOVAR", notificação e inativação
 * temporária, reativação, atualização cadastral, reconferência e renovação (V24).
 */
class SupplierMaintenanceIT extends IntegrationTest {

    static final String PASSWORD = "Senha2026";
    static final byte[] PDF = "%PDF-1.4\n% certidao\n".getBytes(StandardCharsets.US_ASCII);

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
    SupplierMaintenanceService maintenance;

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

    long mailCount(String to, String subjectFragment) {
        relay.relayBatch();
        return sentMails.stream()
                .filter(m -> Mails.to(m).equalsIgnoreCase(to) && Mails.subject(m).contains(subjectFragment)).count();
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    static long version(String json) {
        return ((Number) JsonPath.read(json, "$.version")).longValue();
    }

    String body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    String mine(String supplier) throws Exception {
        return body(mvc.perform(get("/api/supplier/application").header("Authorization", supplier)));
    }

    /** MEI habilitado na linha informada; devolve o id do requerimento. */
    long accredited(String supplier, String commission, String name, String line) throws Exception {
        String json = mine(supplier);
        String data = """
                {"supplierType":"MEI","legalName":"%s","documentNumber":"11222333000181",
                 "street":"Rua A","addressNumber":"1","district":"Centro","municipalityId":%d,"cep":"26553000",
                 "phone":"21999990001","contactEmail":"contato@exemplo.com","representativeName":"Maria",
                 "representativeCpf":"52998224725","lineCodes":["%s"],"linesDescription":"Descrição",
                 "territories":["T1"],"bankName":"Caixa","bankBranch":"1","bankAccount":"2",
                 "accountHolder":"%s","womanLed":true,"declarationsAccepted":true,"version":%d}
                """.formatted(name, municipalities.findByIbgeCode("3302858").orElseThrow().getId(), line, name,
                version(json));
        mvc.perform(put("/api/supplier/application").header("Authorization", supplier)
                .contentType(MediaType.APPLICATION_JSON).content(data)).andExpect(status().isOk());

        // Certidão sem validade ou vencida é recusada no envio
        mvc.perform(multipart("/api/supplier/application/documents/FEDERAL_CERTIFICATE")
                        .file(new MockMultipartFile("file", "f.pdf", "application/pdf", PDF))
                        .header("Authorization", supplier))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDITY_REQUIRED"));
        mvc.perform(multipart("/api/supplier/application/documents/FEDERAL_CERTIFICATE")
                        .file(new MockMultipartFile("file", "f.pdf", "application/pdf", PDF))
                        .param("validUntil", today().minusDays(1).toString())
                        .header("Authorization", supplier))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_EXPIRED"));

        for (DocumentType type : DocumentType.values()) {
            if (type.requirementFor(SupplierType.MEI) == DocumentType.Requirement.REQUIRED) {
                mvc.perform(multipart("/api/supplier/application/documents/" + type.name())
                                .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF))
                                .param("validUntil", today().plusDays(120).toString())
                                .header("Authorization", supplier))
                        .andExpect(status().isCreated());
            }
        }
        json = body(mvc.perform(post("/api/supplier/application/submit").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(mine(supplier)) + "}"))
                .andExpect(status().isOk()));
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        mvc.perform(post("/api/commission/applications/" + id + "/approve").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.regular").value(true))
                .andExpect(jsonPath("$.maintenance.lastRecheckOn").value(today().toString()))
                .andExpect(jsonPath("$.maintenance.certificates[?(@.type=='FGTS_CERTIFICATE')].verified")
                        .value(true));
        return id;
    }

    void setValidity(long applicationId, DocumentType type, LocalDate date) {
        jdbc.update("UPDATE supplier_document SET valid_until = ?, alert_sent_on = NULL "
                + "WHERE application_id = ? AND doc_type = ?", date, applicationId, type.name());
    }

    @Test
    void certidaoVencidaNotificaInativaEReativaComACertidaoNova() throws Exception {
        String supplierEmail = createUser(AppRole.SUPPLIER);
        String supplier = Auth.bearer(mvc, supplierEmail, PASSWORD);
        String commissionEmail = createUser(AppRole.COMMISSION);
        String commission = Auth.bearer(mvc, commissionEmail, PASSWORD);
        String name = "Lanches Certidão " + UUID.randomUUID().toString().substring(0, 8);
        long id = accredited(supplier, commission, name, "L01");

        // Aviso "RENOVAR" (15 dias antes), uma única vez
        setValidity(id, DocumentType.FGTS_CERTIFICATE, today().plusDays(5));
        maintenance.runDaily();
        assertThat(Mails.html(awaitMail(supplierEmail, "perto do vencimento"))).contains("FGTS");
        maintenance.runDaily();
        assertThat(mailCount(supplierEmail, "perto do vencimento")).isEqualTo(1);
        mvc.perform(get("/api/commission/maintenance").param("view", "CERTIFICATES")
                        .header("Authorization", commission))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].maintenance.certificates[?(@.state=='EXPIRING')]")
                        .exists());

        // Vencida: notifica o fornecedor (5 d.u.) e a Comissão (consulta direta); ainda ativo
        setValidity(id, DocumentType.FGTS_CERTIFICATE, today().minusDays(1));
        maintenance.runDaily();
        assertThat(Mails.html(awaitMail(supplierEmail, "Certidão vencida"))).contains("5 dias úteis");
        assertThat(Mails.html(awaitMail(commissionEmail, "Certidão vencida: " + name)))
                .contains("/comissao/requerimentos/" + id);
        mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andExpect(jsonPath("$.maintenance.regular").value(false))
                .andExpect(jsonPath("$.maintenance.irregularSince").value(today().toString()))
                .andExpect(jsonPath("$.maintenance.regularizationDue").isNotEmpty())
                .andExpect(jsonPath("$.maintenance.inactiveSince").doesNotExist());

        // Prazo vencido sem regularização: inativo temporário, fora do cadastro de credenciados
        jdbc.update("UPDATE supplier_application SET regularization_due = ? WHERE id = ?", today().minusDays(1), id);
        maintenance.runDaily();
        awaitMail(supplierEmail, "inativo temporariamente");
        mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andExpect(jsonPath("$.maintenance.inactiveSince").value(today().toString()));
        mvc.perform(get("/api/suppliers").param("q", name).header("Authorization", commission))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/commission/maintenance").param("view", "INACTIVE").header("Authorization", commission))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").exists());

        // 30 dias irregular: alerta de descredenciamento à Comissão (D-014)
        jdbc.update("UPDATE supplier_application SET irregular_since = ? WHERE id = ?", today().minusDays(30), id);
        maintenance.runDaily();
        awaitMail(commissionEmail, "Irregular há 30 dias");

        // Certidão nova do fornecedor: reativação na hora
        mvc.perform(multipart("/api/supplier/application/documents/FGTS_CERTIFICATE")
                        .file(new MockMultipartFile("file", "fgts.pdf", "application/pdf", PDF))
                        .param("validUntil", today().plusDays(30).toString())
                        .header("Authorization", supplier))
                .andExpect(status().isCreated());
        awaitMail(supplierEmail, "ativo de novo");
        awaitMail(commissionEmail, "Cadastro atualizado");
        mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.maintenance.regular").value(true))
                .andExpect(jsonPath("$.maintenance.inactiveSince").doesNotExist())
                .andExpect(jsonPath("$.maintenance.irregularSince").doesNotExist())
                .andExpect(jsonPath("$.maintenance.certificates[?(@.type=='FGTS_CERTIFICATE')].verified")
                        .value(false));
        mvc.perform(get("/api/suppliers").param("q", name).header("Authorization", commission))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void comissaoAnexaCertidaoReconfereERenova() throws Exception {
        String supplierEmail = createUser(AppRole.SUPPLIER);
        String supplier = Auth.bearer(mvc, supplierEmail, PASSWORD);
        String commissionEmail = createUser(AppRole.COMMISSION);
        String commission = Auth.bearer(mvc, commissionEmail, PASSWORD);
        String financeEmail = createUser(AppRole.FINANCE);
        long id = accredited(supplier, commission, "Gráfica Renovação " + UUID.randomUUID().toString().substring(0, 8),
                "L10");

        // Atualização cadastral: telefone e conta; a Comissão e o Financeiro são avisados
        String json = mine(supplier);
        String registration = """
                {"street":"Rua B","addressNumber":"20","district":"Centro","municipalityId":%d,"cep":"26553000",
                 "phone":"21988887777","contactEmail":"novo@exemplo.com","representativeName":"Maria",
                 "representativeCpf":"52998224725","bankName":"Banco do Brasil","bankBranch":"9","bankAccount":"8",
                 "accountHolder":"Gráfica","version":%d}
                """.formatted(municipalities.findByIbgeCode("3302858").orElseThrow().getId(), version(json));
        json = body(mvc.perform(put("/api/supplier/application/registration").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("21988887777"))
                .andExpect(jsonPath("$.bankName").value("Banco do Brasil")));
        assertThat(JsonPath.<String>read(json, "$.history[-1].message")).contains("Endereço", "Contato",
                "Dados bancários");
        awaitMail(commissionEmail, "Cadastro atualizado");
        awaitMail(financeEmail, "Conta bancária alterada");
        // Linhas e documento de identificação não mudam por aqui; rascunho do requerimento continua travado
        mvc.perform(put("/api/supplier/application").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_LOCKED"));

        // Comissão anexa a certidão obtida no site oficial (consulta direta): entra conferida
        mvc.perform(multipart("/api/commission/applications/" + id + "/documents/LABOR_CERTIFICATE")
                        .file(new MockMultipartFile("file", "cndt.pdf", "application/pdf", PDF))
                        .param("validUntil", today().plusDays(150).toString())
                        .header("Authorization", supplier))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/commission/applications/" + id + "/documents/CCMEI")
                        .file(new MockMultipartFile("file", "ccmei.pdf", "application/pdf", PDF))
                        .param("validUntil", today().plusDays(150).toString())
                        .header("Authorization", commission))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_A_CERTIFICATE"));
        json = body(mvc.perform(multipart("/api/commission/applications/" + id + "/documents/LABOR_CERTIFICATE")
                        .file(new MockMultipartFile("file", "cndt.pdf", "application/pdf", PDF))
                        .param("validUntil", today().plusDays(150).toString())
                        .header("Authorization", commission))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.certificates[?(@.type=='LABOR_CERTIFICATE')].verified")
                        .value(true)));

        // Reconferência semestral (vencida há 6 meses) entra na fila e é registrada
        jdbc.update("UPDATE supplier_application SET last_recheck_on = ? WHERE id = ?",
                today().minusMonths(6), id);
        json = mine(supplier);
        mvc.perform(get("/api/commission/maintenance").param("view", "RECHECK").header("Authorization", commission))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].maintenance.recheckDue").value(true));
        mvc.perform(post("/api/commission/applications/" + id + "/recheck").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Conferidas no site da RFB, Caixa e TST.\",\"version\":"
                                + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.lastRecheckOn").value(today().toString()))
                .andExpect(jsonPath("$.maintenance.recheckDue").value(false));

        // Renovação: só abre 60 dias antes do fim
        json = mine(supplier);
        mvc.perform(post("/api/supplier/application/renewal").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarationsAccepted\":true,\"version\":" + version(json) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENEWAL_NOT_OPEN"));
        LocalDate end = today().plusDays(30);
        jdbc.update("UPDATE supplier_application SET accreditation_valid_until = ? WHERE id = ?", end, id);
        maintenance.runDaily();
        awaitMail(supplierEmail, "Renove o seu credenciamento");
        json = mine(supplier);
        assertThat(JsonPath.<Boolean>read(json, "$.maintenance.renewalOpen")).isTrue();
        mvc.perform(post("/api/supplier/application/renewal").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarationsAccepted\":false,\"version\":" + version(json) + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DECLARATIONS_REQUIRED"));
        json = body(mvc.perform(post("/api/supplier/application/renewal").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarationsAccepted\":true,\"version\":" + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.renewalRequestedAt").isNotEmpty()));
        awaitMail(commissionEmail, "Renovação para analisar");

        // Devolvida com o que falta; pedida de novo; renovada a partir do fim do período atual
        json = body(mvc.perform(post("/api/commission/applications/" + id + "/return-renewal")
                        .header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Atualize o comprovante de endereço.\",\"version\":"
                                + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.renewalRequestedAt").doesNotExist()));
        assertThat(Mails.html(awaitMail(supplierEmail, "pediu ajustes"))).contains("comprovante de endereço");
        json = body(mvc.perform(post("/api/supplier/application/renewal").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarationsAccepted\":true,\"version\":" + version(mine(supplier)) + "}"))
                .andExpect(status().isOk()));
        mvc.perform(get("/api/commission/maintenance").param("view", "RENEWAL").header("Authorization", commission))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").exists());
        mvc.perform(post("/api/commission/applications/" + id + "/renew").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accreditationValidUntil").value(end.plusMonths(24).toString()))
                .andExpect(jsonPath("$.maintenance.renewalRequestedAt").doesNotExist());
        awaitMail(supplierEmail, "Credenciamento renovado");
    }
}
