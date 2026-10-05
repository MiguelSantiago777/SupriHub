package org.institutonata.suprihub.catalog.domain;

/** Dados mínimos de um município para cálculo de território (independente de JPA). */
public record MunicipalityRef(String ibgeCode, String uf, boolean metropolitanRegion) {
}
