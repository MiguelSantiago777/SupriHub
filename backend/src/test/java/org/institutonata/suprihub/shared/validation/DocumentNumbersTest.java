package org.institutonata.suprihub.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
    void cpfValido(String cpf) {
        assertThat(DocumentNumbers.isValidCpf(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "111.111.111-11", "123", "", "529.982.247-2a"})
    void cpfInvalido(String cpf) {
        assertThat(DocumentNumbers.isValidCpf(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-81", "11222333000181", "11.444.777/0001-61"})
    void cnpjValido(String cnpj) {
        assertThat(DocumentNumbers.isValidCnpj(cnpj)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-80", "00.000.000/0000-00", "12.345.678/0001-90", "1122233300018"})
    void cnpjInvalido(String cnpj) {
        // 12.345.678/0001-90 é o exemplo da planilha — dígito verificador incorreto.
        assertThat(DocumentNumbers.isValidCnpj(cnpj)).isFalse();
    }

    @Test
    void cpfOuCnpjDecidePeloTamanho() {
        assertThat(DocumentNumbers.isValidCpfOrCnpj("529.982.247-25")).isTrue();
        assertThat(DocumentNumbers.isValidCpfOrCnpj("11.222.333/0001-81")).isTrue();
        assertThat(DocumentNumbers.isValidCpfOrCnpj("1234")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"(21) 99999-0001", "21999990001", "+55 21 99999-0001", "(21) 2696-1234"})
    void telefoneValido(String phone) {
        assertThat(DocumentNumbers.isValidBrazilianPhone(phone)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"(21) 89999-0001", "(01) 99999-0001", "2199999", "(21) 1696-1234", "(21) 9999-000111"})
    void telefoneInvalido(String phone) {
        assertThat(DocumentNumbers.isValidBrazilianPhone(phone)).isFalse();
    }

    @Test
    void cep() {
        assertThat(DocumentNumbers.isValidCep("26553-000")).isTrue();
        assertThat(DocumentNumbers.isValidCep("26553000")).isTrue();
        assertThat(DocumentNumbers.isValidCep("2655-300")).isFalse();
        assertThat(DocumentNumbers.isValidCep("00000-000")).isFalse();
    }

    @Test
    void formatacao() {
        assertThat(DocumentNumbers.formatCpf("52998224725")).isEqualTo("529.982.247-25");
        assertThat(DocumentNumbers.formatCnpj("11222333000181")).isEqualTo("11.222.333/0001-81");
    }
}
