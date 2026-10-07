package org.institutonata.suprihub.tender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
import org.institutonata.suprihub.tender.application.RotationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Oportunidades, rodízio da Faixa 1 e propostas (Fase 2, parte A). */
class TenderFlowIT extends IntegrationTest {

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
    OutboxRelay relay;
    @Autowired
    Clock clock;
    @Autowired
    MunicipalityRepository municipalities;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RotationService rotation;

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

    /** Fornecedor MEI habilitado na linha e territórios informados. */
    Supplier accredited(String commission, String line, String territories) throws Exception {
        String email = createUser(AppRole.SUPPLIER);
        String token = Auth.bearer(mvc, email, PASSWORD);
        String name = "Fornecedor " + UUID.randomUUID().toString().substring(0, 6);
        String json = mvc.perform(get("/api/supplier/application").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/supplier/application").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"supplierType":"MEI","legalName":"%s","documentNumber":"11222333000181","street":"Rua A",
                         "addressNumber":"1","district":"Centro","municipalityId":%d,"cep":"26553000",
                         "phone":"21999990001","contactEmail":"%s","representativeName":"Titular",
                         "representativeCpf":"52998224725","lineCodes":["%s"],"linesDescription":"Descrição",
                         "territories":[%s],"bankName":"Caixa","bankBranch":"1","bankAccount":"2",
                         "accountHolder":"%s","womanLed":false,"cadUnico":false,"declarationsAccepted":true,
                         "version":%d}""".formatted(name,
                        municipalities.findByIbgeCode("3302858").orElseThrow().getId(), email, line, territories,
                        name, num(json, "$.version"))))
                .andExpect(status().isOk());
        for (DocumentType type : DocumentType.values()) {
            if (type.requirementFor(SupplierType.MEI) == DocumentType.Requirement.REQUIRED) {
                mvc.perform(multipart("/api/supplier/application/documents/" + type.name())
                        .file(new MockMultipartFile("file", "d.pdf", "application/pdf", PDF))
                        .param("validUntil", LocalDate.now().plusDays(90).toString())
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

    String createTender(String commission, String title, String line, String value, String territories,
            Instant deadline) throws Exception {
        return mvc.perform(post("/api/tenders").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"%s","description":"Compra para o Festival 60+","lineCode":"%s",
                                 "executionMunicipalityId":%d,"location":"Ginásio de Mesquita",
                                 "executionDeadline":"Entrega até 30/10","estimatedValue":%s,
                                 "proposalDeadline":%s,"territories":[%s],
                                 "items":[{"description":"Cadeira plástica branca","unit":"un","quantity":200},
                                          {"description":"Mesa plástica","unit":"un","quantity":50}],
                                 "version":0}""".formatted(title, line,
                                municipalities.findByIbgeCode("3302858").orElseThrow().getId(), value,
                                deadline == null ? "null" : "\"" + deadline + "\"", territories)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    MimeMessage awaitMail(String to, String subjectFragment) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            relay.relayBatch();
            return sentMails.stream()
                    .filter(m -> Mails.to(m).equalsIgnoreCase(to) && Mails.subject(m).contains(subjectFragment))
                    .findFirst().orElse(null);
        }, m -> m != null);
    }

    static String unzipDocument(byte[] docx) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().equals("word/document.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("document.xml ausente");
    }

    static byte[] fakeDocx() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void cotacaoFaixa2ComPropostasEmSigilo() throws Exception {
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        Supplier a = accredited(commission, "L05", "\"T1\"");
        Supplier b = accredited(commission, "L05", "\"T1\",\"T2\"");
        Supplier otherLine = accredited(commission, "L07", "\"T1\"");

        String tender = createTender(commission, "Cadeiras e mesas plásticas", "L05", "20000", "\"T1\"",
                Instant.now().plus(1, ChronoUnit.DAYS));
        long id = num(tender, "$.id");
        assertThat(JsonPath.<String>read(tender, "$.band")).isEqualTo("FAIXA_2");
        assertThat(JsonPath.<String>read(tender, "$.status")).isEqualTo("DRAFT");

        // Prazo menor que 3 dias úteis é recusado
        mvc.perform(post("/api/tenders/" + id + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(tender, "$.version") + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TENDER_INCOMPLETE"))
                .andExpect(jsonPath("$.errors[0].field").value("proposalDeadline"));
        jdbc.update("UPDATE tender SET proposal_deadline = now() + interval '20 days' WHERE id = ?", id);
        tender = mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        tender = mvc.perform(post("/api/tenders/" + id + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(tender, "$.version") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.eligibleCount").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(tender, "$.number")).matches("OP-\\d{4}-\\d{4}");
        assertThat(Mails.html(awaitMail(a.email(), "Nova oportunidade"))).contains("/fornecedor/editais/" + id);

        // Só quem é da linha vê; o valor estimado nunca vai ao fornecedor
        mvc.perform(get("/api/supplier/opportunities").header("Authorization", otherLine.token()))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
        mvc.perform(get("/api/supplier/opportunities/" + id).header("Authorization", otherLine.token()))
                .andExpect(status().isNotFound());
        String op = mvc.perform(get("/api/supplier/opportunities/" + id).header("Authorization", a.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(op).doesNotContain("estimatedValue", "20000");
        long item1 = num(op, "$.items[0].id");
        long item2 = num(op, "$.items[1].id");

        // Modelo do Anexo VII em Word, já preenchido
        byte[] docx = mvc.perform(get("/api/supplier/opportunities/" + id + "/proposal-model")
                        .header("Authorization", a.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(unzipDocument(docx)).contains("ANEXO VII", a.name(), "11.222.333/0001-81",
                "Cadeira plástica branca", "☒ MEI", "☒ T1");

        // Online sem todos os preços → pendência; completa → total calculado
        mvc.perform(multipart("/api/supplier/opportunities/" + id + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json", """
                                {"mode":"ONLINE","unitPrices":{"%d":12.5},"validityDays":30,
                                 "declarationAccepted":true}""".formatted(item1).getBytes()))
                        .header("Authorization", a.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("items." + item2));
        mvc.perform(multipart("/api/supplier/opportunities/" + id + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json", """
                                {"mode":"ONLINE","unitPrices":{"%d":12.5,"%d":40},"executionDeadline":"10 dias",
                                 "validityDays":30,"declarationAccepted":true}""".formatted(item1, item2).getBytes()))
                        .header("Authorization", a.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposal.totalValue").value(4500.0))
                .andExpect(jsonPath("$.canWithdraw").value(true));
        awaitMail(a.email(), "Recebemos sua proposta");

        // Proposta própria em Word: exige arquivo e valor total
        mvc.perform(multipart("/api/supplier/opportunities/" + id + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json",
                                "{\"mode\":\"OWN_FILE\",\"validityDays\":60,\"declarationAccepted\":true}".getBytes()))
                        .header("Authorization", b.token()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/supplier/opportunities/" + id + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json",
                                "{\"mode\":\"OWN_FILE\",\"totalValue\":3900,\"validityDays\":60,\"declarationAccepted\":true}"
                                        .getBytes()))
                        .file(new MockMultipartFile("file", "minha-proposta.docx", "application/octet-stream",
                                fakeDocx()))
                        .header("Authorization", b.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposal.fileName").value("minha-proposta.docx"))
                .andExpect(jsonPath("$.proposal.totalValue").value(3900.0));

        // Retirar e reenviar
        mvc.perform(delete("/api/supplier/opportunities/" + id + "/proposal").header("Authorization", b.token()))
                .andExpect(status().isNoContent());
        mvc.perform(multipart("/api/supplier/opportunities/" + id + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json",
                                "{\"mode\":\"MODEL\",\"totalValue\":4100,\"validityDays\":30,\"declarationAccepted\":true}"
                                        .getBytes()))
                        .file(new MockMultipartFile("file", "modelo-assinado.pdf", "application/pdf", PDF))
                        .header("Authorization", b.token()))
                .andExpect(status().isCreated());

        // Antes do prazo, a Comissão só vê a quantidade
        mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andExpect(jsonPath("$.proposalsSealed").value(true))
                .andExpect(jsonPath("$.proposalCount").value(2))
                .andExpect(jsonPath("$.proposals.length()").value(0));
        mvc.perform(get("/api/tenders").header("Authorization", a.token())).andExpect(status().isForbidden());

        // Prazo encerrado: mapa ordenado pelo total e envio bloqueado
        jdbc.update("UPDATE tender SET proposal_deadline = now() - interval '1 minute' WHERE id = ?", id);
        String detail = mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andExpect(jsonPath("$.proposalsSealed").value(false))
                .andExpect(jsonPath("$.deadlinePassed").value(true))
                .andExpect(jsonPath("$.proposals[0].totalValue").value(4100.0))
                .andExpect(jsonPath("$.proposals[0].modeLabel").value("Modelo do Instituto assinado"))
                .andExpect(jsonPath("$.proposals[0].seatTerritory").value("T1"))
                .andExpect(jsonPath("$.proposals[1].totalValue").value(4500.0))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/tenders/" + id + "/proposals/" + num(detail, "$.proposals[0].id") + "/file")
                        .header("Authorization", commission))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        mvc.perform(multipart("/api/supplier/opportunities/" + id + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json", """
                                {"mode":"ONLINE","unitPrices":{"%d":1,"%d":1},"validityDays":30,
                                 "declarationAccepted":true}""".formatted(item1, item2).getBytes()))
                        .header("Authorization", a.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_CLOSED"));
    }

    @Test
    void duasRecusasInjustificadasDeslocamAoFinalDoRodizio() throws Exception {
        String commissionEmail = createUser(AppRole.COMMISSION);
        String commission = Auth.bearer(mvc, commissionEmail, PASSWORD);
        Supplier first = accredited(commission, "L17", "\"T1\"");
        Supplier second = accredited(commission, "L17", "\"T1\"");

        // 1ª oportunidade: o primeiro recusa sem motivo; o segundo recusa informando a indisponibilidade
        String t1 = createTender(commission, "Som do sarau", "L17", "2000", "\"T1\"", null);
        long id1 = num(t1, "$.id");
        mvc.perform(post("/api/tenders/" + id1 + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(t1, "$.version") + "}"))
                .andExpect(jsonPath("$.invitations[0].applicationId").value(first.applicationId()));
        mvc.perform(post("/api/supplier/opportunities/" + id1 + "/decline").header("Authorization", first.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/supplier/opportunities/" + id1 + "/decline").header("Authorization", second.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Equipamento em manutenção\"}"))
                .andExpect(status().isOk());

        // 2ª oportunidade: o primeiro não responde no prazo → 2 recusas injustificadas seguidas
        String t2 = createTender(commission, "Som da festa junina", "L17", "2500", "\"T1\"", null);
        long id2 = num(t2, "$.id");
        mvc.perform(post("/api/tenders/" + id2 + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(t2, "$.version") + "}"))
                .andExpect(jsonPath("$.invitations[0].applicationId").value(first.applicationId()));
        jdbc.update("UPDATE tender_invitation SET due_date = current_date - 1 WHERE tender_id = ?", id2);
        rotation.expireOverdue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rotation_displacement WHERE application_id = ?",
                Integer.class, first.applicationId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rotation_displacement WHERE application_id = ?",
                Integer.class, second.applicationId())).isZero();
        assertThat(Mails.html(awaitMail(first.email(), "final do rodízio"))).contains("indisponibilidade");
        assertThat(Mails.html(awaitMail(commissionEmail, "Deslocado no rodízio"))).contains("primeira vez");

        // Próxima oportunidade: o deslocado vai para o fim da fila
        String t3 = createTender(commission, "Som do encerramento", "L17", "1500", "\"T1\"", null);
        mvc.perform(post("/api/tenders/" + num(t3, "$.id") + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(t3, "$.version") + "}"))
                .andExpect(jsonPath("$.invitations[0].applicationId").value(second.applicationId()));
    }

    @Test
    void rodizioFaixa1() throws Exception {
        String commission = Auth.bearer(mvc, createUser(AppRole.COMMISSION), PASSWORD);
        Supplier first = accredited(commission, "L19", "\"T1\"");
        Supplier second = accredited(commission, "L19", "\"T1\"");

        String tender = createTender(commission, "Decoração do baile", "L19", "5000", "\"T1\"", null);
        long id = num(tender, "$.id");
        assertThat(JsonPath.<String>read(tender, "$.band")).isEqualTo("FAIXA_1");
        tender = mvc.perform(post("/api/tenders/" + id + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(tender, "$.version") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations.length()").value(1))
                .andExpect(jsonPath("$.invitations[0].applicationId").value(first.applicationId()))
                .andExpect(jsonPath("$.invitations[0].status").value("INVITED"))
                .andReturn().getResponse().getContentAsString();
        awaitMail(first.email(), "Você foi convidado");

        // Só o convidado vê a oportunidade
        mvc.perform(get("/api/supplier/opportunities/" + id).header("Authorization", second.token()))
                .andExpect(status().isNotFound());

        // Primeiro recusa → convite passa ao segundo
        mvc.perform(post("/api/supplier/opportunities/" + id + "/decline").header("Authorization", first.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sem agenda\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitation.status").value("DECLINED"))
                .andExpect(jsonPath("$.canSubmit").value(false));
        awaitMail(second.email(), "Você foi convidado");
        String op = mvc.perform(get("/api/supplier/opportunities/" + id).header("Authorization", second.token()))
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andReturn().getResponse().getContentAsString();

        // Segundo aceita enviando o preço; Comissão recusa o preço → fila acabou
        mvc.perform(multipart("/api/supplier/opportunities/" + id + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json", """
                                {"mode":"ONLINE","unitPrices":{"%d":20,"%d":60},"validityDays":30,
                                 "declarationAccepted":true}""".formatted(num(op, "$.items[0].id"),
                                num(op, "$.items[1].id")).getBytes()))
                        .header("Authorization", second.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation.status").value("ACCEPTED"));
        tender = mvc.perform(get("/api/tenders/" + id).header("Authorization", commission))
                .andExpect(jsonPath("$.invitations[1].proposedTotal").value(7000.0))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/tenders/invitations/" + num(tender, "$.invitations[1].id") + "/reject-price")
                        .header("Authorization", commission).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Acima do Banco de Preços\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations[1].status").value("PRICE_REJECTED"))
                .andExpect(jsonPath("$.invitations.length()").value(2));

        // Nova oportunidade: convite expira e o rodízio segue; seleção confirmada
        String t2 = createTender(commission, "Arranjos de flores", "L19", "3000", "\"T1\"", null);
        long id2 = num(t2, "$.id");
        t2 = mvc.perform(post("/api/tenders/" + id2 + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(t2, "$.version") + "}"))
                .andExpect(jsonPath("$.invitations[0].applicationId").value(first.applicationId()))
                .andReturn().getResponse().getContentAsString();
        jdbc.update("UPDATE tender_invitation SET due_date = current_date - 1 WHERE tender_id = ?", id2);
        assertThat(rotation.expireOverdue()).isEqualTo(1);
        t2 = mvc.perform(get("/api/tenders/" + id2).header("Authorization", commission))
                .andExpect(jsonPath("$.invitations[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.invitations[1].applicationId").value(second.applicationId()))
                .andReturn().getResponse().getContentAsString();
        String op2 = mvc.perform(get("/api/supplier/opportunities/" + id2).header("Authorization", second.token()))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(multipart("/api/supplier/opportunities/" + id2 + "/proposal")
                        .file(new MockMultipartFile("data", "", "application/json", """
                                {"mode":"MODEL","totalValue":2800,"validityDays":30,"declarationAccepted":true}"""
                                .getBytes()))
                        .file(new MockMultipartFile("file", "assinado.jpg", "image/jpeg",
                                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2}))
                        .header("Authorization", second.token()))
                .andExpect(status().isCreated());
        t2 = mvc.perform(get("/api/tenders/" + id2).header("Authorization", commission))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/tenders/invitations/" + num(t2, "$.invitations[1].id") + "/confirm")
                        .header("Authorization", commission))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWARDED"))
                .andExpect(jsonPath("$.invitations[1].status").value("CONFIRMED"));
        assertThat(op2).contains("Arranjos de flores");

        // Próxima rodada: quem nunca foi selecionado vem antes de quem acabou de ser
        String t3 = createTender(commission, "Balões", "L19", "1000", "\"T1\"", null);
        mvc.perform(post("/api/tenders/" + num(t3, "$.id") + "/publish").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + num(t3, "$.version") + "}"))
                .andExpect(jsonPath("$.invitations[0].applicationId").value(first.applicationId()));
    }
}
