package org.institutonata.suprihub.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentMaskerTest {

    @Test
    void mascaraCnpjNoFormatoDoEdital() {
        // Exemplo do prompt/Edital 6.2: 12.345.***/****-90
        assertThat(DocumentMasker.mask("12.345.678/0001-90")).isEqualTo("12.345.***/****-90");
        assertThat(DocumentMasker.mask("12345678000190")).isEqualTo("12.345.***/****-90");
    }

    @Test
    void mascaraCpfOcultandoInicioEFim() {
        assertThat(DocumentMasker.mask("529.982.247-25")).isEqualTo("***.982.247-**");
    }

    @Test
    void valorDesconhecidoNaoVaza() {
        assertThat(DocumentMasker.mask("abc123")).isEqualTo("***");
        assertThat(DocumentMasker.mask(null)).isEqualTo("***");
    }

    @Test
    void mascaraEmail() {
        assertThat(DocumentMasker.maskEmail("maria@exemplo.com")).isEqualTo("ma***@exemplo.com");
        assertThat(DocumentMasker.maskEmail("a@x.com")).isEqualTo("a***@x.com");
        assertThat(DocumentMasker.maskEmail("invalido")).isEqualTo("***");
    }
}
