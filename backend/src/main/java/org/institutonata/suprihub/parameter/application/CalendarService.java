package org.institutonata.suprihub.parameter.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.catalog.domain.TerritoryResolver;
import org.institutonata.suprihub.catalog.infra.MunicipalityRepository;
import org.institutonata.suprihub.parameter.domain.BusinessHoliday;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.parameter.infra.BusinessHolidayRepository;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.time.BusinessDayCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o {@link BusinessDayCalendar} a partir da tabela de feriados: nacionais,
 * estaduais (RJ) e municipais da sede do Instituto (Mesquita). Pontos facultativos
 * contam como não úteis conforme o parâmetro {@code optional_points_are_non_business}.
 */
@Service
public class CalendarService {

    private final BusinessHolidayRepository repository;
    private final MunicipalityRepository municipalities;
    private final ParameterService parameters;
    private final AuditService audit;
    private final Clock clock;

    public CalendarService(BusinessHolidayRepository repository, MunicipalityRepository municipalities,
            ParameterService parameters, AuditService audit, Clock clock) {
        this.repository = repository;
        this.municipalities = municipalities;
        this.parameters = parameters;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BusinessDayCalendar calendar() {
        boolean optionalNonBusiness = parameters.bool(ParameterKey.OPTIONAL_POINTS_ARE_NON_BUSINESS);
        Integer seatId = municipalities.findByIbgeCode(TerritoryResolver.MESQUITA_IBGE)
                .map(m -> m.getId()).orElse(null);
        Set<LocalDate> nonBusiness = repository.findAllByOrderByDate().stream()
                .filter(h -> optionalNonBusiness || !h.isOptionalPoint())
                .filter(h -> h.getScope() != BusinessHoliday.Scope.MUNICIPAL
                        || (seatId != null && seatId.equals(h.getMunicipalityId())))
                .map(BusinessHoliday::getDate)
                .collect(Collectors.toSet());
        return BusinessDayCalendar.of(nonBusiness);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public List<BusinessHoliday> holidays() {
        return repository.findAllByOrderByDate();
    }

    @Transactional
    public BusinessHoliday addHoliday(LocalDate date, String name, BusinessHoliday.Scope scope,
            boolean optionalPoint, Integer municipalityId) {
        if (scope == BusinessHoliday.Scope.MUNICIPAL && municipalityId == null) {
            throw new BusinessException("MUNICIPALITY_REQUIRED", "Feriado municipal exige o município.");
        }
        Integer muni = scope == BusinessHoliday.Scope.MUNICIPAL ? municipalityId : null;
        if (repository.existsByDateAndScopeAndMunicipalityId(date, scope, muni)) {
            throw new BusinessException("HOLIDAY_DUPLICATED", "Já existe um feriado nesta data e abrangência.");
        }
        BusinessHoliday saved = repository.save(new BusinessHoliday(date, name, scope, optionalPoint, muni,
                clock.instant()));
        audit.record("business_holiday", saved.getId(), "CREATE", null,
                Map.of("date", date.toString(), "name", name, "scope", scope.name()));
        return saved;
    }

    @Transactional
    public void removeHoliday(long id) {
        BusinessHoliday h = repository.findById(id).orElseThrow(() -> new NotFoundException("Feriado"));
        repository.delete(h);
        audit.record("business_holiday", id, "DELETE",
                Map.of("date", h.getDate().toString(), "name", h.getName(), "scope", h.getScope().name()), null);
    }
}
