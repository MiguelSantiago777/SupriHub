package org.institutonata.suprihub.judgment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Julgamento das Faixas 2 e 3 (Edital 8.2 a 8.4; Manual 4.2, passo 6). Regra pura: recebe as
 * propostas válidas e devolve a classificação, sem banco nem relógio.
 *
 * <ol>
 *   <li><b>Menor preço.</b></li>
 *   <li><b>Empate ficto (8.2):</b> proposta de pequeno negócio até {@code tieMargin} acima da
 *       menor é considerada empatada com ela.</li>
 *   <li><b>Preferência local (8.3):</b> entre as empatadas, o território da sede mais próximo
 *       (T1 &lt; T2 &lt; T3 &lt; fora do estado). Com {@code localPreferenceForAll} (D-032), quem
 *       não é pequeno negócio também entra se estiver até {@code localMargin} acima e num
 *       território melhor que o da menor proposta.</li>
 *   <li><b>Desempate por equidade (8.4):</b> negócio liderado por mulher → por mulher negra ou
 *       inscrita no CadÚnico → MEI → melhor avaliação.</li>
 *   <li><b>Menor % acima</b> (D-011) e, persistindo empate exato, <b>sorteio</b> (7.6).</li>
 * </ol>
 *
 * A ordem é lexicográfica (D-011), e não a soma de pesos da planilha. O sorteio usa a semente
 * recebida: com a mesma semente, o resultado é sempre o mesmo (auditável).
 */
public final class JudgmentEngine {

    /** Escala dos percentuais (6 casas). */
    public static final int SCALE = 6;

    private JudgmentEngine() {
    }

    public record Params(BigDecimal tieMargin, BigDecimal localMargin, boolean localPreferenceForAll) {
        public Params {
            Objects.requireNonNull(tieMargin);
            Objects.requireNonNull(localMargin);
        }
    }

    /**
     * Proposta válida. {@code territoryLevel}: 1 = T1, 2 = T2, 3 = T3, 4 = fora do estado.
     * {@code rating} nulo = sem avaliação (conta como zero, como na planilha).
     */
    public record Bid(long proposalId, BigDecimal total, boolean smallBusiness, int territoryLevel,
            boolean womanLed, boolean blackWomanOrCadUnico, boolean mei, BigDecimal rating) {
        public Bid {
            Objects.requireNonNull(total);
            if (total.signum() <= 0) {
                throw new IllegalArgumentException("Valor da proposta deve ser positivo");
            }
        }

        BigDecimal ratingOrZero() {
            return rating == null ? BigDecimal.ZERO : rating;
        }
    }

    /** O que pôs a proposta à frente da de menor preço (para o motivo na ata). */
    public enum Advantage {
        LOWEST_PRICE("menor preço"),
        LOCAL_PREFERENCE("preferência local (item 8.3 do Edital)"),
        WOMAN_LED("negócio liderado por mulher (item 8.4 do Edital)"),
        BLACK_WOMAN_OR_CADUNICO("negócio liderado por mulher negra ou inscrita no CadÚnico (item 8.4 do Edital)"),
        MEI("MEI (item 8.4 do Edital)"),
        RATING("melhor avaliação de desempenho (item 8.4 do Edital)"),
        /** Finalista classificada depois da de menor preço: não chega a ser convocada. */
        NONE("—");

        private final String label;

        Advantage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Para o fornecedor: sem revelar raça/CadÚnico de ninguém (Política art. 28). */
        public boolean isEquity() {
            return this == WOMAN_LED || this == BLACK_WOMAN_OR_CADUNICO || this == MEI || this == RATING;
        }
    }

    /**
     * Linha do mapa comparativo. {@code position} começa em 1. As finalistas (menor preço e
     * empatadas) vêm primeiro, na ordem de convocação; depois as demais, por preço.
     */
    public record Entry(Bid bid, int position, BigDecimal pctAbove, boolean fictitiousTie, boolean localPreference,
            boolean finalist, boolean drawn, Advantage advantage) {

        public boolean isLowest() {
            return pctAbove.signum() == 0;
        }

        /** Só quem está acima da menor precisa cobrir (8.2). */
        public boolean mustCover() {
            return finalist && !isLowest();
        }
    }

    /** {@code drawGroups}: ids das propostas sorteadas entre si, na ordem resultante. */
    public record Result(BigDecimal lowest, List<Entry> entries, List<List<Long>> drawGroups) {

        public boolean drawUsed() {
            return !drawGroups.isEmpty();
        }

        public List<Entry> finalists() {
            return entries.stream().filter(Entry::finalist).toList();
        }
    }

    /** Critérios 8.3 e 8.4 + menor % acima (o sorteio decide o que sobrar). */
    private static final Comparator<Entry> PRIORITY = Comparator
            .comparingInt((Entry e) -> e.bid().territoryLevel())
            .thenComparing(e -> !e.bid().womanLed())
            .thenComparing(e -> !e.bid().blackWomanOrCadUnico())
            .thenComparing(e -> !e.bid().mei())
            .thenComparing((Entry e) -> e.bid().ratingOrZero(), Comparator.reverseOrder())
            .thenComparing(Entry::pctAbove);

