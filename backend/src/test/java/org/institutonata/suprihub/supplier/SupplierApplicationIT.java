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

import java.time.LocalDate;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Requerimento do fornecedor e análise pela Comissão, de ponta a ponta. */
class SupplierApplicationIT extends IntegrationTest {

    static final String PASSWORD = "Senha2026";
    static final byte[] PDF = "%PDF-1.4\n% documento de teste\n".getBytes(StandardCharsets.US_ASCII);

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

    String meiJson(long version) {
        return """
                {"supplierType":"MEI","legalName":"Lanches da Maria MEI","tradeName":"Lanches da Maria",
                 "documentNumber":"11.222.333/0001-81","street":"Rua das Flores","addressNumber":"10",
                 "district":"Centro","municipalityId":%d,"cep":"26553-000","phone":"(21) 99999-0001",
                 "contactEmail":"contato@exemplo.com","representativeName":"Maria da Silva",
                 "representativeCpf":"529.982.247-25","representativeRole":"Titular",
                 "lineCodes":["L01","L07"],"linesDescription":"Kits lanche e oficinas de culinária",
                 "territories":["T1","T2"],"bankName":"Banco do Brasil","bankBranch":"1234",
                 "bankAccount":"56789-0","bankAccountType":"CHECKING","pixKey":"11222333000181",
                 "accountHolder":"Lanches da Maria MEI","womanLed":true,"race":"PARDA","cadUnico":false,
                 "declarationsAccepted":true,"version":%d}
                """.formatted(municipalities.findByIbgeCode("3302858").orElseThrow().getId(), version);
    }

    long version(String json) {
        return ((Number) JsonPath.read(json, "$.version")).longValue();
    }

