package org.institutonata.suprihub.contract.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.contract.application.PriceBankService;
import org.institutonata.suprihub.contract.application.PriceBankService.ItemStats;
import org.institutonata.suprihub.contract.application.PriceBankService.Manual;
import org.institutonata.suprihub.contract.domain.ContractEnums.PriceSource;
import org.institutonata.suprihub.contract.domain.PriceBankEntry;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Banco de Preços: consulta para estimar e conferir preços; lançamento de cotações. */
@RestController
@RequestMapping("/api/price-bank")
@PreAuthorize("hasAnyRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN')")
@Tag(name = "Banco de Preços")
public class PriceBankController {

    public record EntryDto(Long id, BigDecimal unitPrice, PriceSource source, String sourceLabel, String reference,
            LocalDate observedOn) {
        static EntryDto of(PriceBankEntry e) {
            return new EntryDto(e.getId(), e.getUnitPrice(), e.getSource(), e.getSource().label(), e.getReference(),
                    e.getObservedOn());
        }
    }

    public record ItemDto(String lineCode, String lineName, String description, String unit, int count,
            BigDecimal average, BigDecimal min, BigDecimal max, BigDecimal ceiling, LocalDate lastObserved,
            List<EntryDto> entries) {
        static ItemDto of(ItemStats s) {
            return new ItemDto(s.lineCode(), s.lineName(), s.description(), s.unit(), s.count(), s.average(), s.min(),
                    s.max(), s.ceiling(), s.lastObserved(), s.entries().stream().map(EntryDto::of).toList());
        }
    }

    public record ManualRequest(String lineCode, @Size(max = 500) String description, @Size(max = 30) String unit,
            BigDecimal unitPrice, PriceSource source, @Size(max = 100) String reference, LocalDate observedOn) {
    }

    private final PriceBankService prices;

    public PriceBankController(PriceBankService prices) {
        this.prices = prices;
    }

    @GetMapping
    @Operation(summary = "Itens com preço médio, menor, maior e teto do rodízio (média + margem)")
    public List<ItemDto> search(@RequestParam(required = false) String line,
            @RequestParam(defaultValue = "") String q) {
        return prices.search(line, q).stream().map(ItemDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'ADMIN')")
    @Operation(summary = "Lança um preço de cotação, Painel de Preços ou pesquisa de mercado")
    public EntryDto add(@RequestBody ManualRequest r) {
        return EntryDto.of(prices.add(new Manual(r.lineCode(), r.description(), r.unit(), r.unitPrice(), r.source(),
                r.reference(), r.observedOn()), CurrentUser.requireId()));
    }
}
