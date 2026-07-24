package com.ebee.order.event;

import com.ebee.order.dto.OrderStatusChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusChangedEventListener {

    public static final String TOPIC = "/topic/orders";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderStatusChangedMessage message = new OrderStatusChangedMessage(
                event.getOrder().getId(),
                event.getOrder().getOrderNumber(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getOrder().getAgent() != null
                        ? event.getOrder().getAgent().getUser().getFirstName() + " "
                        + event.getOrder().getAgent().getUser().getLastName()
                        : null,
                event.getOrder().getCustomer() != null
                        ? event.getOrder().getCustomer().getFirstName() + " "
                        + event.getOrder().getCustomer().getLastName()
                        : null,
                event.getOrder().getCustomer() != null
                        ? event.getOrder().getCustomer().getPhoneNumber()
                        : null,
                event.getOrder().getTotalAmount(),
                event.getOrder().getPaidAmount(),
                event.getOccurredAt(),
                event.isCreated()
        );
        try {
            messagingTemplate.convertAndSend(TOPIC, message);
            log.info("Broadcast order {} {} → {}",
                    event.getOrder().getOrderNumber(),
                    event.isCreated() ? "CREATED" : event.getPreviousStatus(),
                    event.getNewStatus());
        } catch (Exception e) {
            log.error("Failed to broadcast order status change for order {}: {}",
                    event.getOrder().getOrderNumber(), e.getMessage(), e);
        }
    }
}
