package org.institutonata.suprihub.proposal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.institutonata.suprihub.proposal.domain.ProposalRules.Input;
import org.institutonata.suprihub.proposal.domain.ProposalRules.Issue;
import org.institutonata.suprihub.proposal.domain.ProposalRules.Mode;
import org.institutonata.suprihub.proposal.domain.ProposalRules.RequestedItem;
import org.junit.jupiter.api.Test;

class ProposalRulesTest {

    static final List<RequestedItem> ITEMS = List.of(new RequestedItem(10L, new BigDecimal("100")),
            new RequestedItem(11L, new BigDecimal("2.5")));

    static List<String> fields(List<Issue> issues) {
        return issues.stream().map(Issue::field).toList();
    }

    @Test
    void onlineCalculaOTotalPelosItens() {
        Input in = new Input(Mode.ONLINE, Map.of(10L, new BigDecimal("12.50"), 11L, new BigDecimal("40")),
                new BigDecimal("999"), 30, false, true);
        assertThat(ProposalRules.check(in, ITEMS, 30)).isEmpty();
        assertThat(ProposalRules.total(in, ITEMS)).isEqualByComparingTo("1350.00");
    }

    @Test
    void onlineExigePrecoDeTodosOsItens() {
        Input in = new Input(Mode.ONLINE, Map.of(10L, new BigDecimal("12.50")), null, 30, false, true);
        assertThat(fields(ProposalRules.check(in, ITEMS, 30))).containsExactly("items.11");
    }

    @Test
    void comArquivoExigeArquivoEValorTotal() {
        Input semNada = new Input(Mode.OWN_FILE, Map.of(), null, 30, false, true);
        assertThat(fields(ProposalRules.check(semNada, ITEMS, 30))).containsExactlyInAnyOrder("file", "totalValue");
        Input ok = new Input(Mode.MODEL, Map.of(), new BigDecimal("1500"), 45, true, true);
        assertThat(ProposalRules.check(ok, ITEMS, 30)).isEmpty();
        assertThat(ProposalRules.total(ok, ITEMS)).isEqualByComparingTo("1500.00");
    }

    @Test
    void validadeMinimaEDeclaracao() {
        Input in = new Input(Mode.MODEL, Map.of(), new BigDecimal("10"), 15, true, false);
        assertThat(fields(ProposalRules.check(in, ITEMS, 30)))
                .containsExactlyInAnyOrder("validityDays", "declarationAccepted");
    }

    @Test
    void itemQueNaoEhDaOportunidadeERecusado() {
        Input in = new Input(Mode.ONLINE, Map.of(10L, BigDecimal.ONE, 11L, BigDecimal.ONE, 99L, BigDecimal.ONE),
                null, 30, false, true);
        assertThat(fields(ProposalRules.check(in, ITEMS, 30))).containsExactly("items");
    }
}
