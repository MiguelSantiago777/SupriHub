package org.institutonata.suprihub.judgment.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** De hora em hora: convocações para cobrir com prazo vencido passam à próxima finalista. */
@Component
public class CoverJob {

    private static final Logger log = LoggerFactory.getLogger(CoverJob.class);

    private final JudgmentService judgments;

    public CoverJob(JudgmentService judgments) {
        this.judgments = judgments;
    }

    @Scheduled(cron = "0 10 * * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "judgment-cover-expiry")
    public void run() {
        int expired = judgments.expireOverdue();
        if (expired > 0) {
            log.info("Julgamento: {} convocação(ões) para cobrir expirada(s)", expired);
        }
    }
}
