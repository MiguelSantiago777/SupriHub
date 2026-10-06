package org.institutonata.suprihub.judgment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Advantage;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Bid;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Entry;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Params;
import org.institutonata.suprihub.judgment.domain.JudgmentEngine.Result;
import org.junit.jupiter.api.Test;

class JudgmentEngineTest {

    static final Params PARAMS = new Params(new BigDecimal("0.10"), new BigDecimal("0.10"), false);

    static Bid bid(long id, String total, int level, boolean woman, boolean black, boolean mei, String rating) {
        return new Bid(id, new BigDecimal(total), true, level, woman, black, mei,
                rating == null ? null : new BigDecimal(rating));
    }

    static List<Long> order(Result r) {
        return r.entries().stream().map(e -> e.bid().proposalId()).toList();
    }

    /** Manual 4.2 (exemplo): A (EPP, T2) 3.900; B (MEI, mulher, T1) 4.200; C (ME, T3) 3.850. */
    @Test
    void exemploDoManual() {
        Bid a = bid(1, "3900", 2, false, false, false, null);
        Bid b = bid(2, "4200", 1, true, false, true, null);
        Bid c = bid(3, "3850", 3, false, false, false, null);
        Result r = JudgmentEngine.judge(List.of(a, b, c), PARAMS, 42);

        assertThat(r.lowest()).isEqualByComparingTo("3850");
        assertThat(order(r)).containsExactly(2L, 1L, 3L);
        Entry first = r.entries().get(0);
        assertThat(first.fictitiousTie()).isTrue();
        assertThat(first.pctAbove()).isEqualByComparingTo("0.090909");
        assertThat(first.advantage()).isEqualTo(Advantage.LOCAL_PREFERENCE);
        assertThat(first.mustCover()).isTrue();
        assertThat(r.entries().get(1).advantage()).isEqualTo(Advantage.LOCAL_PREFERENCE);
        assertThat(r.entries().get(1).pctAbove()).isEqualByComparingTo("0.012987");
        assertThat(r.entries().get(2).advantage()).isEqualTo(Advantage.LOWEST_PRICE);
        assertThat(r.entries().get(2).mustCover()).isFalse();
        assertThat(r.drawUsed()).isFalse();
    }

    @Test
    void foraDaMargemNaoEntraNoEmpate() {
        Bid low = bid(1, "1000", 3, false, false, false, null);
        Bid justIn = bid(2, "1100", 1, false, false, false, null);   // exatamente 10%
        Bid out = bid(3, "1100.01", 1, true, true, true, null);      // 10,001%
        Result r = JudgmentEngine.judge(List.of(low, justIn, out), PARAMS, 1);
        assertThat(order(r)).containsExactly(2L, 1L, 3L);
        Entry last = r.entries().get(2);
        assertThat(last.finalist()).isFalse();
        assertThat(last.fictitiousTie()).isFalse();
        assertThat(last.position()).isEqualTo(3);
    }

    @Test
    void naoPequenoNegocioSoEntraComPreferenciaParaTodos() {
        Bid low = bid(1, "1000", 3, false, false, false, null);
        Bid big = new Bid(2, new BigDecimal("1050"), false, 1, false, false, false, null);
        assertThat(JudgmentEngine.judge(List.of(low, big), PARAMS, 1).entries().get(1).finalist()).isFalse();

        Params forAll = new Params(new BigDecimal("0.10"), new BigDecimal("0.10"), true);
        Result r = JudgmentEngine.judge(List.of(low, big), forAll, 1);
        assertThat(order(r)).containsExactly(2L, 1L);
        assertThat(r.entries().get(0).localPreference()).isTrue();
        assertThat(r.entries().get(0).advantage()).isEqualTo(Advantage.LOCAL_PREFERENCE);
    }

    @Test
    void ordemDoDesempatePorEquidade() {
        Bid low = bid(1, "1000", 1, false, false, false, "5");
        Bid mei = bid(2, "1010", 1, false, false, true, null);
        Bid woman = bid(3, "1090", 1, true, false, false, null);
        Bid blackWoman = bid(4, "1095", 1, true, true, false, null);
        Result r = JudgmentEngine.judge(List.of(low, mei, woman, blackWoman), PARAMS, 1);
        assertThat(order(r)).containsExactly(4L, 3L, 2L, 1L);
        assertThat(r.entries().get(0).advantage()).isEqualTo(Advantage.WOMAN_LED);
        assertThat(r.entries().get(1).advantage()).isEqualTo(Advantage.WOMAN_LED);
        assertThat(r.entries().get(2).advantage()).isEqualTo(Advantage.MEI);
    }

