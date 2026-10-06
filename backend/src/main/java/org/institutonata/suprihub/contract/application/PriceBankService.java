package org.institutonata.suprihub.contract.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.catalog.infra.SupplyLineRepository;
import org.institutonata.suprihub.contract.domain.ContractEnums.PriceSource;
import org.institutonata.suprihub.contract.domain.PriceBankEntry;
import org.institutonata.suprihub.contract.infra.PriceBankRepository;
import org.institutonata.suprihub.parameter.application.CalendarService;
import org.institutonata.suprihub.parameter.application.ParameterService;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.tender.application.PriceReferenceSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Banco de Preços (aba da Planilha; Manual 4.1): preço médio, menor preço e teto para o
 * rodízio (média + {@code price_bank_ceiling_margin}) por item padronizado.
 */
@Service
public class PriceBankService implements PriceReferenceSource {

    /** Estatística de um item (linha + descrição + unidade). */
    public record ItemStats(String lineCode, String lineName, String description, String unit, int count,
            BigDecimal average, BigDecimal min, BigDecimal max, BigDecimal ceiling, LocalDate lastObserved,
            List<PriceBankEntry> entries) {
    }

    public record Manual(String lineCode, String description, String unit, BigDecimal unitPrice, PriceSource source,
            String reference, LocalDate observedOn) {
    }

    private final PriceBankRepository entries;
    private final SupplyLineRepository lines;
    private final ParameterService parameters;
    private final CalendarService calendar;
    private final AuditService audit;
    private final Clock clock;

    public PriceBankService(PriceBankRepository entries, SupplyLineRepository lines, ParameterService parameters,
            CalendarService calendar, AuditService audit, Clock clock) {
        this.entries = entries;
        this.lines = lines;
        this.parameters = parameters;
        this.calendar = calendar;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ItemStats> search(String lineCode, String q) {
        Short lineId = null;
        if (lineCode != null && !lineCode.isBlank()) {
            lineId = lines.findByCode(lineCode).map(SupplyLine::getId).orElse((short) -1);
        }
        List<PriceBankEntry> found = entries.search(lineId, q == null ? "" : q.trim(), PageRequest.of(0, 2000));
        Map<Short, SupplyLine> lineById = new LinkedHashMap<>();
        lines.findAll().forEach(l -> lineById.put(l.getId(), l));
        Map<String, List<PriceBankEntry>> groups = new LinkedHashMap<>();
        for (PriceBankEntry e : found) {
            groups.computeIfAbsent(key(e.getSupplyLineId(), e.getItemDescription(), e.getUnit()),
                    k -> new ArrayList<>()).add(e);
        }
        BigDecimal margin = parameters.decimal(ParameterKey.PRICE_BANK_CEILING_MARGIN);
        List<ItemStats> out = new ArrayList<>();
        for (List<PriceBankEntry> g : groups.values()) {
            PriceBankEntry first = g.get(0);
            SupplyLine line = lineById.get(first.getSupplyLineId());
            BigDecimal avg = average(g);
            out.add(new ItemStats(line == null ? null : line.getCode(), line == null ? null : line.getShortName(),
                    first.getItemDescription(), first.getUnit(), g.size(), avg,
                    g.stream().map(PriceBankEntry::getUnitPrice).min(Comparator.naturalOrder()).orElseThrow(),
                    g.stream().map(PriceBankEntry::getUnitPrice).max(Comparator.naturalOrder()).orElseThrow(),
                    ceiling(avg, margin), g.stream().map(PriceBankEntry::getObservedOn)
                            .max(Comparator.naturalOrder()).orElseThrow(),
                    g.stream().limit(20).toList()));
        }
        out.sort(Comparator.comparing(ItemStats::lineCode, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.description().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Lançamento manual: cotação, Painel de Preços ou pesquisa de mercado. */
    @Transactional
    public PriceBankEntry add(Manual m, long userId) {
        List<Map<String, String>> issues = new ArrayList<>();
        SupplyLine line = m.lineCode() == null ? null : lines.findByCode(m.lineCode()).orElse(null);
        if (line == null) {
            issues.add(Map.of("field", "lineCode", "message", "Escolha a linha."));
        }
        if (m.description() == null || m.description().isBlank()) {
            issues.add(Map.of("field", "description", "message", "Descreva o item (descrição padronizada)."));
        }
        if (m.unit() == null || m.unit().isBlank()) {
            issues.add(Map.of("field", "unit", "message", "Informe a unidade."));
        }
        if (m.unitPrice() == null || m.unitPrice().signum() <= 0) {
            issues.add(Map.of("field", "unitPrice", "message", "Informe o preço unitário."));
        }
        if (m.source() == null || m.source() == PriceSource.CONTRACT) {
            issues.add(Map.of("field", "source", "message",
                    "Escolha a fonte (contratações entram sozinhas no recebimento)."));
        }
        if (m.observedOn() == null || m.observedOn().isAfter(calendar.today())) {
            issues.add(Map.of("field", "observedOn", "message", "Informe a data do preço."));
        }
        if (!issues.isEmpty()) {
            throw new BusinessException("PRICE_INVALID", "Corrija o lançamento.", HttpStatus.UNPROCESSABLE_ENTITY,
                    issues);
        }
        PriceBankEntry e = entries.save(new PriceBankEntry(line.getId(), m.description(), m.unit(),
                m.unitPrice().setScale(2, RoundingMode.HALF_UP), m.source(), trim(m.reference()), null, null,
                m.observedOn(), userId, clock.instant()));
        audit.record("price_bank_entry", e.getId(), "CREATE", null,
                Map.of("line", line.getCode(), "price", e.getUnitPrice(), "source", m.source().name()));
        return e;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reference> referenceFor(short supplyLineId, String itemDescription, String unit) {
        List<PriceBankEntry> g = entries.findBySupplyLineIdAndItemDescriptionIgnoreCaseAndUnitIgnoreCase(
                supplyLineId, itemDescription.trim(), unit.trim());
        if (g.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal avg = average(g);
        return Optional.of(new Reference(g.size(), avg,
                ceiling(avg, parameters.decimal(ParameterKey.PRICE_BANK_CEILING_MARGIN))));
    }

    static BigDecimal average(List<PriceBankEntry> g) {
        return g.stream().map(PriceBankEntry::getUnitPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(g.size()), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal ceiling(BigDecimal average, BigDecimal margin) {
        return average.multiply(BigDecimal.ONE.add(margin)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String key(Short line, String description, String unit) {
        return line + "|" + description.trim().toLowerCase(Locale.ROOT) + "|" + unit.trim().toLowerCase(Locale.ROOT);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
