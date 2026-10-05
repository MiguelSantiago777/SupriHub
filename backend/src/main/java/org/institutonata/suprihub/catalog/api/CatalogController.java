package org.institutonata.suprihub.catalog.api;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.catalog.api.CatalogDtos.MunicipalityDto;
import org.institutonata.suprihub.catalog.api.CatalogDtos.SupplyLineDto;
import org.institutonata.suprihub.catalog.api.CatalogDtos.UpdateSupplyLineRequest;
import org.institutonata.suprihub.catalog.application.CatalogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Catálogo", description = "Linhas de fornecimento (Anexo I) e municípios")
public class CatalogController {

    private final CatalogService service;
    private final CatalogMapper mapper;

    public CatalogController(CatalogService service, CatalogMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/api/public/supply-lines")
    @Operation(summary = "Catálogo público de linhas de fornecimento ativas")
    public List<SupplyLineDto> publicLines() {
        return mapper.toLineDtos(service.activeLines());
    }

    @GetMapping("/api/public/supply-lines/{code}")
    @Operation(summary = "Detalhe de uma linha de fornecimento")
    public SupplyLineDto publicLine(@PathVariable String code) {
        return mapper.toDto(service.line(code.toUpperCase()));
    }

    @GetMapping("/api/public/municipalities")
    @Operation(summary = "Municípios do Estado do Rio de Janeiro")
    public List<MunicipalityDto> municipalities() {
        return mapper.toMunicipalityDtos(service.rjMunicipalities());
    }

    @GetMapping("/api/admin/supply-lines")
    @PreAuthorize("hasAnyRole('ADMIN','COMMISSION')")
    @Operation(summary = "Todas as linhas, inclusive inativas")
    public List<SupplyLineDto> allLines() {
        return mapper.toLineDtos(service.allLines());
    }

    @PutMapping("/api/admin/supply-lines/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','COMMISSION')")
    @Operation(summary = "Atualiza uma linha (inclui ligar médio/grande porte)")
    public SupplyLineDto updateLine(@PathVariable String code, @Valid @RequestBody UpdateSupplyLineRequest req) {
        return mapper.toDto(service.updateLine(code.toUpperCase(), req));
    }
}
