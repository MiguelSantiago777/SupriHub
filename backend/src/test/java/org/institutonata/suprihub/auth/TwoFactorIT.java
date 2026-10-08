package org.institutonata.suprihub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;

import com.jayway.jsonpath.JsonPath;
import org.institutonata.suprihub.messaging.application.OutboxRelay;
import org.institutonata.suprihub.support.Auth;
import org.institutonata.suprihub.support.IntegrationTest;
import org.institutonata.suprihub.support.Mails;
import org.institutonata.suprihub.auth.application.TwoFactorSwitch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Código de 6 dígitos por e-mail: obrigatório para a equipe interna, opcional para o fornecedor. */
class TwoFactorIT extends IntegrationTest {

    private static final Pattern CODE = Pattern.compile(">\\s*(\\d{6})\\s*<");

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TwoFactorSwitch twoFactor;

    @BeforeEach
    void ligar() {
        twoFactor.setEnabled(true);
    }

    @AfterEach
    void desligar() {
        twoFactor.setEnabled(false);
    }

    @Test
    void equipeInternaEntraComCodigoEnviadoPorEmail() throws Exception {
        String challenge = loginChallenge(Auth.ADMIN_EMAIL, Auth.ADMIN_PASSWORD);
        String right = lastCode(Auth.ADMIN_EMAIL);

        // Código errado: avisa quantas tentativas restam
        mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(challenge, wrong(right))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CODE"));

        mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(challenge, right)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.twoFactorMandatory").value(true))
                .andExpect(cookie().exists("suprihub_rt"));

        // Código é de uso único
        mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(challenge, right)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    void reenvioTrocaOCodigo() throws Exception {
        String challenge = loginChallenge(Auth.ADMIN_EMAIL, Auth.ADMIN_PASSWORD);
        String first = lastCode(Auth.ADMIN_EMAIL);
        // Logo depois do envio, o reenvio pede para aguardar
        mvc.perform(post("/api/auth/login/resend-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\":\"" + challenge + "\"}"))
                .andExpect(status().isTooManyRequests());
        jdbc.update("UPDATE login_challenge SET last_sent_at = last_sent_at - interval '1 minute'");
        sentMails.clear();
        mvc.perform(post("/api/auth/login/resend-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\":\"" + challenge + "\"}"))
                .andExpect(status().isOk());
        String second = lastCode(Auth.ADMIN_EMAIL);
        if (!second.equals(first)) {
            mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                            .content(codeJson(challenge, first)))
                    .andExpect(jsonPath("$.code").value("WRONG_CODE"));
        }
        mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(challenge, second)))
                .andExpect(status().isOk());
    }

    @Test
    void cincoCodigosErradosInvalidamOLogin() throws Exception {
        String challenge = loginChallenge(Auth.ADMIN_EMAIL, Auth.ADMIN_PASSWORD);
        String right = lastCode(Auth.ADMIN_EMAIL);
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                            .content(codeJson(challenge, wrong(right))))
                    .andExpect(jsonPath("$.code").value("WRONG_CODE"));
        }
        mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(challenge, wrong(right))))
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        // Nem o código certo vale mais
        mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(challenge, right)))
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    void fornecedorLigaEDesligaEmMinhaConta() throws Exception {
        String email = "fornecedor.2fa@exemplo.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Forn2026x","fullName":"Fornecedor Dois Fatores"}""".formatted(email)))
                .andExpect(status().isAccepted());
        jdbc.update("UPDATE app_user SET email_verified = TRUE WHERE email = ?", email);

        // Desligado por padrão: entra só com a senha
        String bearer = Auth.bearer(mvc, email, "Forn2026x");
        mvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.twoFactor").value(false))
                .andExpect(jsonPath("$.twoFactorMandatory").value(false));

        mvc.perform(put("/api/me/two-factor").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"currentPassword\":\"Forn2026x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactor").value(true));

        String challenge = loginChallenge(email, "Forn2026x");
        mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(challenge, lastCode(email))))
                .andExpect(status().isOk());

        // A equipe interna não pode desligar
        String admin = adminBearer();
        mvc.perform(put("/api/me/two-factor").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"currentPassword\":\"Admin@12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED"));
    }

    private String adminBearer() throws Exception {
        String challenge = loginChallenge(Auth.ADMIN_EMAIL, Auth.ADMIN_PASSWORD);
        String body = mvc.perform(post("/api/auth/login/code").contentType(MediaType.APPLICATION_JSON)
                        .content(codeJson(challenge, lastCode(Auth.ADMIN_EMAIL))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private String loginChallenge(String email, String password) throws Exception {
        sentMails.clear();
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.sentTo")).contains("***@");
        return JsonPath.read(body, "$.challenge");
    }

    private String lastCode(String to) {
        MimeMessage mail = await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            relay.relayBatch();
            List<MimeMessage> codes = sentMails.stream()
                    .filter(m -> Mails.to(m).equalsIgnoreCase(to) && Mails.subject(m).contains("código de acesso"))
                    .toList();
            return codes.isEmpty() ? null : codes.getLast();
        }, m -> m != null);
        Matcher m = CODE.matcher(Mails.html(mail));
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static String wrong(String right) {
        return "000000".equals(right) ? "111111" : "000000";
    }

    private static String codeJson(String challenge, String code) {
        return "{\"challenge\":\"%s\",\"code\":\"%s\"}".formatted(challenge, code);
    }
}
