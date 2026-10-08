package org.institutonata.suprihub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;
import org.institutonata.suprihub.auth.application.LoginThrottle;
import org.institutonata.suprihub.messaging.application.OutboxRelay;
import org.institutonata.suprihub.support.Auth;
import org.institutonata.suprihub.support.IntegrationTest;
import org.institutonata.suprihub.support.Mails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Fluxo completo: cadastro → evento no outbox → relay → RabbitMQ → consumidor →
 * e-mail de confirmação → confirmação → login → /api/me → refresh com rotação → logout.
 */
class AuthFlowIT extends IntegrationTest {

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LoginThrottle throttle;

    @Test
    void cadastroConfirmacaoLoginRefreshLogout() throws Exception {
        String email = "maria.lanches@exemplo.com";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Lanche2026","fullName":"Maria das Dores"}""".formatted(email)))
                .andExpect(status().isAccepted());

        // Evento gravado no outbox, sem dados pessoais no payload
        String payload = jdbc.queryForObject("""
                SELECT payload::text FROM outbox_event WHERE event_type = 'auth.user.registered'
                ORDER BY occurred_at DESC LIMIT 1""", String.class);
        assertThat(payload).contains("userId").doesNotContain(email);

        // Antes de confirmar, login é bloqueado
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Lanche2026"}""".formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        MimeMessage confirmation = awaitMail(email);
        assertThat(Mails.subject(confirmation)).contains("Confirme seu e-mail");
        assertThat(Mails.html(confirmation)).contains("Maria das Dores").contains("/confirmar-email?token=");
        String token = Mails.token(confirmation);

        mvc.perform(post("/api/auth/confirm-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());
        // token é de uso único
        mvc.perform(post("/api/auth/confirm-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LINK"));

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Lanche2026"}""".formatted(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("SUPPLIER"))
                .andExpect(cookie().httpOnly("suprihub_rt", true))
                .andExpect(cookie().path("suprihub_rt", "/api/auth"))
                .andReturn();
        String access = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie refresh1 = login.getResponse().getCookie("suprihub_rt");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(true));

        // refresh rotaciona o token
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(refresh1))
                .andExpect(status().isOk()).andReturn();
        Cookie refresh2 = refreshed.getResponse().getCookie("suprihub_rt");
        assertThat(refresh2.getValue()).isNotEqualTo(refresh1.getValue());

        // reapresentado logo em seguida (duas abas renovando juntas): tolerado
        mvc.perform(post("/api/auth/refresh").cookie(refresh1)).andExpect(status().isOk());

        // reuso do token antigo fora da janela de tolerância → todas as sessões encerradas
        jdbc.update("UPDATE refresh_token SET revoked_at = revoked_at - interval '5 minutes' WHERE replaced_by IS NOT NULL");
        mvc.perform(post("/api/auth/refresh").cookie(refresh1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mvc.perform(post("/api/auth/refresh").cookie(refresh2)).andExpect(status().isUnauthorized());
        // ...inclusive o access token já emitido
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test
    void trocaDePerfilInvalidaOAccessTokenNaHora() throws Exception {
        String admin = Auth.admin(mvc);
        String email = "financeiro.perfil@institutonata.org.br";
        String created = mvc.perform(post("/api/admin/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"%s","fullName":"Pessoa do Financeiro","roles":["FINANCE"]}"""
                                .formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + Mails.token(awaitMail(email)) + "\",\"newPassword\":\"Financa2026\"}"))
                .andExpect(status().isOk());
        String finance = Auth.bearer(mvc, email, "Financa2026");
        mvc.perform(get("/api/me").header("Authorization", finance)).andExpect(status().isOk());

        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        int version = JsonPath.read(
                mvc.perform(get("/api/admin/users").param("q", "financeiro.perfil").header("Authorization", admin))
                        .andReturn().getResponse().getContentAsString(), "$.content[0].version");
        mvc.perform(put("/api/admin/users/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":"Pessoa do Financeiro","roles":["DEMAND_MANAGER"],"enabled":true,"version":%d}"""
                                .formatted(version)))
                .andExpect(status().isOk());

        // token antigo (com o perfil FINANCE) recusado; um novo login traz o perfil atual
        mvc.perform(get("/api/me").header("Authorization", finance)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", Auth.bearer(mvc, email, "Financa2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("DEMAND_MANAGER"));
    }

    @Test
    void cadastroDuplicadoNaoRevelaQueOEmailExiste() throws Exception {
        String email = "joao.som@exemplo.com";
        String body = """
                {"email":"%s","password":"SomLuz2026","fullName":"João Som e Luz"}""".formatted(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        awaitMail(email);
        sentMails.clear();

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        MimeMessage warning = awaitMail(email);
        assertThat(Mails.subject(warning)).contains("já tem cadastro");
    }

    @Test
    void senhaFracaEDadosInvalidosRetornamProblemDetail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"invalido","password":"123","fullName":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(greaterThanOrEqualTo(3)));
    }

    @Test
    void bloqueiaOIpAposCincoSenhasErradasSemTravarAContaParaOutros() throws Exception {
        try {
            String body = """
                    {"email":"admin@teste.local","password":"errada123"}""";
            for (int i = 0; i < 5; i++) {
                mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"admin@teste.local","password":"Admin@12345"}"""))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

            // o dono da conta, em outro IP, continua entrando
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                            .content("""
                                    {"email":"admin@teste.local","password":"Admin@12345"}"""))
                    .andExpect(status().isOk());

            // e-mail inexistente recebe a mesma resposta (não revela quais contas existem)
            String ghost = """
                    {"email":"ninguem@teste.local","password":"errada123"}""";
            for (int i = 0; i < 5; i++) {
                mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(ghost))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(ghost))
                    .andExpect(status().isLocked());
        } finally {
            throttle.clear();
            jdbc.update("UPDATE app_user SET locked_until = NULL, failed_logins = 0 WHERE email = 'admin@teste.local'");
        }
    }

    @Test
    void esqueciMinhaSenha() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@teste.local\"}"))
                .andExpect(status().isAccepted());
        String token = Mails.token(awaitMail("admin@teste.local"));
        String tokenJson = "{\"token\":\"" + token + "\"}";
        mvc.perform(post("/api/auth/check-reset-link").contentType(MediaType.APPLICATION_JSON).content(tokenJson))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"Admin@12345\"}"))
                .andExpect(status().isOk());
        // Link já usado: a tela de nova senha avisa antes de a pessoa digitar.
        mvc.perform(post("/api/auth/check-reset-link").contentType(MediaType.APPLICATION_JSON).content(tokenJson))
                .andExpect(status().isBadRequest());
    }

    private MimeMessage awaitMail(String to) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            relay.relayBatch();
            return sentMails.stream().filter(m -> Mails.to(m).equalsIgnoreCase(to)).findFirst().orElse(null);
        }, m -> m != null);
    }
}
