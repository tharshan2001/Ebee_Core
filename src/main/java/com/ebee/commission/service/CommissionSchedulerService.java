package com.ebee.commission.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommissionSchedulerService {

    private final CommissionLifecycleService lifecycleService;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "processApprovedCommissions")
    public void processApprovedCommissions() {
        log.info("Running scheduled auto-processing of approved commissions (10-day hold)");
        try {
            int count = lifecycleService.processApprovedCommissions();
            if (count > 0) {
                log.info("Auto-processed {} commissions from APPROVED to PROCESSING", count);
            }
        } catch (Exception e) {
            log.error("Failed to auto-process commissions: {}", e.getMessage(), e);
        }
    }
}
