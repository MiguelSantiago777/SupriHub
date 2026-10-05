package org.institutonata.suprihub.notification.infra;

import org.institutonata.suprihub.notification.domain.MessageChannel;
import org.springframework.stereotype.Component;

/**
 * WhatsApp — previsto pela Política (art. 16) e pelo Edital (3.2), ainda sem integração.
 * Fica desabilitado; o serviço de notificação registra a mensagem como SKIPPED.
 * Para ativar: implementar {@link #send} com o provedor escolhido (ex.: WhatsApp Business
 * Cloud API) e respeitar o consentimento do fornecedor (Anexo II, item 5).
 */
@Component
public class WhatsAppChannel implements MessageChannel {

    @Override
    public ChannelType type() {
        return ChannelType.WHATSAPP;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void send(OutgoingMessage message) {
        throw new UnsupportedOperationException("Canal WhatsApp ainda não implementado");
    }
}
