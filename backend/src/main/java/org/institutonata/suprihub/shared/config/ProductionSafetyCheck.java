package org.institutonata.suprihub.shared.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * No perfil {@code prod}, impede a subida com segredos de desenvolvimento ou de exemplo
 * (o segredo do JWT de dev está no repositório: com ele, qualquer um emitiria um token de ADMIN).
 * Gere os valores com {@code scripts/gerar-env-producao.sh} (ou {@code .ps1}).
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);

    /** Valores que já apareceram em arquivos do repositório. */
    static final Set<String> KNOWN_SECRETS = Set.of(
            "dev-only-secret-change-me-0123456789abcdef",
            "troque-por-um-segredo-aleatorio-com-pelo-menos-32-caracteres",
            "test-secret-0123456789abcdef0123456789abcdef");
    static final Set<String> KNOWN_PASSWORDS = Set.of("Admin@12345", "Senha@123", "troque-esta-senha-1");
    private static final int MIN_SECRET_BYTES = 48;
    private static final int MIN_ADMIN_PASSWORD = 12;

    public ProductionSafetyCheck(SupriHubProperties props) {
        List<String> problems = problems(props);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Configuração insegura para produção:\n - " + String.join("\n - ", problems)
                    + "\nGere um .env de produção com scripts/gerar-env-producao.sh (ou .ps1).");
        }
        warnings(props).forEach(w -> log.warn("Produção: {}", w));
    }

    static List<String> problems(SupriHubProperties props) {
        List<String> problems = new ArrayList<>();
        String secret = props.jwt().secret();
        if (KNOWN_SECRETS.contains(secret) || looksLikePlaceholder(secret)) {
            problems.add("JWT_SECRET é um valor de exemplo/desenvolvimento.");
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add("JWT_SECRET deve ter pelo menos " + MIN_SECRET_BYTES + " caracteres aleatórios.");
        }
        SupriHubProperties.Bootstrap boot = props.bootstrap();
        if (boot != null && boot.demoUsers()) {
            problems.add("DEMO_USERS deve ser false (contas de demonstração com senha conhecida).");
        }
        String adminPassword = boot == null ? null : boot.adminPassword();
        if (adminPassword != null && !adminPassword.isBlank()
                && (KNOWN_PASSWORDS.contains(adminPassword) || looksLikePlaceholder(adminPassword)
                        || adminPassword.length() < MIN_ADMIN_PASSWORD)) {
            problems.add("BOOTSTRAP_ADMIN_PASSWORD é fraca ou de exemplo (mínimo " + MIN_ADMIN_PASSWORD
                    + " caracteres).");
        }
        return problems;
    }

    static List<String> warnings(SupriHubProperties props) {
        List<String> warnings = new ArrayList<>();
        if (!props.jwt().secureCookie()) {
            warnings.add("JWT_SECURE_COOKIE=false: o cookie de sessão trafega sem HTTPS.");
        }
        if (!props.frontendUrl().startsWith("https://")) {
            warnings.add("FRONTEND_URL não usa HTTPS.");
        }
        if (props.captcha() == null || !props.captcha().enabled()) {
            warnings.add("captcha desligado (defina CAPTCHA_SITE_KEY e CAPTCHA_SECRET_KEY).");
        }
        if (props.antivirus() == null || !props.antivirus().enabled()) {
            warnings.add("antivírus desligado (defina CLAMAV_HOST).");
        }
        return warnings;
    }

    private static boolean looksLikePlaceholder(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return v.contains("troque") || v.contains("change-me") || v.contains("changeme") || v.contains("dev-only");
    }
}
