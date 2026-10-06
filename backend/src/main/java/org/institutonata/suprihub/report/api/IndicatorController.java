package org.institutonata.suprihub.report.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.report.application.IndicatorService;
import org.institutonata.suprihub.report.application.IndicatorService.Report;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Indicadores do relatório semestral (Manual item 7). */
@RestController
@RequestMapping("/api/indicators")
@PreAuthorize("hasAnyRole('COMMISSION', 'BOARD', 'INTERNAL_CONTROL', 'FINANCE', 'ADMIN')")
@Tag(name = "Indicadores")
public class IndicatorController {

    private final IndicatorService indicators;

    public IndicatorController(IndicatorService indicators) {
        this.indicators = indicators;
    }

    @GetMapping
    @Operation(summary = "Indicadores e metas no período (padrão: semestre corrente)")
    public Report report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] sem = indicators.currentSemester();
        LocalDate f = from == null ? sem[0] : from;
        LocalDate t = to == null ? sem[1] : to;
        if (t.isBefore(f)) {
            throw new BusinessException("PERIOD_INVALID", "O fim do período vem antes do início.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return indicators.report(f, t);
    }
}
