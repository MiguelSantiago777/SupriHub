package org.institutonata.suprihub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.institutonata.suprihub.auth.domain.PersonName;
import org.junit.jupiter.api.Test;

class PersonNameTest {

    @Test
    void nomeTodoMinusculoGanhaIniciaisMaiusculas() {
        assertThat(PersonName.normalize("maria antonieta")).isEqualTo("Maria Antonieta");
        assertThat(PersonName.normalize("  joão   da silva  ")).isEqualTo("João da Silva");
        assertThat(PersonName.normalize("ana-luíza dos santos e souza")).isEqualTo("Ana-Luíza dos Santos e Souza");
    }

    @Test
    void nomeTodoMaiusculoTambemEhPadronizado() {
        assertThat(PersonName.normalize("JOSÉ DE ÁVILA")).isEqualTo("José de Ávila");
    }

    @Test
    void particulaNoInicioFicaMaiuscula() {
        assertThat(PersonName.normalize("da silva")).isEqualTo("Da Silva");
    }

    @Test
    void caixaMistaFicaComoAPessoaEscreveu() {
        assertThat(PersonName.normalize("Ronald McDonald")).isEqualTo("Ronald McDonald");
        assertThat(PersonName.normalize("Maria Da Silva")).isEqualTo("Maria Da Silva");
    }
}
