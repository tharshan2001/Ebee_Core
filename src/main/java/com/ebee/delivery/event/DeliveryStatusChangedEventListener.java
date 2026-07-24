package com.ebee.delivery.event;

import com.ebee.delivery.dto.DeliveryStatusChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryStatusChangedEventListener {

    public static final String TOPIC = "/topic/deliveries";

    private final SimpMessagingTemplate messagingTemplate;

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