    public static Result judge(List<Bid> bids, Params params, long seed) {
        if (bids.isEmpty()) {
            throw new IllegalArgumentException("Sem propostas válidas");
        }
        BigDecimal lowest = bids.stream().map(Bid::total).min(Comparator.naturalOrder()).orElseThrow();
        int lowestLevel = bids.stream().filter(b -> b.total().compareTo(lowest) == 0)
                .mapToInt(Bid::territoryLevel).min().orElseThrow();

        List<Entry> finalists = new ArrayList<>();
        List<Entry> others = new ArrayList<>();
        for (Bid b : bids) {
            BigDecimal pct = b.total().divide(lowest, SCALE + 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            boolean above = pct.signum() > 0;
            boolean tie = above && b.smallBusiness() && pct.compareTo(params.tieMargin()) <= 0;
            boolean local = above && !tie && params.localPreferenceForAll()
                    && pct.compareTo(params.localMargin()) <= 0 && b.territoryLevel() < lowestLevel;
            boolean finalist = !above || tie || local;
            Entry e = new Entry(b, 0, pct, tie, local, finalist, false, null);
            (finalist ? finalists : others).add(e);
        }

        // Ordena por prioridade; empates exatos (todos os critérios iguais) vão a sorteio.
        finalists.sort(PRIORITY.thenComparingLong(e -> e.bid().proposalId()));
        SplittableRandom random = new SplittableRandom(seed);
        List<List<Long>> drawGroups = new ArrayList<>();
        List<Entry> ordered = new ArrayList<>();
        int i = 0;
        while (i < finalists.size()) {
            int j = i + 1;
            while (j < finalists.size() && PRIORITY.compare(finalists.get(i), finalists.get(j)) == 0) {
                j++;
            }
            List<Entry> group = new ArrayList<>(finalists.subList(i, j));
            if (group.size() > 1) {
                for (int k = group.size() - 1; k > 0; k--) {
                    int r = random.nextInt(k + 1);
                    Entry tmp = group.get(k);
                    group.set(k, group.get(r));
                    group.set(r, tmp);
                }
                drawGroups.add(group.stream().map(e -> e.bid().proposalId()).toList());
                group = group.stream().map(e -> new Entry(e.bid(), 0, e.pctAbove(), e.fictitiousTie(),
                        e.localPreference(), true, true, null)).toList();
            }
            ordered.addAll(group);
            i = j;
        }

        // Referência para o motivo: a primeira proposta de menor preço na classificação.
        Entry reference = ordered.stream().filter(Entry::isLowest).findFirst().orElseThrow();
        int referenceIndex = ordered.indexOf(reference);

        List<Entry> result = new ArrayList<>();
        int pos = 1;
        for (int k = 0; k < ordered.size(); k++) {
            Entry e = ordered.get(k);
            Advantage adv = k > referenceIndex ? Advantage.NONE
                    : e.isLowest() ? Advantage.LOWEST_PRICE : advantage(e.bid(), reference.bid());
            result.add(new Entry(e.bid(), pos++, e.pctAbove(), e.fictitiousTie(), e.localPreference(), true,
                    e.drawn(), adv));
        }
        others.sort(Comparator.comparing((Entry e) -> e.bid().total()).thenComparing(PRIORITY)
                .thenComparingLong(e -> e.bid().proposalId()));
        for (Entry e : others) {
            result.add(new Entry(e.bid(), pos++, e.pctAbove(), false, false, false, false, Advantage.NONE));
        }
        return new Result(lowest, List.copyOf(result), List.copyOf(drawGroups));
    }

    /** Primeiro critério (8.3, 8.4) em que {@code b} vence a de menor preço. */
    static Advantage advantage(Bid b, Bid lowest) {
        if (b.territoryLevel() != lowest.territoryLevel()) {
            return Advantage.LOCAL_PREFERENCE;
        }
        if (b.womanLed() != lowest.womanLed()) {
            return Advantage.WOMAN_LED;
        }
        if (b.blackWomanOrCadUnico() != lowest.blackWomanOrCadUnico()) {
            return Advantage.BLACK_WOMAN_OR_CADUNICO;
        }
        if (b.mei() != lowest.mei()) {
            return Advantage.MEI;
        }
        return Advantage.RATING;
    }

    /** Direito de cobrir (8.2; D-010): valor inferior à menor proposta, ou igual se o parâmetro permitir. */
    public static boolean covers(BigDecimal offered, BigDecimal lowest, boolean strictlyLower) {
        int cmp = offered.compareTo(lowest);
        return offered.signum() > 0 && (strictlyLower ? cmp < 0 : cmp <= 0);
    }

    /** Pontuação da aba Cotações da planilha (menor = melhor), só para o teste de paridade (D-011). */
    public static BigDecimal spreadsheetScore(Bid b, BigDecimal pctAbove) {
        return BigDecimal.valueOf(b.territoryLevel() * 1000L)
                .subtract(BigDecimal.valueOf(b.womanLed() ? 100 : 0))
                .subtract(BigDecimal.valueOf(b.blackWomanOrCadUnico() ? 10 : 0))
                .subtract(BigDecimal.valueOf(b.mei() ? 1 : 0))
                .subtract(b.ratingOrZero().divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP))
                .add(pctAbove);
    }
}
