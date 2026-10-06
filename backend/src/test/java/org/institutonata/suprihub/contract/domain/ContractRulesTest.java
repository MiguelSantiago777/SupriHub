package org.institutonata.suprihub.contract.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.institutonata.suprihub.contract.domain.ContractRules.Rated;
import org.institutonata.suprihub.contract.domain.ContractRules.Scores;
import org.junit.jupiter.api.Test;

class ContractRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Test
    void mediaDoTermo() {
        assertThat(new Scores(5, 4, 4, 5, 3).average()).isEqualByComparingTo("4.20");
        assertThat(new Scores(1, 1, 1, 1, 2).average()).isEqualByComparingTo("1.20");
        assertThat(ContractRules.checkScores(new Scores(0, 6, 3, 3, 3))).extracting(ContractRules.Issue::field)
                .containsExactly("scoreDeadline", "scoreQuality");
    }

    @Test
    void sinalLimitadoA30PorCento() {
        BigDecimal total = new BigDecimal("1000");
        BigDecimal max = new BigDecimal("0.30");
        assertThat(ContractRules.upfrontAllowed(null, total, max)).isTrue();
        assertThat(ContractRules.upfrontAllowed(new BigDecimal("300"), total, max)).isTrue();
        assertThat(ContractRules.upfrontAllowed(new BigDecimal("300.01"), total, max)).isFalse();
    }

    /** Edital 9.5: duas médias abaixo de 3 em 12 meses. */
    @Test
    void alertaDeSuspensao() {
        BigDecimal three = BigDecimal.valueOf(3);
        List<Rated> one = List.of(new Rated(TODAY.minusMonths(2), new BigDecimal("2.4")),
                new Rated(TODAY.minusMonths(1), new BigDecimal("4.0")));
        assertThat(ContractRules.suspensionAlert(one, TODAY, three, 2, 12)).isFalse();
        List<Rated> two = List.of(new Rated(TODAY.minusMonths(11), new BigDecimal("2.8")),
                new Rated(TODAY, new BigDecimal("2.0")));
        assertThat(ContractRules.suspensionAlert(two, TODAY, three, 2, 12)).isTrue();
        List<Rated> old = List.of(new Rated(TODAY.minusMonths(13), new BigDecimal("2.0")),
                new Rated(TODAY, new BigDecimal("2.0")));
        assertThat(ContractRules.suspensionAlert(old, TODAY, three, 2, 12)).isFalse();
        // exatamente 3 não é insuficiente
        assertThat(ContractRules.suspensionAlert(List.of(new Rated(TODAY, three), new Rated(TODAY, three)), TODAY,
                three, 2, 12)).isFalse();
    }

    @Test
    void mediaDasAvaliacoesNoPeriodo() {
        List<Rated> h = List.of(new Rated(TODAY.minusMonths(30), new BigDecimal("1.0")),
                new Rated(TODAY.minusMonths(3), new BigDecimal("4.0")), new Rated(TODAY, new BigDecimal("5.0")));
        assertThat(ContractRules.ratingSince(h, TODAY.minusMonths(24))).isEqualByComparingTo("4.50");
        assertThat(ContractRules.ratingSince(List.of(), TODAY)).isNull();
    }

    @Test
    void precoEfetivoParaOBancoDePrecos() {
        // Proposta 200 × 20 = 4.000; cobriu com 3.800 → preço unitário 19,00
        assertThat(ContractRules.effectiveUnitPrices(List.of(new BigDecimal("20")), List.of(new BigDecimal("200")),
                new BigDecimal("3800"))).containsExactly(new BigDecimal("19.00"));
        // Item único sem preço: total ÷ quantidade
        assertThat(ContractRules.effectiveUnitPrices(Arrays.asList((BigDecimal) null), List.of(new BigDecimal("8")),
                new BigDecimal("2000"))).containsExactly(new BigDecimal("250.00"));
        // Vários itens sem preço: não registra
        assertThat(ContractRules.effectiveUnitPrices(Arrays.asList(null, null),
                List.of(BigDecimal.ONE, BigDecimal.TEN), new BigDecimal("100"))).containsOnlyNulls();
    }
}
