package org.institutonata.suprihub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.mail.internet.MimeMessage;

import com.jayway.jsonpath.JsonPath;
import org.institutonata.suprihub.messaging.application.OutboxRelay;
import org.institutonata.suprihub.support.Auth;
import org.institutonata.suprihub.support.IntegrationTest;
import org.institutonata.suprihub.support.Mails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Controle de acesso por perfil e convite de usuários internos. */
class AccessControlIT extends IntegrationTest {

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void endpointsPublicosNaoExigemLogin() throws Exception {
        mvc.perform(get("/api/public/supply-lines")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void endpointsInternosExigemLogin() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conviteDeUsuarioInternoEPerfis() throws Exception {
        String admin = Auth.admin(mvc);
        String email = "comissao.teste@institutonata.org.br";

        String created = mvc.perform(post("/api/admin/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"%s","fullName":"Membro da Comissão","roles":["COMMISSION"]}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("COMMISSION"))
                .andReturn().getResponse().getContentAsString();

        MimeMessage invite = await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            relay.relayBatch();
            return sentMails.stream().filter(m -> Mails.to(m).equalsIgnoreCase(email)).findFirst().orElse(null);
        }, m -> m != null);
        assertThat(Mails.html(invite)).contains("/nova-senha?token=");

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + Mails.token(invite) + "\",\"newPassword\":\"Comissao2026\"}"))
                .andExpect(status().isOk());

        String commission = Auth.bearer(mvc, email, "Comissao2026");
        // Comissão não administra usuários nem altera parâmetros
        mvc.perform(get("/api/admin/users").header("Authorization", commission))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(put("/api/admin/parameters/band1_upper_limit").header("Authorization", commission)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"1\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        // ...mas consulta parâmetros e gerencia o catálogo
        mvc.perform(get("/api/admin/parameters").header("Authorization", commission)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/supply-lines").header("Authorization", commission)).andExpect(status().isOk());

        // ADMIN desativa o usuário
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        int version = JsonPath.read(
                mvc.perform(get("/api/admin/users").param("q", "comissao.teste").header("Authorization", admin))
                        .andReturn().getResponse().getContentAsString(), "$.content[0].version");
        mvc.perform(put("/api/admin/users/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":"Membro da Comissão","roles":["COMMISSION"],"enabled":false,"version":%d}"""
                                .formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Comissao2026\"}".formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        // o access token já emitido deixa de valer na hora (sem esperar os 15 minutos)
        mvc.perform(get("/api/me").header("Authorization", commission)).andExpect(status().isUnauthorized());
    }

    @Test
    void comissaoPodeLigarMedioGrandePorteNaLinha() throws Exception {
        String admin = Auth.admin(mvc);
        String line = mvc.perform(get("/api/public/supply-lines/L13")).andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(line, "$.version");
        mvc.perform(put("/api/admin/supply-lines/L13").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Materiais pedagógicos, esportivos e de consumo",
                                 "shortName":"Materiais pedagógicos e de consumo",
                                 "scope":"Papelaria, materiais didáticos",
                                 "cnaeReference":"4761-0/03","allowsIndividual":false,
                                 "allowsMediumLarge":true,"active":true,"version":%d}""".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowsMediumLarge").value(true));
        // versão antiga → conflito (controle de concorrência otimista)
        mvc.perform(put("/api/admin/supply-lines/L13").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"x","shortName":"x","scope":"x","cnaeReference":"x","allowsIndividual":false,
                                 "allowsMediumLarge":false,"active":true,"version":%d}""".formatted(version)))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE supply_line SET allows_medium_large = FALSE WHERE code = 'L13'");
    }
}
