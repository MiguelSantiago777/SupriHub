package org.institutonata.suprihub.notification.domain;

/**
 * Canal de entrega de mensagens. Implementações: e-mail (ativo) e WhatsApp (interface
 * prevista, sem implementação). Novos canais basta registrar como bean.
 */
public interface MessageChannel {

    ChannelType type();

    boolean isEnabled();

    void send(OutgoingMessage message);

    enum ChannelType { EMAIL, WHATSAPP }

    /**
     * @param to endereço no formato do canal (e-mail ou telefone E.164)
     */
    record OutgoingMessage(String to, String subject, String htmlBody, String textBody) {
        @Override
        public String toString() {
            return "OutgoingMessage[subject=" + subject + "]"; // sem destinatário em logs
        }
    }
}
