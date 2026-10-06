package org.institutonata.suprihub.tender.application;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.domain.TenderEligibility;
import org.institutonata.suprihub.tender.domain.TenderEligibility.Opportunity;
import org.institutonata.suprihub.tender.domain.TenderEligibility.Standing;
import org.institutonata.suprihub.tender.domain.TenderEligibility.SupplierProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ponte entre o requerimento do fornecedor e a regra de elegibilidade das oportunidades. */
@Service
public class EligibleSuppliers {

    private final SupplierApplicationRepository applications;

    public EligibleSuppliers(SupplierApplicationRepository applications) {
        this.applications = applications;
    }

    public static SupplierProfile profile(SupplierApplication a) {
        Standing standing = switch (a.getStatus()) {
            case APPROVED -> Standing.ACCREDITED;
            case SUBMITTED, ADJUSTMENT_REQUESTED -> Standing.PENDING;
            default -> Standing.OTHER;
        };
        return new SupplierProfile(a.getId(), standing, a.getAccreditationValidUntil(),
                a.getLines().stream().map(SupplyLine::getCode).collect(Collectors.toSet()), a.getTerritories(),
                a.isWomanLed());
    }

    public static Opportunity opportunity(Tender t) {
        return new Opportunity(t.getBand(), t.getSupplyLine().getCode(), t.getTerritories(), t.getAudience());
    }

    public static boolean eligible(Tender t, SupplierApplication a, LocalDate today) {
        // Suspenso, descredenciado ou inativado cautelarmente: fora de qualquer oportunidade.
        return !a.isBlockedOn(today) && TenderEligibility.eligible(opportunity(t), profile(a), today);
    }

    /** Fornecedores que podem receber/ver a oportunidade (para avisos e rodízio). */
    @Transactional(readOnly = true)
    public List<SupplierApplication> of(Tender t, LocalDate today) {
        List<ApplicationStatus> statuses = t.getBand() == Band.FAIXA_3
                ? List.of(ApplicationStatus.APPROVED, ApplicationStatus.SUBMITTED, ApplicationStatus.ADJUSTMENT_REQUESTED)
                : List.of(ApplicationStatus.APPROVED);
        return applications.findByLineAndStatusIn(t.getSupplyLine().getCode(), statuses).stream()
                .filter(a -> eligible(t, a, today))
                .toList();
    }
}
