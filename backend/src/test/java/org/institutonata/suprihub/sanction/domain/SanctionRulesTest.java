package org.institutonata.suprihub.sanction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.institutonata.suprihub.sanction.domain.SanctionEnums.Sanction;
import org.institutonata.suprihub.sanction.domain.SanctionRules.Decision;
import org.institutonata.suprihub.sanction.domain.SanctionRules.Issue;
import org.junit.jupiter.api.Test;

class SanctionRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Test
    void multaSoQuandoPrevistaNaOrdem() {
        Decision fine = new Decision(Sanction.FINE, new BigDecimal("5"), null, null);
        assertThat(SanctionRules.check(fine, null)).extracting(Issue::field).containsExactly("sanction");
        assertThat(SanctionRules.check(fine, new BigDecimal("10"))).isEmpty();
        assertThat(SanctionRules.check(fine, new BigDecimal("3"))).extracting(Issue::field).containsExactly("finePct");
        assertThat(SanctionRules.check(new Decision(Sanction.FINE, new BigDecimal("1"), null, null),
                BigDecimal.TEN)).extracting(Issue::field).containsExactly("finePct");
    }

    @Test
    void limitesDeSuspensaoEImpedimento() {
        assertThat(SanctionRules.check(new Decision(Sanction.SUSPENSION, null, 25, null), null)).hasSize(1);
        assertThat(SanctionRules.check(new Decision(Sanction.SUSPENSION, null, 24, null), null)).isEmpty();
        assertThat(SanctionRules.check(new Decision(Sanction.DEREGISTRATION, null, null, 4), null)).hasSize(1);
        assertThat(SanctionRules.check(new Decision(Sanction.DEREGISTRATION, null, null, 3), null)).isEmpty();
        assertThat(SanctionRules.check(new Decision(Sanction.WARNING, null, null, null), null)).isEmpty();
    }

    @Test
    void vigencia() {
        assertThat(SanctionRules.effectiveUntil(new Decision(Sanction.SUSPENSION, null, 6, null), TODAY))
                .isEqualTo(LocalDate.of(2027, 4, 5));
        assertThat(SanctionRules.effectiveUntil(new Decision(Sanction.DEREGISTRATION, null, null, 3), TODAY))
                .isEqualTo(LocalDate.of(2029, 10, 5));
        assertThat(SanctionRules.effectiveUntil(new Decision(Sanction.WARNING, null, null, null), TODAY)).isNull();
    }

    @Test
    void reincidenciaEmCincoAnos() {
        assertThat(SanctionRules.recidivist(List.of(TODAY.minusYears(4)), TODAY, 5)).isTrue();
        assertThat(SanctionRules.recidivist(List.of(TODAY.minusYears(5).minusDays(1)), TODAY, 5)).isFalse();
        assertThat(SanctionRules.recidivist(List.of(), TODAY, 5)).isFalse();
    }
}
