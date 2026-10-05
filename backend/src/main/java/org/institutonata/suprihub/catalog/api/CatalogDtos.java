package org.institutonata.suprihub.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs do catálogo (públicos — não contêm dados pessoais). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record SupplyLineDto(
            String code,
            String name,
            String shortName,
            String scope,
            String cnaeReference,
            boolean allowsIndividual,
            boolean allowsMediumLarge,
            boolean active,
            long version) {
    }

    public record MunicipalityDto(Integer id, String ibgeCode, String name, String uf, boolean metropolitanRegion) {
    }

    public record UpdateSupplyLineRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String shortName,
            @NotBlank String scope,
            @NotBlank @Size(max = 300) String cnaeReference,
            boolean allowsIndividual,
            boolean allowsMediumLarge,
            boolean active,
            long version) {
    }
}
