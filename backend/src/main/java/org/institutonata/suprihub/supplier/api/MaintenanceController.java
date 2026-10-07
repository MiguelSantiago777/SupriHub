package org.institutonata.suprihub.supplier.api;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.supplier.api.SupplierDtos.MaintenanceRow;
import org.institutonata.suprihub.supplier.application.SupplierMaintenanceService;
import org.institutonata.suprihub.supplier.application.SupplierMaintenanceService.View;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Filas de manutenção do cadastro (Manual item 3, passo 8): certidões a renovar ou vencidas,
 * reconferência semestral, pedidos de renovação e inativos. As ações ficam no requerimento
 * ({@code /api/commission/applications/{id}/...}).
 */
@RestController
@RequestMapping("/api/commission/maintenance")
@PreAuthorize("hasAnyRole('COMMISSION', 'ADMIN')")
@Tag(name = "Manutenção do cadastro")
public class MaintenanceController {

    private final SupplierMaintenanceService maintenance;

    public MaintenanceController(SupplierMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @GetMapping
    @Operation(summary = "Credenciados de uma fila de manutenção")
    public List<MaintenanceRow> list(@RequestParam(defaultValue = "CERTIFICATES") View view) {
        return maintenance.list(view).stream().map(e -> MaintenanceRow.of(e.getKey(), e.getValue())).toList();
    }

    @GetMapping("/counts")
    @Operation(summary = "Quantidade por fila")
    public Map<View, Long> counts() {
        return maintenance.counts();
    }
}
