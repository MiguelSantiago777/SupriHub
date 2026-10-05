package org.institutonata.suprihub.catalog.application;

import java.time.Clock;
import java.util.List;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.catalog.api.CatalogDtos.SupplyLineDto;
import org.institutonata.suprihub.catalog.api.CatalogDtos.UpdateSupplyLineRequest;
import org.institutonata.suprihub.catalog.api.CatalogMapper;
import org.institutonata.suprihub.catalog.domain.Municipality;
import org.institutonata.suprihub.catalog.domain.SupplyLine;
import org.institutonata.suprihub.catalog.infra.MunicipalityRepository;
import org.institutonata.suprihub.catalog.infra.SupplyLineRepository;
import org.institutonata.suprihub.catalog.domain.TerritoryResolver;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final SupplyLineRepository lines;
    private final MunicipalityRepository municipalities;
    private final CatalogMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public CatalogService(SupplyLineRepository lines, MunicipalityRepository municipalities, CatalogMapper mapper,
            AuditService audit, Clock clock) {
        this.lines = lines;
        this.municipalities = municipalities;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SupplyLine> activeLines() {
        return lines.findByActiveTrueOrderByDisplayOrder();
    }

    @Transactional(readOnly = true)
    public List<SupplyLine> allLines() {
        return lines.findAllByOrderByDisplayOrder();
    }

    @Transactional(readOnly = true)
    public SupplyLine line(String code) {
        return lines.findByCode(code).orElseThrow(() -> new NotFoundException("Linha de fornecimento"));
    }

    @Transactional(readOnly = true)
    public List<Municipality> rjMunicipalities() {
        return municipalities.findByUfOrderByName(TerritoryResolver.RJ);
    }

    @Transactional
    public SupplyLine updateLine(String code, UpdateSupplyLineRequest req) {
        SupplyLine line = line(code);
        if (line.getVersion() != req.version()) {
            throw new ObjectOptimisticLockingFailureException(SupplyLine.class, code);
        }
        SupplyLineDto before = mapper.toDto(line);
        line.update(req.name(), req.shortName(), req.scope(), req.cnaeReference(), req.allowsIndividual(),
                req.allowsMediumLarge(), req.active(), clock.instant());
        audit.record("supply_line", code, "UPDATE", before, mapper.toDto(line));
        return line;
    }
}
