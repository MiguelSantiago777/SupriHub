package org.institutonata.suprihub.parameter.infra;

import java.time.LocalDate;
import java.util.List;

import org.institutonata.suprihub.parameter.domain.BusinessHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessHolidayRepository extends JpaRepository<BusinessHoliday, Long> {

    List<BusinessHoliday> findAllByOrderByDate();

    boolean existsByDateAndScopeAndMunicipalityId(LocalDate date, BusinessHoliday.Scope scope,
            Integer municipalityId);
}
