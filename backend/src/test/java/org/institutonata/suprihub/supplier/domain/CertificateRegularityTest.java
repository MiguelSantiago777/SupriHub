package org.institutonata.suprihub.supplier.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.institutonata.suprihub.supplier.domain.CertificateRegularity.Check;
import org.institutonata.suprihub.supplier.domain.CertificateRegularity.Result;
import org.institutonata.suprihub.supplier.domain.CertificateRegularity.State;
import org.institutonata.suprihub.supplier.domain.CertificateRegularity.Uploaded;
import org.junit.jupiter.api.Test;

class CertificateRegularityTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @Test
    void alertaRenovarSegueAPlanilha() {
        // Planilha: "RENOVAR" quando o vencimento é até hoje + 15 dias (inclusive)
        assertThat(CertificateRegularity.state(TODAY.plusDays(16), TODAY, 15)).isEqualTo(State.VALID);
        assertThat(CertificateRegularity.state(TODAY.plusDays(15), TODAY, 15)).isEqualTo(State.EXPIRING);
        assertThat(CertificateRegularity.state(TODAY, TODAY, 15)).isEqualTo(State.EXPIRING);
        assertThat(CertificateRegularity.state(TODAY.minusDays(1), TODAY, 15)).isEqualTo(State.EXPIRED);
        assertThat(CertificateRegularity.state(null, TODAY, 15)).isEqualTo(State.NO_DATE);
    }

    @Test
    void acompanhaAsExigidasEAsOpcionaisEnviadas() {
        Result r = CertificateRegularity.evaluate(SupplierType.MEI, List.of(
                new Uploaded(DocumentType.FEDERAL_CERTIFICATE, TODAY.plusDays(100)),
                new Uploaded(DocumentType.LABOR_CERTIFICATE, TODAY.plusDays(5)),
                new Uploaded(DocumentType.MUNICIPAL_CERTIFICATE, TODAY.plusDays(60)),
                new Uploaded(DocumentType.CCMEI, null)), TODAY, 15);
        assertThat(r.checks()).extracting(Check::type).containsExactly(DocumentType.FEDERAL_CERTIFICATE,
                DocumentType.FGTS_CERTIFICATE, DocumentType.LABOR_CERTIFICATE, DocumentType.MUNICIPAL_CERTIFICATE);
        assertThat(r.withState(State.MISSING)).extracting(Check::type)
                .containsExactly(DocumentType.FGTS_CERTIFICATE);
        assertThat(r.withState(State.EXPIRING)).extracting(Check::type)
                .containsExactly(DocumentType.LABOR_CERTIFICATE);
        assertThat(r.regular()).isFalse();
        assertThat(r.nextExpiry()).isEqualTo(TODAY.plusDays(5));
    }

    @Test
    void semValidadeInformadaNaoEIrregular() {
        // Cadastros anteriores à V24: a Comissão informa a validade na reconferência
        Result r = CertificateRegularity.evaluate(SupplierType.INDIVIDUAL, List.of(
                new Uploaded(DocumentType.FEDERAL_CERTIFICATE, null),
                new Uploaded(DocumentType.LABOR_CERTIFICATE, TODAY)), TODAY, 15);
        assertThat(r.regular()).isTrue();
        assertThat(r.withState(State.NO_DATE)).hasSize(1);
        Result expired = CertificateRegularity.evaluate(SupplierType.INDIVIDUAL, List.of(
                new Uploaded(DocumentType.FEDERAL_CERTIFICATE, TODAY.minusDays(1)),
                new Uploaded(DocumentType.LABOR_CERTIFICATE, TODAY)), TODAY, 15);
        assertThat(expired.regular()).isFalse();
    }
}
