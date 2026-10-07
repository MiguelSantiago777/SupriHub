package org.institutonata.suprihub.supplier.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Todo dia às 6h: certidões a vencer e vencidas, inativação temporária e aviso de renovação. */
@Component
public class SupplierMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierMaintenanceJob.class);

    private final SupplierMaintenanceService maintenance;

    public SupplierMaintenanceJob(SupplierMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "supplier-maintenance")
    public void run() {
        int n = maintenance.runDaily();
        if (n > 0) {
            log.info("Manutenção do cadastro: {} credenciado(s) com aviso ou mudança de situação", n);
        }
    }
}
