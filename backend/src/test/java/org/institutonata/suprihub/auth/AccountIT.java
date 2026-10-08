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

import jakarta.mail.internet.MimeMessage;

import com.jayway.jsonpath.JsonPath;
import org.institutonata.suprihub.messaging.application.OutboxRelay;
import org.institutonata.suprihub.support.Auth;
import org.institutonata.suprihub.support.IntegrationTest;
import org.institutonata.suprihub.support.Mails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** "Minha conta": nome, senha (encerra outras sessões) e e-mail (confirmado pelo novo endereço). */
class AccountIT extends IntegrationTest {

    @Autowired
    OutboxRelay relay;

    @Test
    void trocaNomeSenhaEEmail() throws Exception {
        String email = "conta.propria@exemplo.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Conta2026","fullName":"joana conta"}""".formatted(email)))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/confirm-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + Mails.token(awaitMail(email, "Confirme seu e-mail")) + "\"}"))
                .andExpect(status().isOk());
        String bearer = Auth.bearer(mvc, email, "Conta2026");

        // Nome digitado em minúsculas já foi salvo com iniciais maiúsculas
        mvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.fullName").value("Joana Conta"));
        mvc.perform(put("/api/me/name").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"joana da conta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Joana da Conta"));

        // Senha atual errada é recusada
        mvc.perform(post("/api/me/password").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Errada123\",\"newPassword\":\"Nova2026x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        // Trocar a senha encerra a sessão antiga e devolve uma nova
        MvcResult changed = mvc.perform(post("/api/me/password").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Conta2026\",\"newPassword\":\"Nova2026x\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("suprihub_rt"))
                .andReturn();
        String newBearer = "Bearer " + JsonPath.read(changed.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", newBearer)).andExpect(status().isOk());
        assertThat(Mails.html(awaitMail(email, "Sua senha foi trocada"))).contains("Joana da Conta");

        // Troca de e-mail: só vale depois de abrir o link enviado ao novo endereço
        String newEmail = "conta.nova@exemplo.com";
        mvc.perform(post("/api/me/email").header("Authorization", newBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"%s\",\"currentPassword\":\"Nova2026x\"}".formatted(newEmail)))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/me").header("Authorization", newBearer)).andExpect(jsonPath("$.email").value(email));
        String token = Mails.token(awaitMail(newEmail, "Confirme seu novo e-mail"));
        mvc.perform(post("/api/auth/confirm-email-change").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", newBearer)).andExpect(jsonPath("$.email").value(newEmail));
        awaitMail(email, "Seu e-mail de acesso foi trocado");
        Auth.bearer(mvc, newEmail, "Nova2026x");

        // E-mail já usado por outra conta: resposta igual, mas nada é enviado
        mvc.perform(post("/api/me/email").header("Authorization", newBearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"admin@teste.local\",\"currentPassword\":\"Nova2026x\"}"))
                .andExpect(status().isAccepted());
    }

    private MimeMessage awaitMail(String to, String subject) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            relay.relayBatch();
            return sentMails.stream()
                    .filter(m -> Mails.to(m).equalsIgnoreCase(to) && Mails.subject(m).contains(subject))
                    .findFirst().orElse(null);
        }, m -> m != null);
    }
}
