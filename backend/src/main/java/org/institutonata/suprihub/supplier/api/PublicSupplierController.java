package org.institutonata.suprihub.supplier.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.institutonata.suprihub.supplier.api.SupplierDtos.PublicSupplier;
import org.institutonata.suprihub.supplier.application.PublicDirectoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Público")
public class PublicSupplierController {

    private final PublicDirectoryService directory;

    public PublicSupplierController(PublicDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/api/public/suppliers")
    @Operation(summary = "Lista pública de credenciados (por linha, território e nome)")
    public PageResponse<PublicSupplier> list(@RequestParam(required = false) String line,
            @RequestParam(required = false) Territory territory, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
        return PageResponse.of(directory.search(line, territory, q,
                PageRequest.of(page, Math.min(size, 60), Sort.by("legalName"))), PublicSupplier::of);
    }
}
