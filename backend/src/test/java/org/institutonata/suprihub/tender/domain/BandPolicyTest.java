package org.institutonata.suprihub.tender.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class BandPolicyTest {

    // Valores de 2026 (Decreto nº 12.807/2025)
    private final BandPolicy policy2026 = new BandPolicy(new BigDecimal("13098.41"), new BigDecimal("65492.11"));

    @ParameterizedTest
    @CsvSource({
            "0.01, FAIXA_1",
            "3900.00, FAIXA_1",
            "13098.41, FAIXA_1",
            "13098.42, FAIXA_2",
            "18000, FAIXA_2",
            "65492.11, FAIXA_2",
            "65492.12, FAIXA_3",
            "250000, FAIXA_3"})
    void limitesDasFaixas(String value, Band expected) {
        assertThat(policy2026.classify(new BigDecimal(value))).isEqualTo(expected);
    }

    @Test
    void novosLimitesMudamOEnquadramentoSemAlterarCodigo() {
        BandPolicy policy2027 = new BandPolicy(new BigDecimal("13500.00"), new BigDecimal("67500.00"));
        assertThat(policy2026.classify(new BigDecimal("13200"))).isEqualTo(Band.FAIXA_2);
        assertThat(policy2027.classify(new BigDecimal("13200"))).isEqualTo(Band.FAIXA_1);
    }

    @Test
    void valorInvalido() {
        assertThatThrownBy(() -> policy2026.classify(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BandPolicy(BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
