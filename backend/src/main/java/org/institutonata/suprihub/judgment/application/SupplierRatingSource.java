package org.institutonata.suprihub.judgment.application;

import java.math.BigDecimal;

/**
 * Média das avaliações de desempenho do fornecedor (Termos de Recebimento) para o critério
 * "melhor avaliação" do desempate (Edital 8.4). Implementada pelo módulo de contratação.
 */
public interface SupplierRatingSource {

    /** Média no período configurado; nula se o fornecedor ainda não foi avaliado. */
    BigDecimal ratingOf(long applicationId);
}
