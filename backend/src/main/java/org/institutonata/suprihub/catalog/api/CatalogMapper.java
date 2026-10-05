package org.institutonata.suprihub.catalog.api;

import java.util.List;

import org.institutonata.suprihub.catalog.api.CatalogDtos.MunicipalityDto;
import org.institutonata.suprihub.catalog.api.CatalogDtos.SupplyLineDto;
import org.institutonata.suprihub.catalog.domain.Municipality;
import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.mapstruct.Mapper;

@Mapper
public interface CatalogMapper {

    SupplyLineDto toDto(SupplyLine line);

    List<SupplyLineDto> toLineDtos(List<SupplyLine> lines);

    MunicipalityDto toDto(Municipality municipality);

    List<MunicipalityDto> toMunicipalityDtos(List<Municipality> municipalities);
}
