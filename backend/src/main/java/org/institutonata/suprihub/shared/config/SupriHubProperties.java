package org.institutonata.suprihub.shared.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações técnicas da aplicação (não confundir com parâmetros de negócio,
 * que ficam na tabela {@code parameter}).
 */
@Validated
@ConfigurationProperties(prefix = "suprihub")
public record SupriHubProperties(
        @NotBlank @DefaultValue("http://localhost:4200") String frontendUrl,
        @DefaultValue("America/Sao_Paulo") String timezone,
        Jwt jwt,
        Storage storage,
        Messaging messaging,
        RateLimit rateLimit,
        Mail mail,
        Bootstrap bootstrap,
        Captcha captcha,
        Antivirus antivirus) {

    public record Jwt(
            @NotBlank String secret,
            @DefaultValue("suprihub") String issuer,
            @DefaultValue("15m") Duration accessTtl,
            @DefaultValue("7d") Duration refreshTtl,
            @DefaultValue("false") boolean secureCookie,
            /** Janela em que reapresentar um refresh já rotacionado é tratado como corrida entre abas. */
            @DefaultValue("30s") Duration refreshReuseGrace) {
    }

    public record Storage(@DefaultValue("./data/files") String localPath) {
    }

    public record Messaging(
            @DefaultValue("true") boolean relayEnabled,
            @DefaultValue("2s") Duration relayInterval,
            @DefaultValue("50") int relayBatchSize,
            @DefaultValue("10") int maxAttempts,
            @DefaultValue("30d") Duration publishedRetention,
            @DefaultValue("suprihub.events") String exchange) {
    }

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("60") int publicPerMinute,
            @DefaultValue("10") int authPerMinute) {
    }

    public record Mail(
            @DefaultValue("Contrata + NATA <nao-responda@institutonata.org.br>") String from) {
    }

    /** Cria um ADMIN inicial se não existir nenhum (e, no perfil dev, usuários de demonstração). */
    public record Bootstrap(
            String adminEmail,
            String adminPassword,
            @DefaultValue("false") boolean demoUsers,
            @DefaultValue("Senha@123") String demoPassword) {
    }

    /** Cloudflare Turnstile. Sem a chave secreta, o captcha fica desligado. */
    public record Captcha(String siteKey, String secretKey) {

        public boolean enabled() {
            return secretKey != null && !secretKey.isBlank() && siteKey != null && !siteKey.isBlank();
        }
    }

    /** ClamAV (clamd via TCP). Sem host, os arquivos não são verificados. */
    public record Antivirus(
            String clamavHost,
            @DefaultValue("3310") int clamavPort,
            @DefaultValue("30s") Duration timeout) {

        public boolean enabled() {
            return clamavHost != null && !clamavHost.isBlank();
        }
    }
}
