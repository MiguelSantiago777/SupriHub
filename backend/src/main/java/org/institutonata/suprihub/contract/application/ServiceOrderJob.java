package org.institutonata.suprihub.contract.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** De hora em hora: ordens não assinadas no prazo (Edital 9.1) ficam como "não assinada". */
@Component
public class ServiceOrderJob {

    private static final Logger log = LoggerFactory.getLogger(ServiceOrderJob.class);

    private final ServiceOrderService orders;

    public ServiceOrderJob(ServiceOrderService orders) {
        this.orders = orders;
    }

    @Scheduled(cron = "0 15 * * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "service-order-signature-expiry")
    public void run() {
        int expired = orders.expireOverdue();
        if (expired > 0) {
            log.info("Contratação: {} ordem(ns) não assinada(s) no prazo", expired);
        }
    }
}
