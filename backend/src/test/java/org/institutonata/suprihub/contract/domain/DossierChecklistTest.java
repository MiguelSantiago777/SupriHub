package org.institutonata.suprihub.contract.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.institutonata.suprihub.contract.domain.DossierChecklist.Facts;
import org.institutonata.suprihub.contract.domain.DossierChecklist.Item;
import org.institutonata.suprihub.contract.domain.DossierChecklist.Manual;
import org.institutonata.suprihub.contract.domain.DossierChecklist.State;
import org.institutonata.suprihub.tender.domain.Band;
import org.junit.jupiter.api.Test;

class DossierChecklistTest {

    static Facts complete(Band band) {
        return new Facts(band, true, band == Band.FAIXA_1 ? 1 : 0, true, true, band != Band.FAIXA_1,
                band != Band.FAIXA_1, true, true, true, true, true);
    }

    static State state(List<Item> items, int no) {
        return items.stream().filter(i -> i.no() == no).findFirst().orElseThrow().state();
    }

    @Test
    void faixa1SemMapaNemAta() {
        List<Item> items = DossierChecklist.evaluate(complete(Band.FAIXA_1), Map.of());
        assertThat(state(items, 3)).isEqualTo(State.OK);
        assertThat(state(items, 5)).isEqualTo(State.NOT_APPLICABLE);
        assertThat(state(items, 7)).isEqualTo(State.NOT_APPLICABLE);
        assertThat(state(items, 8)).isEqualTo(State.NOT_APPLICABLE);
        // DFD com atesto e estimativa ainda pendentes (manuais)
        assertThat(state(items, 1)).isEqualTo(State.PENDING);
        assertThat(state(items, 2)).isEqualTo(State.PENDING);
        assertThat(DossierChecklist.complete(items)).isFalse();
        items = DossierChecklist.evaluate(complete(Band.FAIXA_1),
                Map.of(1, new Manual(true, null), 2, new Manual(true, "Banco de Preços")));
        assertThat(DossierChecklist.complete(items)).isTrue();
    }

    @Test
    void faixa3ExigePublicacaoNoSitio() {
        Map<Integer, Manual> m = Map.of(1, new Manual(true, null), 2, new Manual(true, null));
        List<Item> items = DossierChecklist.evaluate(complete(Band.FAIXA_3), m);
        assertThat(state(items, 3)).isEqualTo(State.NOT_APPLICABLE);
        assertThat(state(items, 5)).isEqualTo(State.PENDING);
        assertThat(state(items, 14)).isEqualTo(State.NOT_APPLICABLE);
        assertThat(DossierChecklist.complete(items)).isFalse();
    }

    @Test
    void semPagamentoFicaPendente() {
        Facts f = new Facts(Band.FAIXA_2, true, 0, true, true, true, true, true, true, true, false, true);
        List<Item> items = DossierChecklist.evaluate(f, Map.of(1, new Manual(true, null), 2, new Manual(true, null)));
        assertThat(state(items, 12)).isEqualTo(State.PENDING);
        assertThat(DossierChecklist.isManual(12)).isFalse();
        assertThat(DossierChecklist.isManual(5)).isTrue();
    }
}
