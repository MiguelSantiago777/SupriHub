package org.institutonata.suprihub.sanction.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.institutonata.suprihub.sanction.domain.SanctionEnums.Sanction;

/** Limites das sanções (Política art. 25; Edital 11.1) e vigência. Regra pura. */
public final class SanctionRules {

    private SanctionRules() {
    }

    /** Sanção decidida, com a dosimetria. */
    public record Decision(Sanction sanction, BigDecimal finePct, Integer months, Integer years) {
    }

    public record Issue(String field, String message) {
    }

    /**
     * Multa de 2% a 10%, só se prevista na ordem (e no máximo o percentual previsto);
     * suspensão de 1 a 24 meses; impedimento de 1 a 3 anos.
     *
     * @param orderPenaltyPct multa prevista na ordem ligada ao processo (nula = não prevista)
     */
    public static List<Issue> check(Decision d, BigDecimal orderPenaltyPct) {
        List<Issue> issues = new ArrayList<>();
        if (d.sanction() == null) {
            issues.add(new Issue("sanction", "Escolha a decisão."));
            return issues;
        }
        switch (d.sanction()) {
            case FINE -> {
                if (orderPenaltyPct == null) {
                    issues.add(new Issue("sanction", "Multa só quando prevista na ordem de fornecimento (art. 25)."));
                } else if (d.finePct() == null || d.finePct().compareTo(BigDecimal.valueOf(2)) < 0
                        || d.finePct().compareTo(BigDecimal.TEN) > 0) {
                    issues.add(new Issue("finePct", "A multa vai de 2% a 10%."));
                } else if (d.finePct().compareTo(orderPenaltyPct) > 0) {
                    issues.add(new Issue("finePct", "A multa não pode passar do percentual previsto na ordem ("
                            + orderPenaltyPct.stripTrailingZeros().toPlainString() + "%)."));
                }
            }
            case SUSPENSION -> {
                if (d.months() == null || d.months() < 1 || d.months() > 24) {
                    issues.add(new Issue("months", "A suspensão vai de 1 a 24 meses."));
                }
            }
            case DEREGISTRATION -> {
                if (d.years() == null || d.years() < 1 || d.years() > 3) {
                    issues.add(new Issue("years", "O impedimento vai de 1 a 3 anos."));
                }
            }
            default -> {
            }
        }
        return issues;
    }

    /** Até quando o fornecedor fica fora (inclusive); nulo se a sanção não bloqueia. */
    public static LocalDate effectiveUntil(Decision d, LocalDate start) {
        return switch (d.sanction()) {
            case SUSPENSION -> start.plusMonths(d.months()).minusDays(1);
            case DEREGISTRATION -> start.plusYears(d.years()).minusDays(1);
            default -> null;
        };
    }

    /** Anexo XII: reincidência é nova infração no prazo de {@code years} anos. */
    public static boolean recidivist(List<LocalDate> previousSanctions, LocalDate today, int years) {
        LocalDate since = today.minusYears(years);
        return previousSanctions.stream().anyMatch(d -> !d.isBefore(since));
    }
}
