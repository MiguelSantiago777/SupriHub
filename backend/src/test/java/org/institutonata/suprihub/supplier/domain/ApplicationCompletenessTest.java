package org.institutonata.suprihub.supplier.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness.Issue;
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness.LineRef;
import org.institutonata.suprihub.supplier.domain.ApplicationCompleteness.Snapshot;
import org.junit.jupiter.api.Test;

class ApplicationCompletenessTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    static final LineRef L01 = new LineRef("L01", "Alimentação — lanches e kits", false, true);
    static final LineRef L07 = new LineRef("L07", "Recreação, oficinas e atrações", true, true);

    static Snapshot mei() {
        return new Snapshot(SupplierType.MEI, "Lanches da Maria MEI", "11222333000181", null,
                "Rua das Flores", "10", "Centro", true, "26553000", "21999990001", "maria@exemplo.com",
                "Maria da Silva", "52998224725", List.of(L01), "Kits lanche para até 200 pessoas",
                Set.of(Territory.T1), "Banco do Brasil", "1234", "56789-0", "Maria da Silva", false, null, true);
    }

    static Snapshot individual(LocalDate birth, List<LineRef> lines) {
        return new Snapshot(SupplierType.INDIVIDUAL, "João Oficineiro", "52998224725", birth,
                "Rua A", "s/n", "Centro", true, "26553000", "21999990001", "joao@exemplo.com",
                null, null, lines, "Oficinas de teatro", Set.of(Territory.T1, Territory.T2), "Caixa", "0001",
                "123-4", "João Oficineiro", false, null, true);
    }

    static Set<DocumentType> requiredFor(SupplierType type) {
        return Arrays.stream(DocumentType.values())
                .filter(d -> d.requirementFor(type) == DocumentType.Requirement.REQUIRED)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentType.class)));
    }

    /** Documentos enviados; certidões válidas por 90 dias. */
    static List<CertificateRegularity.Uploaded> sent(Set<DocumentType> types) {
        return types.stream()
                .map(t -> new CertificateRegularity.Uploaded(t, t.isCertificate() ? TODAY.plusDays(90) : null))
                .toList();
    }

    static Set<String> fields(List<Issue> issues) {
        return issues.stream().map(Issue::field).collect(Collectors.toSet());
    }

    @Test
    void requerimentoCompletoNaoTemPendencias() {
        assertThat(ApplicationCompleteness.check(mei(), sent(requiredFor(SupplierType.MEI)), TODAY)).isEmpty();
    }

    @Test
    void documentosExigidosSeguemATabelaDoItem41() {
        assertThat(requiredFor(SupplierType.MEI)).contains(DocumentType.CCMEI, DocumentType.FGTS_CERTIFICATE)
                .doesNotContain(DocumentType.SOCIAL_CONTRACT, DocumentType.INSS_REGISTRATION);
        assertThat(requiredFor(SupplierType.INDIVIDUAL)).contains(DocumentType.INSS_REGISTRATION)
                .doesNotContain(DocumentType.CCMEI, DocumentType.CNPJ_CARD, DocumentType.FGTS_CERTIFICATE);
        assertThat(requiredFor(SupplierType.EPP)).contains(DocumentType.SOCIAL_CONTRACT,
                DocumentType.SIZE_DECLARATION);
        assertThat(DocumentType.SIZE_DECLARATION.requirementFor(SupplierType.COOPERATIVE))
                .isEqualTo(DocumentType.Requirement.OPTIONAL);
        for (SupplierType t : SupplierType.values()) {
            assertThat(requiredFor(t)).contains(DocumentType.ID_DOCUMENT, DocumentType.FEDERAL_CERTIFICATE,
                    DocumentType.LABOR_CERTIFICATE, DocumentType.BANK_PROOF);
            assertThat(DocumentType.PORTFOLIO.requirementFor(t)).isEqualTo(DocumentType.Requirement.OPTIONAL);
        }
    }

    @Test
    void apontaDocumentoFaltante() {
        Set<DocumentType> docs = requiredFor(SupplierType.MEI);
        docs.remove(DocumentType.CCMEI);
        assertThat(fields(ApplicationCompleteness.check(mei(), sent(docs), TODAY))).containsExactly("documents.CCMEI");
    }

    @Test
    void certidaoPrecisaDeValidadeEmDia() {
        List<CertificateRegularity.Uploaded> docs = new java.util.ArrayList<>(sent(requiredFor(SupplierType.MEI)));
        docs.replaceAll(u -> switch (u.type()) {
            case FGTS_CERTIFICATE -> new CertificateRegularity.Uploaded(u.type(), null);
            case LABOR_CERTIFICATE -> new CertificateRegularity.Uploaded(u.type(), TODAY.minusDays(1));
            case FEDERAL_CERTIFICATE -> new CertificateRegularity.Uploaded(u.type(), TODAY);
            default -> u;
        });
        List<Issue> issues = ApplicationCompleteness.check(mei(), docs, TODAY);
        assertThat(fields(issues)).containsExactlyInAnyOrder("documents.FGTS_CERTIFICATE",
                "documents.LABOR_CERTIFICATE");
        assertThat(issues).extracting(Issue::message).anyMatch(m -> m.startsWith("Informe a validade"))
                .anyMatch(m -> m.startsWith("Certidão vencida"));
    }

    @Test
    void rascunhoVazioListaTudoQueFalta() {
        Snapshot empty = new Snapshot(null, null, null, null, null, null, null, false, null, null, null, null,
                null, List.of(), null, Set.of(), null, null, null, null, false, null, false);
        assertThat(fields(ApplicationCompleteness.check(empty, List.of(), TODAY))).contains("supplierType",
                "legalName", "documentNumber", "street", "municipalityId", "cep", "phone", "lineCodes",
                "territories", "bankName", "declarationsAccepted");
    }

    @Test
    void cnpjInvalidoECepInvalidoSaoApontados() {
        Snapshot s = mei();
        Snapshot bad = new Snapshot(s.supplierType(), s.legalName(), "11222333000180", null, s.street(),
                s.addressNumber(), s.district(), true, "00000000", "2199999", s.contactEmail(),
                s.representativeName(), "11111111111", s.lines(), s.linesDescription(), s.territories(),
                s.bankName(), s.bankBranch(), s.bankAccount(), s.accountHolder(), false, null, true);
        assertThat(fields(ApplicationCompleteness.check(bad, sent(requiredFor(SupplierType.MEI)), TODAY)))
                .containsExactlyInAnyOrder("documentNumber", "cep", "phone", "representativeCpf");
    }

    @Test
    void pessoaFisicaPrecisaTer18Anos() {
        Set<DocumentType> docs = requiredFor(SupplierType.INDIVIDUAL);
        assertThat(ApplicationCompleteness.check(individual(TODAY.minusYears(18), List.of(L07)), sent(docs), TODAY))
                .isEmpty();
        assertThat(fields(ApplicationCompleteness.check(
                individual(TODAY.minusYears(18).plusDays(1), List.of(L07)), sent(docs), TODAY)))
                .containsExactly("birthDate");
    }

    @Test
    void pessoaFisicaSoEmLinhasQueAceitamPf() {
        List<Issue> issues = ApplicationCompleteness.check(individual(LocalDate.of(1980, 1, 1), List.of(L01, L07)),
                sent(requiredFor(SupplierType.INDIVIDUAL)), TODAY);
        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.field()).isEqualTo("lineCodes");
            assertThat(i.message()).contains("L01").contains("não aceita pessoa física");
        });
    }

    @Test
    void cadUnicoExigeNis() {
        Snapshot s = mei();
        Snapshot withCad = new Snapshot(s.supplierType(), s.legalName(), s.documentNumber(), null, s.street(),
                s.addressNumber(), s.district(), true, s.cep(), s.phone(), s.contactEmail(),
                s.representativeName(), s.representativeCpf(), s.lines(), s.linesDescription(), s.territories(),
                s.bankName(), s.bankBranch(), s.bankAccount(), s.accountHolder(), true, null, true);
        assertThat(fields(ApplicationCompleteness.check(withCad, sent(requiredFor(SupplierType.MEI)), TODAY)))
                .containsExactly("nis");
    }

    @Test
    void situacoesEditaveisPeloFornecedor() {
        assertThat(ApplicationStatus.DRAFT.isEditableBySupplier()).isTrue();
        assertThat(ApplicationStatus.ADJUSTMENT_REQUESTED.isEditableBySupplier()).isTrue();
        assertThat(ApplicationStatus.REJECTED.isEditableBySupplier()).isTrue();
        assertThat(ApplicationStatus.SUBMITTED.isEditableBySupplier()).isFalse();
        assertThat(ApplicationStatus.APPROVED.isEditableBySupplier()).isFalse();
    }
}
