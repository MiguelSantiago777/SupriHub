package org.institutonata.suprihub.auth.application;

import java.util.concurrent.atomic.AtomicBoolean;

import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.stereotype.Component;

/**
 * Liga a verificação em duas etapas ({@code suprihub.two-factor.enabled}, ligada por padrão).
 * Alterável em tempo de execução só para os testes de integração, que compartilham um único
 * contexto (e uma única fila de notificações) entre as classes.
 */
@Component
public class TwoFactorSwitch {

    private final AtomicBoolean enabled;

    public TwoFactorSwitch(SupriHubProperties props) {
        this.enabled = new AtomicBoolean(props.twoFactor().enabled());
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
