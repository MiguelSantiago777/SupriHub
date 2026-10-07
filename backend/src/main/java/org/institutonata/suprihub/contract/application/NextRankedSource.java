package org.institutonata.suprihub.contract.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Próximo classificado de uma oportunidade julgada (Faixas 2 e 3), para convocar quando o
 * selecionado não assina a ordem (Edital 9.1). Implementado pelo módulo de julgamento.
 */
public interface NextRankedSource {

    /** Classificado com o preço da própria proposta (posição no mapa homologado). */
    record Ranked(long applicationId, Long proposalId, BigDecimal value, int position) {
    }

    /**
     * @param excluded fornecedores já convocados nesta oportunidade
     * @param active   se o fornecedor está ativo hoje (credenciamento, bloqueio, certidões)
     */
    Optional<Ranked> nextAfter(long tenderId, Set<Long> excluded, Predicate<Long> active);
}
