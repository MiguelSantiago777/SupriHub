package org.institutonata.suprihub.tender.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.tender.domain.RotationPolicy.Candidate;
import org.institutonata.suprihub.tender.domain.TenderEligibility.Opportunity;
import org.institutonata.suprihub.tender.domain.TenderEligibility.Standing;
import org.institutonata.suprihub.tender.domain.TenderEligibility.SupplierProfile;
import org.institutonata.suprihub.tender.domain.TenderEnums.Audience;
import org.junit.jupiter.api.Test;

class TenderRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    static SupplierProfile supplier(Standing standing, Set<String> lines, Set<Territory> territories, boolean woman) {
        return new SupplierProfile(1L, standing, TODAY.plusYears(2), lines, territories, woman);
    }

    static Opportunity op(Band band, Audience audience, Territory... territories) {
        return new Opportunity(band, "L01", Set.of(territories), audience);
    }

    @Test
    void elegivelPorLinhaETerritorio() {
        SupplierProfile s = supplier(Standing.ACCREDITED, Set.of("L01", "L07"), Set.of(Territory.T1), false);
        assertThat(TenderEligibility.eligible(op(Band.FAIXA_2, Audience.ALL, Territory.T1, Territory.T2), s, TODAY))
                .isTrue();
        assertThat(TenderEligibility.eligible(op(Band.FAIXA_2, Audience.ALL, Territory.T2), s, TODAY)).isFalse();
        SupplierProfile otherLine = supplier(Standing.ACCREDITED, Set.of("L07"), Set.of(Territory.T1), false);
        assertThat(TenderEligibility.eligible(op(Band.FAIXA_2, Audience.ALL, Territory.T1), otherLine, TODAY))
                .isFalse();
    }

    @Test
    void exclusivaAMulheres() {
        Opportunity o = op(Band.FAIXA_2, Audience.WOMEN, Territory.T1);
        assertThat(TenderEligibility.eligible(o,
                supplier(Standing.ACCREDITED, Set.of("L01"), Set.of(Territory.T1), true), TODAY)).isTrue();
        assertThat(TenderEligibility.eligible(o,
                supplier(Standing.ACCREDITED, Set.of("L01"), Set.of(Territory.T1), false), TODAY)).isFalse();
    }

    @Test
    void faixa3AceitaQuemAindaEstaEmAnaliseFaixa2Nao() {
        SupplierProfile pending = supplier(Standing.PENDING, Set.of("L01"), Set.of(Territory.T1), false);
        assertThat(TenderEligibility.eligible(op(Band.FAIXA_3, Audience.ALL, Territory.T1), pending, TODAY)).isTrue();
        assertThat(TenderEligibility.eligible(op(Band.FAIXA_2, Audience.ALL, Territory.T1), pending, TODAY))
                .isFalse();
        assertThat(TenderEligibility.eligible(op(Band.FAIXA_1, Audience.ALL, Territory.T1), pending, TODAY))
                .isFalse();
    }

    @Test
    void credenciamentoVencidoNaoParticipa() {
        SupplierProfile expired = new SupplierProfile(1L, Standing.ACCREDITED, TODAY.minusDays(1), Set.of("L01"),
                Set.of(Territory.T1), false);
        assertThat(TenderEligibility.eligible(op(Band.FAIXA_2, Audience.ALL, Territory.T1), expired, TODAY))
                .isFalse();
    }

    @Test
    void rodizioPrimeiroQuemNuncaFoiSelecionadoDepoisOMaisAntigo() {
        Instant jan = Instant.parse("2026-01-10T12:00:00Z");
        Instant mar = Instant.parse("2026-03-10T12:00:00Z");
        Candidate selectedInMarch = new Candidate(1L, mar, jan);
        Candidate selectedInJanuary = new Candidate(2L, jan, jan);
        Candidate neverOld = new Candidate(3L, null, jan);
        Candidate neverNew = new Candidate(4L, null, mar);
        List<Candidate> all = List.of(selectedInMarch, selectedInJanuary, neverOld, neverNew);

        assertThat(RotationPolicy.queue(all, Set.of())).extracting(Candidate::applicationId)
                .containsExactly(3L, 4L, 2L, 1L);
        assertThat(RotationPolicy.next(all, Set.of(3L))).get().extracting(Candidate::applicationId).isEqualTo(4L);
        assertThat(RotationPolicy.next(all, Set.of(1L, 2L, 3L, 4L))).isEmpty();
    }
}
