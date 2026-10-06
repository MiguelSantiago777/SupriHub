package org.institutonata.suprihub.sanction.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** De hora em hora: prazos de defesa e de recurso vencidos fazem o processo andar. */
@Component
public class SanctionJob {

    private static final Logger log = LoggerFactory.getLogger(SanctionJob.class);

    private final SanctionService sanctions;

    public SanctionJob(SanctionService sanctions) {
        this.sanctions = sanctions;
    }

    @Scheduled(cron = "0 20 * * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "sanction-deadlines")
    public void run() {
        int n = sanctions.advanceDeadlines();
        if (n > 0) {
            log.info("Sanções: {} processo(s) avançado(s) por prazo", n);
        }
    }
}
