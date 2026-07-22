package com.ebee.delivery.service;

import com.ebee.delivery.config.DeliveryProperties;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.order.entity.Order;
import com.ebee.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTimeoutScheduler {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final DeliveryStateTransition deliveryStateTransition;
    private final DeliveryProperties deliveryProperties;

    @Scheduled(fixedRate = 900_000)
    @SchedulerLock(name = "failStalledDeliveries", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    @Transactional
    public void failStalledDeliveries() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pickupCutoff = now.minus(deliveryProperties.getTimeout().getPickup());
        LocalDateTime inTransitCutoff = now.minus(deliveryProperties.getTimeout().getInTransit());

        int failed = failStalled(DeliveryStatus.PICKED_UP, pickupCutoff);
        failed += failStalled(DeliveryStatus.IN_TRANSIT, inTransitCutoff);

        if (failed > 0) {
            log.info("Auto-failed {} stalled deliveries (pickup cutoff: {}, in-transit cutoff: {})",
                    failed, pickupCutoff, inTransitCutoff);
        }
    }

    private int failStalled(DeliveryStatus status, LocalDateTime cutoff) {
        List<Order> stalled = deliveryRepository.findStalledDeliveries(status, cutoff);
        for (Order order : stalled) {
            try {
                deliveryStateTransition.transition(order, DeliveryStatus.FAILED,
                        "Delivery stalled — automatically failed after timeout", true);
                orderRepository.save(order);
                log.warn("Auto-failed delivery {} (was {} since {})",
                        order.getOrderNumber(), status, order.getUpdatedAt());
            } catch (Exception e) {
                log.error("Failed to auto-fail delivery {}: {}",
                        order.getOrderNumber(), e.getMessage());
            }
        }
        return stalled.size();
    }
}
