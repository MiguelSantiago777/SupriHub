package org.institutonata.suprihub.report.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.catalog.domain.TerritoryResolver;
import org.institutonata.suprihub.catalog.infra.SupplyLineRepository;
import org.institutonata.suprihub.contract.domain.ContractEnums.Status;
import org.institutonata.suprihub.contract.domain.DossierReview;
import org.institutonata.suprihub.contract.domain.ServiceOrder;
import org.institutonata.suprihub.contract.infra.DossierReviewRepository;
import org.institutonata.suprihub.contract.infra.ServiceOrderRepository;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.supplier.domain.ApplicationStatus;
import org.institutonata.suprihub.supplier.domain.SupplierApplication;
import org.institutonata.suprihub.supplier.infra.SupplierApplicationRepository;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;
import org.institutonata.suprihub.tender.infra.TenderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores do relatório semestral (Manual item 7; Política art. 27). A base são as ordens
 * emitidas no período (contratações efetivas), não as oportunidades.
 */
@Service
public class IndicatorService {

    /** Indicador com meta: {@code value} e {@code goal} na mesma unidade; {@code met} nulo = sem dado. */
    public record Indicator(String code, String label, String formula, BigDecimal value, BigDecimal goal,
            String goalLabel, Boolean met, String note) {
    }

    public record BandStats(Band band, String label, int count, BigDecimal value, BigDecimal avgBusinessDays) {
    }

    public record LineStats(String code, String name, int activeT1, int active) {
    }

    public record ProjectStats(String project, int count, BigDecimal value) {
    }

    public record Report(LocalDate from, LocalDate to, int contracts, BigDecimal totalValue, List<Indicator> indicators,
            List<BandStats> bands, List<ProjectStats> projects, List<LineStats> lines, int activeSuppliers) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ServiceOrderRepository orders;
    private final TenderRepository tenders;
    private final SupplierApplicationRepository applications;
    private final SupplyLineRepository lines;
    private final DossierReviewRepository reviews;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final Clock clock;

    public IndicatorService(ServiceOrderRepository orders, TenderRepository tenders,
            SupplierApplicationRepository applications, SupplyLineRepository lines, DossierReviewRepository reviews,
            ParameterService parameters, CalendarService calendar, Clock clock) {
        this.orders = orders;
        this.tenders = tenders;
        this.applications = applications;
        this.lines = lines;
        this.reviews = reviews;
        this.parameters = parameters;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Semestre corrente: 1/1 a 30/6 ou 1/7 a 31/12. */
    public LocalDate[] currentSemester() {
        LocalDate today = calendar.today();
        return today.getMonthValue() <= 6
                ? new LocalDate[] {LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 6, 30)}
                : new LocalDate[] {LocalDate.of(today.getYear(), 7, 1), LocalDate.of(today.getYear(), 12, 31)};
    }

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(clock.getZone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        List<ServiceOrder> contracted = orders.findAll().stream()
                .filter(o -> o.getIssuedAt() != null && !o.getIssuedAt().isBefore(start) && o.getIssuedAt().isBefore(end))
                .filter(o -> o.getStatus() != Status.CANCELLED && o.getStatus() != Status.EXPIRED)
                .toList();
        Map<Long, SupplierApplication> apps = new LinkedHashMap<>();
        applications.findAllById(contracted.stream().map(ServiceOrder::getApplicationId).toList())
                .forEach(a -> apps.put(a.getId(), a));
        Map<Long, Tender> tenderById = new LinkedHashMap<>();
        tenders.findAllById(contracted.stream().map(ServiceOrder::getTenderId).toList())
                .forEach(t -> tenderById.put(t.getId(), t));

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal women = BigDecimal.ZERO;
        BigDecimal t1 = BigDecimal.ZERO;
        BigDecimal small = BigDecimal.ZERO;
        Map<Band, List<ServiceOrder>> byBand = new EnumMap<>(Band.class);
        Map<String, List<ServiceOrder>> byProject = new LinkedHashMap<>();
        for (ServiceOrder o : contracted) {
            BigDecimal v = o.getTotalValue();
            total = total.add(v);
            SupplierApplication a = apps.get(o.getApplicationId());
            Tender t = tenderById.get(o.getTenderId());
            // Todos os tipos aceitos no credenciamento são pequenos negócios (D-031).
            small = small.add(v);
            if (a != null && a.isWomanLed()) {
                women = women.add(v);
            }
            if (a != null && a.getMunicipality() != null && t != null
                    && TerritoryResolver.resolve(a.getMunicipality().toRef(), t.getExecutionMunicipality() == null
                            ? null : t.getExecutionMunicipality().toRef()).territory() == Territory.T1) {
                t1 = t1.add(v);
            }
            if (t != null) {
                byBand.computeIfAbsent(t.getBand(), b -> new ArrayList<>()).add(o);
            }
            byProject.computeIfAbsent(o.getProject() == null ? "Sem projeto informado" : o.getProject(),
                    k -> new ArrayList<>()).add(o);
        }

        List<BandStats> bands = new ArrayList<>();
        Map<Band, BigDecimal> avgDays = new EnumMap<>(Band.class);
        for (Band b : Band.values()) {
            List<ServiceOrder> list = byBand.getOrDefault(b, List.of());
            BigDecimal avg = averageDays(list, tenderById);
            avgDays.put(b, avg);
            bands.add(new BandStats(b, b.label(), list.size(), sum(list), avg));
        }
        List<ProjectStats> projects = byProject.entrySet().stream()
                .map(e -> new ProjectStats(e.getKey(), e.getValue().size(), sum(e.getValue()))).toList();

        List<BigDecimal> ratings = orders.findAll().stream()
                .filter(o -> o.getRatedAt() != null && !o.getRatedAt().isBefore(start) && o.getRatedAt().isBefore(end))
                .map(ServiceOrder::getRating).filter(r -> r != null).toList();
        BigDecimal avgRating = ratings.isEmpty() ? null
                : ratings.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(ratings.size()), 2, RoundingMode.HALF_UP);

