package org.institutonata.suprihub.supplier.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AppealTest {

    static final Instant NOW = Instant.parse("2026-10-05T15:00:00Z");
    static final LocalDate DUE = LocalDate.of(2026, 10, 8);

    static Appeal filed() {
        return new Appeal(1L, 10L, " razões ", " pedido ", null, DUE, NOW);
    }

    @Test
    void comissaoReconsideraEProveORecurso() {
        Appeal a = filed();
        a.reconsider(20L, " ok ", NOW);
        assertThat(a.getStatus()).isEqualTo(Appeal.Status.GRANTED);
        assertThat(a.getCommissionReason()).isEqualTo("ok");
        assertThat(a.getStatus().isOpen()).isFalse();
    }

    @Test
    void encaminhadoSoADiretoriaDecide() {
        Appeal a = filed();
        a.forward(20L, "mantida", DUE.plusDays(7), NOW);
        assertThat(a.getStatus()).isEqualTo(Appeal.Status.FORWARDED);
        assertThat(a.getStatus().isOpen()).isTrue();
        assertThatThrownBy(() -> a.reconsider(20L, "x", NOW)).isInstanceOf(IllegalStateException.class);
        a.boardDecision(30L, false, "negado", NOW);
        assertThat(a.getStatus()).isEqualTo(Appeal.Status.DENIED);
        assertThatThrownBy(() -> a.boardDecision(30L, true, "x", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void diretoriaNaoDecideRecursoQueNaoFoiEncaminhado() {
        assertThatThrownBy(() -> filed().boardDecision(30L, true, "x", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void textosSaoAparados() {
        Appeal a = filed();
        assertThat(a.getReasons()).isEqualTo("razões");
        assertThat(a.getRequest()).isEqualTo("pedido");
        assertThat(a.hasAttachment()).isFalse();
    }
}
