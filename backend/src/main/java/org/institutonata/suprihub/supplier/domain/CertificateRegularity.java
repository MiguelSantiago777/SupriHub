package org.institutonata.suprihub.supplier.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Situação das certidões de um credenciado (Planilha, aba Cadastro: "Venc. certidão federal",
 * "Venc. FGTS", "Venc. CNDT", "Venc. municipal" e "Alerta certidões"). Regra pura.
 * <p>
 * Acompanha as certidões exigidas para o tipo de negócio e, além delas, as opcionais que
 * foram enviadas (a municipal, "quando exigível"). Vencida ou faltando = irregular. Sem
 * validade informada (cadastros anteriores à V24) não é irregular: fica pendente de
 * conferência pela Comissão.
 */
public final class CertificateRegularity {

    private CertificateRegularity() {
    }

    public enum State {
        VALID("Em dia"),
        EXPIRING("Renovar"),
        EXPIRED("Vencida"),
        NO_DATE("Sem validade informada"),
        MISSING("Não enviada");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Check(DocumentType type, LocalDate validUntil, State state) {
    }

    public record Result(List<Check> checks) {

        /** Nenhuma certidão vencida ou faltando. */
        public boolean regular() {
            return checks.stream().noneMatch(c -> c.state() == State.EXPIRED || c.state() == State.MISSING);
        }

        public List<Check> withState(State state) {
            return checks.stream().filter(c -> c.state() == state).toList();
        }

        /** Primeira validade a vencer entre as certidões com data. */
        public LocalDate nextExpiry() {
            return checks.stream().map(Check::validUntil).filter(d -> d != null).min(LocalDate::compareTo)
                    .orElse(null);
        }
    }

    /** Documento enviado e a validade informada ({@code null} quando não há). */
    public record Uploaded(DocumentType type, LocalDate validUntil) {

        public static List<Uploaded> of(List<SupplierDocument> docs) {
            return docs.stream().map(d -> new Uploaded(d.getType(), d.getValidUntil())).toList();
        }
    }

    /** @param alertDays antecedência do aviso "RENOVAR" ({@code certificate_alert_days}) */
    public static Result evaluate(SupplierType type, List<Uploaded> uploaded, LocalDate today, int alertDays) {
        Map<DocumentType, Uploaded> byType = new EnumMap<>(DocumentType.class);
        uploaded.forEach(u -> byType.put(u.type(), u));
        List<Check> checks = new ArrayList<>();
        for (DocumentType t : DocumentType.values()) {
            if (!t.isCertificate()) {
                continue;
            }
            Uploaded u = byType.get(t);
            if (u == null) {
                if (type != null && t.requirementFor(type) == DocumentType.Requirement.REQUIRED) {
                    checks.add(new Check(t, null, State.MISSING));
                }
            } else {
                checks.add(new Check(t, u.validUntil(), state(u.validUntil(), today, alertDays)));
            }
        }
        return new Result(List.copyOf(checks));
    }

    public static State state(LocalDate validUntil, LocalDate today, int alertDays) {
        if (validUntil == null) {
            return State.NO_DATE;
        }
        if (validUntil.isBefore(today)) {
            return State.EXPIRED;
        }
        return validUntil.isAfter(today.plusDays(alertDays)) ? State.VALID : State.EXPIRING;
    }
}
