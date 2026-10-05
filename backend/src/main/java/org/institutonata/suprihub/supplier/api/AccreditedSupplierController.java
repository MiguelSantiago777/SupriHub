package org.institutonata.suprihub.supplier.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.institutonata.suprihub.supplier.api.SupplierDtos.AccreditedSupplier;
import org.institutonata.suprihub.supplier.application.AccreditedDirectoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro de credenciados — uso interno da equipe do Instituto (D-027). */
@RestController
@Tag(name = "Credenciados")
public class AccreditedSupplierController {

    private final AccreditedDirectoryService directory;

    public AccreditedSupplierController(AccreditedDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/api/suppliers")
    @PreAuthorize("hasAnyRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN')")
    @Operation(summary = "Credenciados vigentes (por linha, território e nome) — equipe interna")
    public PageResponse<AccreditedSupplier> list(@RequestParam(required = false) String line,
            @RequestParam(required = false) Territory territory, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
        return PageResponse.of(directory.search(line, territory, q,
                PageRequest.of(page, Math.min(size, 60), Sort.by("legalName"))), AccreditedSupplier::of);
    }
}
