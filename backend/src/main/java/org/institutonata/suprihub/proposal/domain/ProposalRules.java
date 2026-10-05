package org.institutonata.suprihub.proposal.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validação e total da proposta (Anexo VII). Regra pura.
 * <ul>
 *   <li>{@link Mode#ONLINE}: preço unitário de todos os itens; total = Σ preço × quantidade.</li>
 *   <li>{@link Mode#MODEL} e {@link Mode#OWN_FILE}: arquivo obrigatório e valor total digitado
 *       (é o valor que entra no julgamento); preços por item opcionais — se vierem todos, o
 *       total é calculado a partir deles.</li>
 *   <li>Validade mínima (parâmetro, 30 dias — Edital 7.4) e declaração do Anexo VII.</li>
 * </ul>
 */
public final class ProposalRules {

    private ProposalRules() {
    }

    public enum Mode {
        ONLINE("Preenchida no sistema"),
        MODEL("Modelo do Instituto assinado"),
        OWN_FILE("Proposta própria");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Issue(String field, String message) {
    }

    /** Item pedido: id e quantidade. */
    public record RequestedItem(Long id, BigDecimal quantity) {
    }

    public record Input(Mode mode, Map<Long, BigDecimal> unitPrices, BigDecimal typedTotal, int validityDays,
            boolean hasFile, boolean declarationAccepted) {
    }

    public static List<Issue> check(Input in, List<RequestedItem> items, int minValidityDays) {
        List<Issue> issues = new ArrayList<>();
        if (in.mode() == null) {
            issues.add(new Issue("mode", "Escolha como vai enviar a proposta."));
            return issues;
        }
        for (Map.Entry<Long, BigDecimal> e : in.unitPrices().entrySet()) {
            if (items.stream().noneMatch(i -> i.id().equals(e.getKey()))) {
                issues.add(new Issue("items", "Item inexistente nesta oportunidade."));
            } else if (e.getValue() == null || e.getValue().signum() < 0) {
                issues.add(new Issue("items." + e.getKey(), "Preço inválido."));
            }
        }
        boolean allPriced = items.stream().allMatch(i -> in.unitPrices().get(i.id()) != null);
        if (in.mode() == Mode.ONLINE) {
            for (RequestedItem i : items) {
                if (in.unitPrices().get(i.id()) == null) {
                    issues.add(new Issue("items." + i.id(), "Informe o preço unitário."));
                }
            }
        } else {
            if (!in.hasFile()) {
                issues.add(new Issue("file", in.mode() == Mode.MODEL
                        ? "Envie o modelo preenchido e assinado (foto ou PDF)."
                        : "Envie o arquivo da sua proposta."));
            }
            if (!allPriced && (in.typedTotal() == null || in.typedTotal().signum() <= 0)) {
                issues.add(new Issue("totalValue", "Informe o valor total da proposta."));
            }
        }
        if (in.validityDays() < minValidityDays) {
            issues.add(new Issue("validityDays",
                    "A proposta deve valer por pelo menos " + minValidityDays + " dias."));
        }
        if (!in.declarationAccepted()) {
            issues.add(new Issue("declarationAccepted", "Confirme a declaração do Anexo VII."));
        }
        if (issues.isEmpty() && total(in, items).signum() <= 0) {
            issues.add(new Issue("totalValue", "O valor total deve ser maior que zero."));
        }
        return issues;
    }

    /** Total da proposta: pelos itens, quando todos têm preço; senão, o valor digitado. */
    public static BigDecimal total(Input in, List<RequestedItem> items) {
        boolean allPriced = !items.isEmpty() && items.stream().allMatch(i -> in.unitPrices().get(i.id()) != null);
        if (allPriced) {
            return items.stream()
                    .map(i -> in.unitPrices().get(i.id()).multiply(i.quantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return in.typedTotal() == null ? BigDecimal.ZERO : in.typedTotal().setScale(2, RoundingMode.HALF_UP);
    }
}