    @Test
    void fluxoCompletoDoRequerimento() throws Exception {
        String supplierEmail = createUser(AppRole.SUPPLIER);
        String commissionEmail = createUser(AppRole.COMMISSION);
        String supplier = Auth.bearer(mvc, supplierEmail, PASSWORD);
        String commission = Auth.bearer(mvc, commissionEmail, PASSWORD);

        // Primeira visita cria o rascunho com nome e e-mail da conta
        String json = mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.contactEmail").value(supplierEmail))
                .andExpect(jsonPath("$.editable").value(true))
                .andReturn().getResponse().getContentAsString();

        // Envio incompleto é recusado com as pendências por campo
        mvc.perform(post("/api/supplier/application/submit").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"))
                .andExpect(jsonPath("$.errors[?(@.field=='supplierType')]").exists());

        json = mvc.perform(put("/api/supplier/application").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content(meiJson(version(json))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value("11222333000181"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.checklist[?(@.type=='CCMEI')].requirement").value("REQUIRED"))
                .andExpect(jsonPath("$.pendingItems[?(@.field=='documents.CCMEI')]").exists())
                .andReturn().getResponse().getContentAsString();

        // Só PDF, JPG ou PNG — conferido pelo conteúdo
        mvc.perform(multipart("/api/supplier/application/documents/CCMEI")
                        .file(new MockMultipartFile("file", "ccmei.pdf", "application/pdf", "texto".getBytes()))
                        .param("validUntil", LocalDate.now().plusDays(90).toString())
                        .header("Authorization", supplier))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));

        Long firstDocId = null;
        for (DocumentType type : DocumentType.values()) {
            if (type.requirementFor(SupplierType.MEI) == DocumentType.Requirement.REQUIRED) {
                String doc = mvc.perform(multipart("/api/supplier/application/documents/" + type.name())
                                .file(new MockMultipartFile("file", "../../" + type.name() + ".pdf",
                                        "application/pdf", PDF))
                                .param("validUntil", LocalDate.now().plusDays(90).toString())
                                .header("Authorization", supplier))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.contentType").value("application/pdf"))
                        .andExpect(jsonPath("$.filename").value(type.name() + ".pdf"))
                        .andReturn().getResponse().getContentAsString();
                if (firstDocId == null) {
                    firstDocId = ((Number) JsonPath.read(doc, "$.id")).longValue();
                }
            }
        }

        json = mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andExpect(jsonPath("$.pendingItems.length()").value(0))
                .andReturn().getResponse().getContentAsString();

        json = mvc.perform(post("/api/supplier/application/submit").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.analysisDueDate").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String protocol = JsonPath.read(json, "$.protocol");
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        assertThat(protocol).matches("CR-\\d{4}-\\d{5}");

        assertThat(Mails.html(awaitMail(supplierEmail, protocol))).contains(protocol);
        assertThat(Mails.html(awaitMail(commissionEmail, "Novo requerimento na fila: " + protocol)))
                .contains("/comissao/requerimentos/" + id);

        // Em análise, o fornecedor não altera nada
        mvc.perform(put("/api/supplier/application").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content(meiJson(version(json))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_LOCKED"));

        // Fornecedor não acessa a fila; Comissão e Admin acessam
        mvc.perform(get("/api/commission/applications").header("Authorization", supplier))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/commission/applications").header("Authorization", Auth.admin(mvc)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/commission/applications").param("q", "11.222.333")
                        .header("Authorization", commission))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].documentMasked").value("11.222.***/****-81"));
        mvc.perform(get("/api/commission/applications/counts").header("Authorization", commission))
                .andExpect(jsonPath("$.SUBMITTED").isNumber());
        mvc.perform(get("/api/commission/applications/" + id + "/documents/" + firstDocId + "/file")
                        .header("Authorization", commission))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(PDF));

        // Pedido de ajuste → fornecedor corrige e reenvia (mesmo protocolo)
        json = mvc.perform(get("/api/commission/applications/" + id).header("Authorization", commission))
                .andExpect(jsonPath("$.documentNumber").value("11222333000181"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/commission/applications/" + id + "/request-adjustment")
                        .header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\",\"version\":" + version(json) + "}"))
                .andExpect(status().isBadRequest());
        json = mvc.perform(post("/api/commission/applications/" + id + "/request-adjustment")
                        .header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"A certidão do FGTS está vencida.\",\"version\":"
                                + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADJUSTMENT_REQUESTED"))
                .andExpect(jsonPath("$.adjustmentDueDate").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(Mails.html(awaitMail(supplierEmail, "Ajuste"))).contains("FGTS está vencida");

        mvc.perform(multipart("/api/supplier/application/documents/FGTS_CERTIFICATE")
                        .file(new MockMultipartFile("file", "fgts-novo.pdf", "application/pdf", PDF))
                        .param("validUntil", LocalDate.now().plusDays(90).toString())
                        .header("Authorization", supplier))
                .andExpect(status().isCreated());
        json = mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andExpect(jsonPath("$.adjustmentMessage").value("A certidão do FGTS está vencida."))
                .andReturn().getResponse().getContentAsString();
        json = mvc.perform(post("/api/supplier/application/submit").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.protocol").value(protocol))
                .andReturn().getResponse().getContentAsString();

        // Versão desatualizada é recusada; com a versão certa, habilita
        mvc.perform(post("/api/commission/applications/" + id + "/approve").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/commission/applications/" + id + "/approve").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Documentação conferida.\",\"version\":" + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.accreditationValidUntil").isNotEmpty())
                .andExpect(jsonPath("$.history.length()").value(5));
        assertThat(Mails.html(awaitMail(supplierEmail, "credenciado"))).contains(protocol);

        // Decisão só em requerimento em análise
        json = mvc.perform(get("/api/commission/applications/" + id).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/commission/applications/" + id + "/reject").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"x\",\"version\":" + version(json) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_UNDER_ANALYSIS"));
    }

    @Test
    void inabilitadoPodeCorrigirEReenviar() throws Exception {
        String supplierEmail = createUser(AppRole.SUPPLIER);
        String supplier = Auth.bearer(mvc, supplierEmail, PASSWORD);
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);

        String json = mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andReturn().getResponse().getContentAsString();
        json = mvc.perform(put("/api/supplier/application").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content(meiJson(version(json))))
                .andReturn().getResponse().getContentAsString();
        for (DocumentType type : DocumentType.values()) {
            if (type.requirementFor(SupplierType.MEI) == DocumentType.Requirement.REQUIRED) {
                mvc.perform(multipart("/api/supplier/application/documents/" + type.name())
                                .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF))
                                .param("validUntil", LocalDate.now().plusDays(90).toString())
                                .header("Authorization", supplier))
                        .andExpect(status().isCreated());
            }
        }
        json = mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andReturn().getResponse().getContentAsString();
        json = mvc.perform(post("/api/supplier/application/submit").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();

        mvc.perform(post("/api/commission/applications/" + id + "/reject").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Consta restrição no CEIS.\",\"version\":" + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Consta restrição no CEIS."));
        assertThat(Mails.html(awaitMail(supplierEmail, "Resultado"))).contains("restrição no CEIS");

        json = mvc.perform(get("/api/supplier/application").header("Authorization", supplier))
                .andExpect(jsonPath("$.editable").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/supplier/application/submit").header("Authorization", supplier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version(json) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
}
