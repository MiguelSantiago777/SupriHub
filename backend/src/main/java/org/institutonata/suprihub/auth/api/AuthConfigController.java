package org.institutonata.suprihub.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuração pública das telas de acesso (ex.: chave do captcha, que não é segredo). */
@RestController
@Tag(name = "Autenticação")
public class AuthConfigController {

    public record AuthConfig(String captchaSiteKey) {
    }

    private final AuthConfig config;

    public AuthConfigController(SupriHubProperties props) {
        SupriHubProperties.Captcha captcha = props.captcha();
        this.config = new AuthConfig(captcha != null && captcha.enabled() ? captcha.siteKey() : null);
    }

    @GetMapping("/api/public/auth-config")
    @Operation(summary = "Configuração pública das telas de entrada e cadastro")
    public AuthConfig get() {
        return config;
    }
}
