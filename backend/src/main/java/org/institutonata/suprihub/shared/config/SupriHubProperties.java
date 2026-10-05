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
        Bootstrap bootstrap) {

    public record Jwt(
            @NotBlank String secret,
            @DefaultValue("suprihub") String issuer,
            @DefaultValue("15m") Duration accessTtl,
            @DefaultValue("7d") Duration refreshTtl,
            @DefaultValue("false") boolean secureCookie) {
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
}
