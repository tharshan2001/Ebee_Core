package com.ebee.inventory.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.notification.NotificationPort;
import com.ebee.inventory.dto.ExpiringStockResponse;
import com.ebee.user.repository.UserRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static com.ebee.user.enums.RoleType.ADMIN;
import static com.ebee.user.enums.RoleType.SUPER_ADMIN;

/** Every morning: tell admins about expired batches and batches expiring within the next 30 days. */
@Service
public class ExpiryAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryAlertScheduler.class);
    static final int WARN_DAYS = 30;

    private final InventoryService inventoryService;
    private final NotificationPort notificationPort;
    private final UserRepository userRepository;

    public ExpiryAlertScheduler(InventoryService inventoryService, NotificationPort notificationPort,
                                UserRepository userRepository) {
        this.inventoryService = inventoryService;
        this.notificationPort = notificationPort;
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "0 0 7 * * *")
    @SchedulerLock(name = "expiryAlerts", lockAtMostFor = "PT5M")
    public void alertExpiringStock() {
        String message = buildMessage(inventoryService.expiringStock(WARN_DAYS, null));
        if (message == null) {
            return;
        }
        for (var admin : userRepository.findByRoleIn(List.of(ADMIN, SUPER_ADMIN))) {
            notificationPort.createInternalAlert(admin, "Stock expiry", message, AlertType.STOCK_EXPIRING, null, "inventory");
        }
        log.info("Expiry alert sent: {}", message);
    }

    /** Null when there's nothing to report. */
    static String buildMessage(List<ExpiringStockResponse> batches) {
        long expired = batches.stream().filter(b -> b.daysLeft() < 0).count();
        long soon = batches.size() - expired;
        if (batches.isEmpty()) {
            return null;
        }
        BigDecimal expiredValue = batches.stream().filter(b -> b.daysLeft() < 0).map(ExpiringStockResponse::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder sb = new StringBuilder();
        if (expired > 0) {
            sb.append(expired).append(" batch(es) have expired (LKR ").append(expiredValue.setScale(2, java.math.RoundingMode.HALF_UP))
                    .append(" at cost) — write them off. ");
        }
        if (soon > 0) {
            sb.append(soon).append(" batch(es) expire within ").append(WARN_DAYS).append(" days.");
        }
        return sb.toString().trim();
    }
}
