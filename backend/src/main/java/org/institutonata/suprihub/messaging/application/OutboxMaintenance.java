package org.institutonata.suprihub.messaging.application;

import java.time.Clock;
import java.util.UUID;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.institutonata.suprihub.messaging.domain.OutboxEvent;
import org.institutonata.suprihub.messaging.infra.OutboxEventRepository;
import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.institutonata.suprihub.shared.error.BusinessException;
import org.institutonata.suprihub.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operação do outbox: consulta, reprocessamento manual e limpeza de publicados. */
@Service
public class OutboxMaintenance {

    private static final Logger log = LoggerFactory.getLogger(OutboxMaintenance.class);

    private final OutboxEventRepository repository;
    private final Clock clock;
    private final SupriHubProperties.Messaging config;

    public OutboxMaintenance(OutboxEventRepository repository, Clock clock, SupriHubProperties props) {
        this.repository = repository;
        this.clock = clock;
        this.config = props.messaging();
    }

    @Transactional(readOnly = true)
    public Page<OutboxEvent> list(OutboxEvent.Status status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    @Transactional
    public void retry(UUID id) {
        OutboxEvent event = repository.findById(id).orElseThrow(() -> new NotFoundException("Evento"));
        if (event.getStatus() != OutboxEvent.Status.FAILED) {
            throw new BusinessException("OUTBOX_NOT_FAILED", "Só é possível reprocessar eventos com falha.");
        }
        event.retry(clock.instant());
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "outbox-cleanup")
    @Transactional
    public void purgePublished() {
        int removed = repository.deletePublishedBefore(clock.instant().minus(config.publishedRetention()));
        if (removed > 0) {
            log.info("Outbox: {} eventos publicados removidos", removed);
        }
    }
}
