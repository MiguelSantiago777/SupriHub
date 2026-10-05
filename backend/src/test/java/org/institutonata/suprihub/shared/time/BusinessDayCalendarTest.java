package org.institutonata.suprihub.shared.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Edital 12.2: exclui o dia do início, inclui o do vencimento; só vencem em dias úteis. */
class BusinessDayCalendarTest {

    // 2026: 12/10 (seg) N. Sra. Aparecida; 02/11 (seg) Finados; 20/11 (sex) Consciência Negra
    private final BusinessDayCalendar calendar = BusinessDayCalendar.of(Set.of(
            LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 20),
            LocalDate.of(2026, 12, 25)));

    @Test
    void excluiODiaDoInicio() {
        // segunda 05/10/2026 + 1 d.u. = terça 06/10
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 10, 5), 1)).isEqualTo(LocalDate.of(2026, 10, 6));
    }

    @Test
    void pulaFimDeSemana() {
        // sexta 02/10 + 1 d.u. = segunda 05/10
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 10, 2), 1)).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void pulaFeriado() {
        // sexta 09/10 + 1 d.u.: sáb, dom, seg 12/10 feriado → terça 13/10
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 10, 9), 1)).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    void prazoDeAnaliseDe10DiasUteis() {
        // requerimento completo em 05/10 (seg): 06,07,08,09,13,14,15,16,19,20 → 20/10
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 10, 5), 10)).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void inicioEmDiaNaoUtilComecaAContarNoProximoUtil() {
        // comunicado no sábado 03/10: 1º d.u. = segunda 05/10; 3 d.u. = quarta 07/10
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 10, 3), 3)).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    void prazoZeroCaiNoProprioDiaOuNoProximoUtil() {
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 10, 5), 0)).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 10, 10), 0)).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    void vencimentoSempreEmDiaUtil() {
        for (int d = 1; d <= 60; d++) {
            LocalDate due = calendar.addBusinessDays(LocalDate.of(2026, 10, 1), d);
            assertThat(calendar.isBusinessDay(due)).as("prazo de %d d.u.", d).isTrue();
        }
    }

    @Test
    void contagemEntreDatasExcluiInicioEIncluiFim() {
        LocalDate start = LocalDate.of(2026, 11, 16); // segunda
        LocalDate end = LocalDate.of(2026, 11, 23);   // segunda seguinte; sexta 20/11 é feriado
        assertThat(calendar.businessDaysBetween(start, end)).isEqualTo(4); // 17,18,19,23
        assertThat(calendar.businessDaysBetween(end, start)).isEqualTo(-4);
        assertThat(calendar.businessDaysBetween(start, start)).isZero();
    }

    @Test
    void addEBetweenSaoInversos() {
        LocalDate start = LocalDate.of(2026, 12, 18);
        for (int d = 1; d <= 30; d++) {
            assertThat(calendar.businessDaysBetween(start, calendar.addBusinessDays(start, d))).isEqualTo(d);
        }
    }

    @Test
    void prazoNegativoInvalido() {
        assertThatThrownBy(() -> calendar.addBusinessDays(LocalDate.of(2026, 10, 5), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
