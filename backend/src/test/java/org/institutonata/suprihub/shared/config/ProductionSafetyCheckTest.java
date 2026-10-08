package org.institutonata.suprihub.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ProductionSafetyCheckTest {

    private static final String STRONG_SECRET = "Vq3k9yQ2mXc8RzL0pT7nW4sB1hF6dJ5gA2eK8uY3oP0iZ9xC7vN4bM1qS6tR";

    private static SupriHubProperties props(String secret, boolean demoUsers, String adminPassword) {
        return new SupriHubProperties("https://suprihub.example.org", "America/Sao_Paulo",
                new SupriHubProperties.Jwt(secret, "suprihub", Duration.ofMinutes(15), Duration.ofDays(7), true,
                        Duration.ofSeconds(30)),
                null, null, null, null,
                new SupriHubProperties.Bootstrap("admin@exemplo.org", adminPassword, demoUsers, "Senha@123"),
                new SupriHubProperties.Captcha("site", "secret"),
                new SupriHubProperties.Antivirus("clamav", 3310, Duration.ofSeconds(30)),
                new SupriHubProperties.TwoFactor(true));
    }

    @Test
    void recusaSegredoDeDesenvolvimentoEUsuariosDeDemonstracao() {
        assertThatThrownBy(() -> new ProductionSafetyCheck(
                props("dev-only-secret-change-me-0123456789abcdef", true, "Admin@12345")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("DEMO_USERS")
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void recusaSegredoCurto() {
        assertThat(ProductionSafetyCheck.problems(props("abcdefghijklmnopqrstuvwxyz0123456789", false,
                "uma-senha-bem-longa-42"))).singleElement().asString().contains("48");
    }

    @Test
    void aceitaConfiguracaoGerada() {
        assertThat(ProductionSafetyCheck.problems(props(STRONG_SECRET, false, "Xk2!pR9wq7LmZ4tB"))).isEmpty();
        assertThat(ProductionSafetyCheck.warnings(props(STRONG_SECRET, false, "Xk2!pR9wq7LmZ4tB"))).isEmpty();
    }
}
