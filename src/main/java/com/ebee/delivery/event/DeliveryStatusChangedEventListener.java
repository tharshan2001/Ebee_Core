package com.ebee.delivery.event;

import com.ebee.delivery.dto.DeliveryStatusChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link DeliveryStatusChangedEvent} and broadcasts a
 * {@link DeliveryStatusChangedMessage} to all STOMP subscribers on
 * {@code /topic/deliveries}.
 *
 * <p>Like its sibling
 * {@link com.ebee.order.event.OrderStatusChangedEventListener}, this listener
 * fires only after the enclosing transaction commits. A messaging failure is
 * logged but does not propagate.
 *
 * <p>Topic access is enforced by
 * {@link com.ebee.driver.config.WebSocketAuthInterceptor}. The
 * {@code /topic/deliveries} topic is available to ADMIN, SUPER_ADMIN, AGENT,
 * and DRIVER roles — the same roles that interact with deliveries through the
 * REST API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryStatusChangedEventListener {

    /** STOMP destination broadcast on every delivery status change. */
    public static final String TOPIC = "/topic/deliveries";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Convert the event into a lightweight DTO and send it to all connected
     * subscribers on {@link #TOPIC}.
     *
     * @param event delivery status change published by the delivery service
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryStatusChanged(DeliveryStatusChangedEvent event) {
        DeliveryStatusChangedMessage message = new DeliveryStatusChangedMessage(
                event.getOrderId(),
                event.getOrderNumber(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getDriverId(),
                event.getDriverName(),
                event.getCustomerName(),
                event.getDistrict(),
                event.getReason(),
                event.getOccurredAt()
        );
        try {
            messagingTemplate.convertAndSend(TOPIC, message);
            log.info("Broadcast delivery {} {} → {}",
                    event.getOrderNumber(), event.getPreviousStatus(), event.getNewStatus());
        } catch (Exception e) {
            log.error("Failed to broadcast delivery status change for order {}: {}",
                    event.getOrderNumber(), e.getMessage(), e);
        }
    }
}
