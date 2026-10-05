package org.institutonata.suprihub.supplier.application;

import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de credenciados (uso interno, D-027): só habilitados com
 * credenciamento vigente. Vencido o prazo de 24 meses, sai da lista automaticamente.
 */
@Service
public class AccreditedDirectoryService {

    private final SupplierApplicationRepository applications;
    private final CalendarService calendar;

    public AccreditedDirectoryService(SupplierApplicationRepository applications, CalendarService calendar) {
        this.applications = applications;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public Page<SupplierApplication> search(String line, Territory territory, String q, Pageable pageable) {
        return applications.searchPublic(ApplicationStatus.APPROVED, calendar.today(),
                line == null ? "" : line.trim().toUpperCase(), territory, q == null ? "" : q.trim(), pageable);
    }
}
