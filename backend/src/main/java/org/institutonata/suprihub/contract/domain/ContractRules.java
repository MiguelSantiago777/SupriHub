package org.institutonata.suprihub.contract.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras puras da contratação (Edital 9 e 10; Anexos VIII e IX).
 */
public final class ContractRules {

    private ContractRules() {
    }

    /** As cinco notas do Termo de Recebimento (1 a 5). */
    public record Scores(int deadline, int quality, int documentation, int service, int safety) {

        public List<Integer> all() {
            return List.of(deadline, quality, documentation, service, safety);
        }

        /** Média com 2 casas (Anexo IX, linha "Média"). */
        public BigDecimal average() {
            int sum = all().stream().mapToInt(Integer::intValue).sum();
            return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        }
    }

    public record Issue(String field, String message) {
    }

    public static List<Issue> checkScores(Scores s) {
        List<Issue> issues = new ArrayList<>();
        String[] fields = {"scoreDeadline", "scoreQuality", "scoreDocumentation", "scoreService", "scoreSafety"};
        List<Integer> all = s.all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) < 1 || all.get(i) > 5) {
                issues.add(new Issue(fields[i], "Dê uma nota de 1 a 5."));
            }
        }
        return issues;
    }

    /** Sinal só para eventos e alimentação, até a fração máxima do valor (Edital 10.4). */
    public static boolean upfrontAllowed(BigDecimal upfront, BigDecimal total, BigDecimal maxShare) {
        if (upfront == null || upfront.signum() == 0) {
            return true;
        }
        return upfront.signum() > 0 && upfront.compareTo(total.multiply(maxShare)) <= 0;
    }

    /**
     * Edital 9.5: médias abaixo do limite em {@code occurrences} contratações nos últimos
     * {@code windowMonths} meses ensejam processo de suspensão.
     */
    public static boolean suspensionAlert(List<Rated> history, LocalDate today, BigDecimal threshold,
            int occurrences, int windowMonths) {
        LocalDate since = today.minusMonths(windowMonths);
        long low = history.stream()
                .filter(r -> !r.date().isBefore(since))
                .filter(r -> r.average().compareTo(threshold) < 0)
                .count();
        return low >= occurrences;
    }

    public record Rated(LocalDate date, BigDecimal average) {
    }

    /** Média das avaliações no período (desempate 8.4); nula se não há avaliação. */
    public static BigDecimal ratingSince(List<Rated> history, LocalDate since) {
        List<BigDecimal> values = history.stream().filter(r -> !r.date().isBefore(since)).map(Rated::average).toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Preço unitário efetivo de cada item para o Banco de Preços: se o valor contratado difere
     * da soma dos itens (ex.: cobriu o menor preço), aplica a mesma proporção a todos.
     * Itens sem preço: só se houver um único item (valor total ÷ quantidade).
     */
    public static List<BigDecimal> effectiveUnitPrices(List<BigDecimal> unitPrices, List<BigDecimal> quantities,
            BigDecimal total) {
        int n = unitPrices.size();
        List<BigDecimal> out = new ArrayList<>();
        boolean allPriced = unitPrices.stream().allMatch(p -> p != null);
        if (allPriced && n > 0) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                sum = sum.add(unitPrices.get(i).multiply(quantities.get(i)));
            }
            for (int i = 0; i < n; i++) {
                BigDecimal p = sum.signum() == 0 ? unitPrices.get(i)
                        : unitPrices.get(i).multiply(total).divide(sum, 2, RoundingMode.HALF_UP);
                out.add(p);
            }
            return out;
        }
        if (n == 1 && quantities.get(0).signum() > 0) {
            out.add(total.divide(quantities.get(0), 2, RoundingMode.HALF_UP));
            return out;
        }
        for (int i = 0; i < n; i++) {
            out.add(null);
        }
        return out;
    }
}
