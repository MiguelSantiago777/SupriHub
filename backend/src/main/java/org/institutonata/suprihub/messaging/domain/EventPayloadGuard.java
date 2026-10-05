package org.institutonata.suprihub.messaging.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Impede que dados pessoais sensíveis entrem no payload de eventos (que trafegam pelo
 * broker e ficam na tabela de outbox). Eventos devem carregar IDs, não dados pessoais.
 */
public final class EventPayloadGuard {

    private static final Set<String> FORBIDDEN_KEY_FRAGMENTS = Set.of(
            "email", "cpf", "cnpj", "phone", "telefone", "whatsapp",
            "address", "endereco", "bank", "conta", "agencia", "pix",
            "race", "raca", "nis", "cadunico", "password", "senha", "token");

    private EventPayloadGuard() {
    }

    public static void check(String eventType, Map<String, Object> payload) {
        for (String key : payload.keySet()) {
            String normalized = Normalizer.normalize(key, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            for (String fragment : FORBIDDEN_KEY_FRAGMENTS) {
                if (normalized.contains(fragment)) {
                    throw new IllegalArgumentException("Evento " + eventType
                            + " não pode carregar dado pessoal no payload (campo '" + key + "'). Use IDs.");
                }
            }
        }
    }
}
