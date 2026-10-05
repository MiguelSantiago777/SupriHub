package org.institutonata.suprihub.parameter.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.BusinessHoliday;
import org.institutonata.suprihub.parameter.domain.Parameter;
import org.institutonata.suprihub.parameter.domain.ParameterHistory;
import org.institutonata.suprihub.parameter.domain.ValueType;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Parâmetros e calendário")
public class ParameterController {

    private final ParameterService parameters;
    private final CalendarService calendar;

    public ParameterController(ParameterService parameters, CalendarService calendar) {
        this.parameters = parameters;
        this.calendar = calendar;
    }

    public record ParameterDto(String key, String value, ValueType valueType, String category, String description,
            String source, Instant updatedAt) {
        static ParameterDto of(Parameter p) {
            return new ParameterDto(p.getKey(), p.getValue(), p.getValueType(), p.getCategory(), p.getDescription(),
                    p.getSource(), p.getUpdatedAt());
        }
    }

    public record ParameterHistoryDto(String oldValue, String newValue, String reason, Long changedBy,
            Instant changedAt) {
        static ParameterHistoryDto of(ParameterHistory h) {
            return new ParameterHistoryDto(h.getOldValue(), h.getNewValue(), h.getReason(), h.getChangedBy(),
                    h.getChangedAt());
        }
    }

    public record UpdateParameterRequest(
            @NotBlank(message = "Informe o valor.") @Size(max = 500) String value,
            @NotBlank(message = "Informe o motivo da alteração.") @Size(max = 500) String reason) {
    }

    public record HolidayDto(Long id, LocalDate date, String name, BusinessHoliday.Scope scope,
            boolean optionalPoint, Integer municipalityId) {
        static HolidayDto of(BusinessHoliday h) {
            return new HolidayDto(h.getId(), h.getDate(), h.getName(), h.getScope(), h.isOptionalPoint(),
                    h.getMunicipalityId());
        }
    }

    public record CreateHolidayRequest(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 150) String name,
            @NotNull BusinessHoliday.Scope scope,
            boolean optionalPoint,
            Integer municipalityId) {
    }

    public record BusinessDayResponse(LocalDate start, int businessDays, LocalDate dueDate) {
    }

    @GetMapping("/api/admin/parameters")
    @PreAuthorize("hasAnyRole('ADMIN','COMMISSION','INTERNAL_CONTROL','BOARD','FINANCE','DEMAND_MANAGER')")
    @Operation(summary = "Lista os parâmetros vigentes")
    public List<ParameterDto> list() {
        return parameters.all().stream().map(ParameterDto::of).toList();
    }

    @PutMapping("/api/admin/parameters/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Altera um parâmetro (gera histórico e auditoria)")
    public ParameterDto update(@PathVariable String key, @Valid @RequestBody UpdateParameterRequest req) {
        return ParameterDto.of(parameters.update(key, req.value(), req.reason()));
    }

    @GetMapping("/api/admin/parameters/{key}/history")
    @PreAuthorize("hasAnyRole('ADMIN','INTERNAL_CONTROL')")
    @Operation(summary = "Histórico de alterações de um parâmetro")
    public List<ParameterHistoryDto> history(@PathVariable String key) {
        return parameters.history(key).stream().map(ParameterHistoryDto::of).toList();
    }

    @GetMapping("/api/admin/holidays")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Feriados e pontos facultativos cadastrados")
    public List<HolidayDto> holidays() {
        return calendar.holidays().stream().map(HolidayDto::of).toList();
    }

    @PostMapping("/api/admin/holidays")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra feriado/ponto facultativo")
    public HolidayDto addHoliday(@Valid @RequestBody CreateHolidayRequest req) {
        return HolidayDto.of(calendar.addHoliday(req.date(), req.name(), req.scope(), req.optionalPoint(),
                req.municipalityId()));
    }

    @DeleteMapping("/api/admin/holidays/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove feriado")
    public void removeHoliday(@PathVariable long id) {
        calendar.removeHoliday(id);
    }

    @GetMapping("/api/public/business-days")
    @Operation(summary = "Calcula o vencimento de um prazo em dias úteis (exclui o início, inclui o vencimento)")
    public BusinessDayResponse dueDate(@RequestParam LocalDate start, @RequestParam int businessDays) {
        if (businessDays < 0 || businessDays > 365) {
            throw new BusinessException("INVALID_DAYS",
                    "Informe um prazo entre 0 e 365 dias úteis.");
        }
        return new BusinessDayResponse(start, businessDays, calendar.calendar().addBusinessDays(start, businessDays));
    }
}
