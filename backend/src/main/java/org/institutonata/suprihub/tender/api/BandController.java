package org.institutonata.suprihub.tender.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.BandPolicy;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Faixas de valor")
public class BandController {

    private final ParameterService parameters;

    public BandController(ParameterService parameters) {
        this.parameters = parameters;
    }

    public record BandResponse(Band band, String label, String selectionMode, BigDecimal estimatedValue,
            BigDecimal band1UpperLimit, BigDecimal band2UpperLimit, int referenceYear) {
    }

    @GetMapping("/api/public/bands")
    @Operation(summary = "Calcula a faixa e o modo de seleção para um valor estimado (parâmetros vigentes)")
    public BandResponse classify(
            @RequestParam @NotNull @DecimalMin(value = "0.01", message = "Informe um valor positivo.")
            BigDecimal estimatedValue) {
        BandPolicy policy = new BandPolicy(parameters.decimal(ParameterKey.BAND1_UPPER_LIMIT),
                parameters.decimal(ParameterKey.BAND2_UPPER_LIMIT));
        Band band = policy.classify(estimatedValue);
        return new BandResponse(band, band.label(), band.selectionMode(), estimatedValue,
                policy.band1UpperLimit(), policy.band2UpperLimit(), parameters.integer(ParameterKey.REFERENCE_YEAR));
    }
}
