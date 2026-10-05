package org.institutonata.suprihub.tender.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Enquadramento na faixa a partir do valor estimado e dos limites vigentes (parâmetros).
 * ≤ limite 1 → Faixa 1; ≤ limite 2 → Faixa 2; acima → Faixa 3. Componente puro.
 *
 * <p>Anti-fracionamento (Política art. 6º, parágrafo único): o chamador deve informar o
 * valor somado das demandas previsíveis do mesmo objeto, no mesmo projeto, no exercício.
 */
public record BandPolicy(BigDecimal band1UpperLimit, BigDecimal band2UpperLimit) {

    public BandPolicy {
        Objects.requireNonNull(band1UpperLimit);
        Objects.requireNonNull(band2UpperLimit);
        if (band1UpperLimit.compareTo(band2UpperLimit) >= 0) {
            throw new IllegalArgumentException("Limite da Faixa 1 deve ser menor que o da Faixa 2");
        }
    }

    public Band classify(BigDecimal estimatedValue) {
        Objects.requireNonNull(estimatedValue);
        if (estimatedValue.signum() <= 0) {
            throw new IllegalArgumentException("Valor estimado deve ser positivo");
        }
        if (estimatedValue.compareTo(band1UpperLimit) <= 0) {
            return Band.FAIXA_1;
        }
        if (estimatedValue.compareTo(band2UpperLimit) <= 0) {
            return Band.FAIXA_2;
        }
        return Band.FAIXA_3;
    }
}
