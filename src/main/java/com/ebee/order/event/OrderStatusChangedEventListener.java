package com.ebee.order.event;

import com.ebee.order.dto.OrderStatusChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link OrderStatusChangedEvent} and broadcasts an
 * {@link OrderStatusChangedMessage} to all STOMP subscribers on
 * {@code /topic/orders}.
 *
 * <p>The listener fires only after the enclosing transaction commits
 * ({@code AFTER_COMMIT}), ensuring clients never receive updates for rolled-back
 * changes. The broadcast is fire-and-forget — a messaging failure is logged but
 * does not propagate, so a broken WebSocket connection cannot bring down the
 * transaction that triggered it.
 *
 * <p>Topic access is enforced server-side by
 * {@link com.ebee.driver.config.WebSocketAuthInterceptor} which checks the
 * subscriber's role against the registered authorizations. Only ADMIN and
 * SUPER_ADMIN roles may subscribe to this topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusChangedEventListener {

    /** STOMP destination broadcast on every order status change. */
    public static final String TOPIC = "/topic/orders";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Convert the event into a lightweight DTO and send it to all connected
     * subscribers on {@link #TOPIC}.
     *
     * @param event order status change published by
     *              {@link com.ebee.order.service.OrderServiceImpl}
     */
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