        List<DossierReview> rv = reviews.findByReviewedAtBetween(start, end);
        BigDecimal dossiers = rv.isEmpty() ? null
                : pct(BigDecimal.valueOf(rv.stream().filter(DossierReview::isComplete).count()),
                        BigDecimal.valueOf(rv.size()));

        List<Indicator> ind = new ArrayList<>();
        ind.add(share("SMALL", "% de pequenos negócios no valor contratado",
                "Σ valor com MEI/ME/EPP/coop./PF ÷ Σ valor contratado", small, total,
                parameters.decimal(ParameterKey.GOAL_SMALL_BUSINESS_SHARE), true,
                "Todos os tipos aceitos no credenciamento são pequenos negócios (D-031)."));
        ind.add(share("WOMEN", "% de negócios liderados por mulheres no valor contratado",
                "Σ valor com negócios liderados por mulheres ÷ Σ valor", women, total,
                parameters.decimal(ParameterKey.GOAL_WOMEN_SHARE), true, null));
        ind.add(share("T1", "% do valor contratado com fornecedores T1", "Σ valor com sede T1 ÷ Σ valor", t1, total,
                parameters.decimal(ParameterKey.GOAL_T1_SHARE), true, null));
        ind.add(days("DAYS_F1", "Tempo médio DFD → Ordem — Faixa 1", avgDays.get(Band.FAIXA_1), BigDecimal.valueOf(4)));
        ind.add(days("DAYS_F2", "Tempo médio DFD → Ordem — Faixa 2", avgDays.get(Band.FAIXA_2), BigDecimal.valueOf(8)));
        BigDecimal minRating = parameters.decimal(ParameterKey.GOAL_MIN_AVG_RATING);
        ind.add(new Indicator("RATING", "Avaliação média dos fornecedores", "Média das notas dos Termos de Recebimento",
                avgRating, minRating, "≥ " + minRating.stripTrailingZeros().toPlainString().replace('.', ','),
                avgRating == null ? null : avgRating.compareTo(minRating) >= 0,
                avgRating == null ? "Nenhum termo final no período." : ratings.size() + " avaliação(ões)."));
        BigDecimal maxUrgency = parameters.decimal(ParameterKey.GOAL_MAX_URGENCY_SHARE).multiply(HUNDRED);
        long urgent = contracted.stream().map(o -> tenderById.get(o.getTenderId()))
                .filter(t -> t != null && t.isUrgent()).count();
        BigDecimal urgency = contracted.isEmpty() ? null
                : BigDecimal.valueOf(urgent).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(contracted.size()), 1, RoundingMode.HALF_UP);
        ind.add(new Indicator("URGENCY", "Contratações de urgência", "Nº de urgências ÷ nº total de contratações",
                urgency, maxUrgency, "≤ " + maxUrgency.stripTrailingZeros().toPlainString() + "%",
                urgency == null ? null : urgency.compareTo(maxUrgency) <= 0,
                urgent + " de " + contracted.size() + " contratação(ões) por urgência (Política art. 13, § 3º)."));
        ind.add(new Indicator("DOSSIERS", "Dossiês completos na revisão por amostragem",
                "Dossiês sem pendência ÷ dossiês revisados", dossiers, HUNDRED, "100%",
                dossiers == null ? null : dossiers.compareTo(HUNDRED) >= 0,
                rv.isEmpty() ? "Nenhum dossiê revisado no período." : rv.size() + " dossiê(s) revisado(s)."));

        LocalDate today = calendar.today();
        List<SupplierApplication> approved = applications.findAll().stream()
                .filter(a -> a.getStatus() == ApplicationStatus.APPROVED && a.isActiveOn(today)).toList();
        List<LineStats> lineStats = new ArrayList<>();
        for (SupplyLine l : lines.findAll()) {
            if (!l.isActive()) {
                continue;
            }
            List<SupplierApplication> inLine = approved.stream()
                    .filter(a -> a.getLines().stream().anyMatch(x -> x.getCode().equals(l.getCode()))).toList();
            int activeT1 = (int) inLine.stream().filter(a -> a.getTerritories().contains(Territory.T1)).count();
            lineStats.add(new LineStats(l.getCode(), l.getShortName(), activeT1, inLine.size()));
        }
        lineStats.sort((a, b) -> a.code().compareTo(b.code()));
        return new Report(from, to, contracted.size(), total, ind, bands, projects, lineStats, approved.size());
    }

    private BigDecimal averageDays(List<ServiceOrder> list, Map<Long, Tender> tenderById) {
        List<Integer> days = new ArrayList<>();
        for (ServiceOrder o : list) {
            Tender t = tenderById.get(o.getTenderId());
            if (t == null) {
                continue;
            }
            // O DFD é formalizado na criação da oportunidade (D-040).
            LocalDate dfd = LocalDate.ofInstant(t.getCreatedAt(), clock.getZone());
            LocalDate of = LocalDate.ofInstant(o.getIssuedAt(), clock.getZone());
            days.add(calendar.calendar().businessDaysBetween(dfd, of));
        }
        if (days.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(days.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(days.size()), 1, RoundingMode.HALF_UP);
    }

    private static Indicator share(String code, String label, String formula, BigDecimal part, BigDecimal total,
            BigDecimal goalShare, boolean atLeast, String note) {
        BigDecimal value = total.signum() == 0 ? null : pct(part, total);
        BigDecimal goal = goalShare.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP);
        Boolean met = value == null ? null : atLeast ? value.compareTo(goal) >= 0 : value.compareTo(goal) <= 0;
        return new Indicator(code, label, formula, value, goal, (atLeast ? "≥ " : "≤ ") + goal + "%", met,
                value == null ? "Nenhuma contratação no período." : note);
    }

    private static Indicator days(String code, String label, BigDecimal value, BigDecimal max) {
        return new Indicator(code, label, "Média (data da OF/OS − data do DFD), em dias úteis", value, max,
                "≤ " + max + " dias úteis", value == null ? null : value.compareTo(max) <= 0,
                value == null ? "Nenhuma ordem desta faixa no período." : null);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal total) {
        return part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<ServiceOrder> list) {
        return list.stream().map(ServiceOrder::getTotalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
