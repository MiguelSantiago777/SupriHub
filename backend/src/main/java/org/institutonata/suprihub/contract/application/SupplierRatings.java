package org.institutonata.suprihub.contract.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.institutonata.suprihub.contract.domain.ContractRules;
import org.institutonata.suprihub.contract.infra.ServiceOrderRepository;
import org.institutonata.suprihub.judgment.application.SupplierRatingSource;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Média dos Termos de Recebimento no período ({@code rating_window_months}) — desempate 8.4. */
@Component
public class SupplierRatings implements SupplierRatingSource {

    private final ServiceOrderRepository orders;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final Clock clock;

    public SupplierRatings(ServiceOrderRepository orders, ParameterService parameters, CalendarService calendar,
            Clock clock) {
        this.orders = orders;
        this.parameters = parameters;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal ratingOf(long applicationId) {
        LocalDate since = calendar.today().minusMonths(parameters.integer(ParameterKey.RATING_WINDOW_MONTHS));
        Instant from = since.atStartOfDay(clock.getZone()).toInstant();
        List<ContractRules.Rated> history = orders.findByApplicationIdAndRatedAtAfter(applicationId, from).stream()
                .filter(o -> o.getRating() != null)
                .map(o -> new ContractRules.Rated(LocalDate.ofInstant(o.getRatedAt(), clock.getZone()), o.getRating()))
                .toList();
        return ContractRules.ratingSince(history, since);
    }
}
