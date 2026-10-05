package org.institutonata.suprihub.parameter.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.institutonata.suprihub.audit.application.AuditService;
import org.institutonata.suprihub.parameter.domain.Parameter;
import org.institutonata.suprihub.parameter.domain.ParameterHistory;
import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.parameter.domain.ValueType;
import org.institutonata.suprihub.parameter.infra.ParameterHistoryRepository;
import org.institutonata.suprihub.parameter.infra.ParameterRepository;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura tipada e alteração (com histórico e auditoria) dos parâmetros de negócio.
 * Mudar um parâmetro muda o comportamento do sistema sem alterar código.
 */
@Service
public class ParameterService implements ApplicationRunner {

    private final ParameterRepository repository;
    private final ParameterHistoryRepository historyRepository;
    private final AuditService audit;
    private final Clock clock;

    public ParameterService(ParameterRepository repository, ParameterHistoryRepository historyRepository,
            AuditService audit, Clock clock) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.audit = audit;
        this.clock = clock;
    }

    /** Falha ao subir se o banco não tiver todas as chaves conhecidas pelo código. */
    @Override
    public void run(ApplicationArguments args) {
        Set<String> existing = repository.findAll().stream().map(Parameter::getKey).collect(Collectors.toSet());
        List<String> missing = Arrays.stream(ParameterKey.values()).map(ParameterKey::dbKey)
                .filter(k -> !existing.contains(k)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Parâmetros ausentes no banco: " + missing);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal decimal(ParameterKey key) {
        return new BigDecimal(raw(key, ValueType.DECIMAL));
    }

    @Transactional(readOnly = true)
    public int integer(ParameterKey key) {
        return Integer.parseInt(raw(key, ValueType.INTEGER));
    }

    @Transactional(readOnly = true)
    public boolean bool(ParameterKey key) {
        return Boolean.parseBoolean(raw(key, ValueType.BOOLEAN));
    }

    @Transactional(readOnly = true)
    public String text(ParameterKey key) {
        return raw(key, ValueType.TEXT);
    }

    @Transactional(readOnly = true)
    public List<Parameter> all() {
        return repository.findAllByOrderByCategoryAscKeyAsc();
    }

    @Transactional(readOnly = true)
    public List<ParameterHistory> history(String key) {
        return historyRepository.findByKeyOrderByChangedAtDesc(key);
    }

    @Transactional
    public Parameter update(String key, String newValue, String reason) {
        Parameter p = repository.findById(key).orElseThrow(() -> new NotFoundException("Parâmetro"));
        String old = p.getValue();
        try {
            p.changeValue(newValue, CurrentUser.id().orElse(null), clock.instant());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_PARAMETER_VALUE",
                    "Valor inválido para \"" + p.getDescription() + "\".");
        }
        validateConsistency(p);
        historyRepository.save(new ParameterHistory(key, old, p.getValue(), reason,
                CurrentUser.id().orElse(null), clock.instant()));
        audit.record("parameter", key, "UPDATE", Map.of("value", old), Map.of("value", p.getValue()), reason);
        return p;
    }

    private void validateConsistency(Parameter changed) {
        ParameterKey key = ParameterKey.fromDbKey(changed.getKey());
        if (key.type() == ValueType.DECIMAL && new BigDecimal(changed.getValue()).signum() < 0) {
            throw new BusinessException("INVALID_PARAMETER_VALUE", "O valor não pode ser negativo.");
        }
        if (key.type() == ValueType.INTEGER && Integer.parseInt(changed.getValue()) < 0) {
            throw new BusinessException("INVALID_PARAMETER_VALUE", "O valor não pode ser negativo.");
        }
        if (key.name().endsWith("_MARGIN") || key.name().endsWith("_SHARE")) {
            if (new BigDecimal(changed.getValue()).compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException("INVALID_PARAMETER_VALUE",
                        "Percentuais devem ser informados como fração (ex.: 0.10 para 10%).");
            }
        }
        if (key == ParameterKey.BAND1_UPPER_LIMIT || key == ParameterKey.BAND2_UPPER_LIMIT) {
            BigDecimal band1 = key == ParameterKey.BAND1_UPPER_LIMIT ? new BigDecimal(changed.getValue())
                    : decimal(ParameterKey.BAND1_UPPER_LIMIT);
            BigDecimal band2 = key == ParameterKey.BAND2_UPPER_LIMIT ? new BigDecimal(changed.getValue())
                    : decimal(ParameterKey.BAND2_UPPER_LIMIT);
            if (band1.compareTo(band2) >= 0) {
                throw new BusinessException("INVALID_BAND_LIMITS",
                        "O limite da Faixa 1 deve ser menor que o limite da Faixa 2.");
            }
        }
    }

    private String raw(ParameterKey key, ValueType expected) {
        if (key.type() != expected) {
            throw new IllegalArgumentException("Parâmetro " + key + " é do tipo " + key.type());
        }
        return repository.findById(key.dbKey())
                .orElseThrow(() -> new IllegalStateException("Parâmetro ausente: " + key.dbKey()))
                .getValue();
    }
}