    @Test
    void avaliacaoDepoisMenorPercentual() {
        Bid low = bid(1, "1000", 2, false, false, false, "3");
        Bid better = bid(2, "1080", 2, false, false, false, "4.5");
        Bid sameRatingCheaper = bid(3, "1040", 2, false, false, false, "4.5");
        Result r = JudgmentEngine.judge(List.of(low, better, sameRatingCheaper), PARAMS, 1);
        assertThat(order(r)).containsExactly(3L, 2L, 1L);
        assertThat(r.entries().get(0).advantage()).isEqualTo(Advantage.RATING);
    }

    @Test
    void empateExatoVaiASorteioReproduzivel() {
        Bid a = bid(10, "500", 1, true, false, true, null);
        Bid b = bid(11, "500", 1, true, false, true, null);
        Bid c = bid(12, "500", 1, true, false, true, null);
        Result r1 = JudgmentEngine.judge(List.of(a, b, c), PARAMS, 2026);
        Result r2 = JudgmentEngine.judge(List.of(c, a, b), PARAMS, 2026);
        assertThat(r1.drawUsed()).isTrue();
        assertThat(r1.drawGroups()).hasSize(1);
        assertThat(r1.drawGroups().get(0)).containsExactlyInAnyOrder(10L, 11L, 12L);
        assertThat(order(r1)).isEqualTo(order(r2)).isEqualTo(r1.drawGroups().get(0));
        assertThat(r1.entries()).allMatch(Entry::drawn).allMatch(e -> !e.mustCover());
        // A semente muda o resultado (alguma das 50 diferentes dá outra ordem).
        boolean differs = false;
        for (long s = 0; s < 50 && !differs; s++) {
            differs = !order(JudgmentEngine.judge(List.of(a, b, c), PARAMS, s)).equals(order(r1));
        }
        assertThat(differs).isTrue();
    }

    @Test
    void finalistaAbaixoDaMenorNaoTemVantagem() {
        Bid low = bid(1, "1000", 1, true, false, false, null);
        Bid tied = bid(2, "1050", 2, false, false, false, null);
        Result r = JudgmentEngine.judge(List.of(low, tied), PARAMS, 1);
        assertThat(order(r)).containsExactly(1L, 2L);
        assertThat(r.entries().get(1).advantage()).isEqualTo(Advantage.NONE);
        assertThat(r.entries().get(1).finalist()).isTrue();
    }

    @Test
    void direitoDeCobrir() {
        BigDecimal lowest = new BigDecimal("3850");
        assertThat(JudgmentEngine.covers(new BigDecimal("3849.99"), lowest, true)).isTrue();
        assertThat(JudgmentEngine.covers(new BigDecimal("3850"), lowest, true)).isFalse();
        assertThat(JudgmentEngine.covers(new BigDecimal("3850"), lowest, false)).isTrue();
        assertThat(JudgmentEngine.covers(BigDecimal.ZERO, lowest, false)).isFalse();
    }

    /** D-011: onde a fórmula da planilha e a ordem lexicográfica coincidem, o vencedor é o mesmo. */
    @Test
    void paridadeComAPlanilha() {
        List<List<Bid>> cases = List.of(
                List.of(bid(1, "3900", 2, false, false, false, null), bid(2, "4200", 1, true, false, true, null),
                        bid(3, "3850", 3, false, false, false, null)),
                List.of(bid(1, "1000", 1, false, false, false, null), bid(2, "1050", 1, true, false, false, null)),
                List.of(bid(1, "1000", 2, true, false, false, null), bid(2, "1020", 2, true, true, false, null),
                        bid(3, "1001", 2, true, false, true, null)));
        for (List<Bid> bids : cases) {
            Result r = JudgmentEngine.judge(bids, PARAMS, 7);
            Entry spreadsheetWinner = r.finalists().stream()
                    .min(Comparator.comparing(e -> JudgmentEngine.spreadsheetScore(e.bid(), e.pctAbove())))
                    .orElseThrow();
            assertThat(r.entries().get(0).bid().proposalId()).isEqualTo(spreadsheetWinner.bid().proposalId());
        }
    }

    /** D-011: avaliação 4,0 com 0% acima vence 5,0 com 9% na planilha; aqui vence a melhor avaliação. */
    @Test
    void divergenciaDocumentadaComAPlanilha() {
        Bid rating4 = bid(1, "1000", 1, false, false, false, "4.0");
        Bid rating5 = bid(2, "1090", 1, false, false, false, "5.0");
        Result r = JudgmentEngine.judge(List.of(rating4, rating5), PARAMS, 1);
        Entry spreadsheetWinner = r.finalists().stream()
                .min(Comparator.comparing(e -> JudgmentEngine.spreadsheetScore(e.bid(), e.pctAbove())))
                .orElseThrow();
        assertThat(spreadsheetWinner.bid().proposalId()).isEqualTo(1L);
        assertThat(r.entries().get(0).bid().proposalId()).isEqualTo(2L);
    }
}
