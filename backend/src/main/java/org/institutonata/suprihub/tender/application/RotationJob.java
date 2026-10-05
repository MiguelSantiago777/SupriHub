package org.institutonata.suprihub.tender.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** De hora em hora: expira convites do rodízio vencidos e convida o próximo. */
@Component
public class RotationJob {

    private static final Logger log = LoggerFactory.getLogger(RotationJob.class);

    private final RotationService rotation;

    public RotationJob(RotationService rotation) {
        this.rotation = rotation;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "tender-rotation-expiry")
    public void run() {
        int expired = rotation.expireOverdue();
        if (expired > 0) {
            log.info("Rodízio: {} convite(s) expirado(s)", expired);
        }
    }
}
