package org.institutonata.suprihub.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerritoryResolverTest {

    private static final MunicipalityRef MESQUITA = new MunicipalityRef("3302858", "RJ", true);
    private static final MunicipalityRef NOVA_IGUACU = new MunicipalityRef("3303500", "RJ", true);
    private static final MunicipalityRef RIO = new MunicipalityRef("3304557", "RJ", true);
    private static final MunicipalityRef PETROPOLIS = new MunicipalityRef("3303906", "RJ", true);
    private static final MunicipalityRef VOLTA_REDONDA = new MunicipalityRef("3306305", "RJ", false);
    private static final MunicipalityRef SAO_PAULO = new MunicipalityRef("3550308", "SP", false);

    @Test
    void sedeEmMesquitaEhSempreT1() {
        assertThat(TerritoryResolver.resolve(MESQUITA, RIO).territory()).isEqualTo(Territory.T1);
        assertThat(TerritoryResolver.resolve(MESQUITA, VOLTA_REDONDA).territory()).isEqualTo(Territory.T1);
    }

    @Test
    void sedeNoMunicipioDeExecucaoEhT1() {
        assertThat(TerritoryResolver.resolve(RIO, RIO).territory()).isEqualTo(Territory.T1);
        assertThat(TerritoryResolver.resolve(VOLTA_REDONDA, VOLTA_REDONDA).territory()).isEqualTo(Territory.T1);
    }

    @Test
    void regiaoMetropolitanaEhT2() {
        assertThat(TerritoryResolver.resolve(NOVA_IGUACU, MESQUITA).territory()).isEqualTo(Territory.T2);
        assertThat(TerritoryResolver.resolve(PETROPOLIS, RIO).territory()).isEqualTo(Territory.T2);
    }

    @Test
    void demaisDoEstadoEhT3() {
        assertThat(TerritoryResolver.resolve(VOLTA_REDONDA, MESQUITA).territory()).isEqualTo(Territory.T3);
    }

    @Test
    void foraDoEstadoEhNivel4() {
        TerritoryResolver.Resolution r = TerritoryResolver.resolve(SAO_PAULO, MESQUITA);
        assertThat(r.territory()).isEqualTo(Territory.OUT_OF_STATE);
        assertThat(r.territory().level()).isEqualTo(4);
    }

    @Test
    void trocarMunicipioDeExecucaoAlteraOT1() {
        // Fornecedor de Nova Iguaçu: T2 num edital executado em Mesquita, T1 se executado em Nova Iguaçu.
        assertThat(TerritoryResolver.resolve(NOVA_IGUACU, MESQUITA).territory()).isEqualTo(Territory.T2);
        assertThat(TerritoryResolver.resolve(NOVA_IGUACU, NOVA_IGUACU).territory()).isEqualTo(Territory.T1);
    }

    @Test
    void municipioDeExecucaoDesconhecidoSinalizaAviso() {
        TerritoryResolver.Resolution r = TerritoryResolver.resolve(NOVA_IGUACU, null);
        assertThat(r.territory()).isEqualTo(Territory.T2);
        assertThat(r.executionMunicipalityUnknown()).isTrue();
        assertThat(TerritoryResolver.resolve(MESQUITA, null).territory()).isEqualTo(Territory.T1);
    }

    @Test
    void ordemDePrioridade() {
        assertThat(Territory.T1.outranks(Territory.T2)).isTrue();
        assertThat(Territory.T2.outranks(Territory.T3)).isTrue();
        assertThat(Territory.T3.outranks(Territory.OUT_OF_STATE)).isTrue();
        assertThat(Territory.T2.outranks(Territory.T1)).isFalse();
    }
}
