package org.institutonata.suprihub.parameter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ValueTypeTest {

    @Test
    void normalizaDecimalComVirgula() {
        assertThat(ValueType.DECIMAL.normalize("13.098,41".replace(".", ""))).isEqualTo("13098.41");
        assertThat(ValueType.DECIMAL.normalize(" 0.10 ")).isEqualTo("0.10");
    }

    @Test
    void rejeitaValoresInvalidos() {
        assertThatThrownBy(() -> ValueType.INTEGER.normalize("1.5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValueType.BOOLEAN.normalize("sim")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValueType.TEXT.normalize("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValueType.DECIMAL.normalize("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void booleanoCaseInsensitive() {
        assertThat(ValueType.BOOLEAN.normalize("TRUE")).isEqualTo("true");
    }
}
