package com.ebee.order.event;

import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Published after an order is created, its status changes, or it is
 * cancelled/returned. Consumed by
 * {@link OrderStatusChangedEventListener} to broadcast a STOMP message to
 * {@code /topic/orders} once the originating transaction commits.
 *
 * <p>The {@code created} flag is {@code true} when the previous status is
 * {@code null} (newly inserted order). Front-end consumers use this flag to
 * decide whether to insert a new row or update an existing one.
 *
 * <p>Carries the full {@link Order} reference so the listener can read any
 * eagerly-fetched associations without an extra query. Avoid leaking this
 * event outside the transaction boundary.
 */
@Getter
public class OrderStatusChangedEvent extends ApplicationEvent {

    private final Order order;
    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;
    private final LocalDateTime occurredAt;
    private final boolean created;

    /**
     * @param source         the publishing service (typically {@code this})
     * @param order          the order whose status changed (must be within an
     *                       active transaction)
     * @param previousStatus the status before the change, or {@code null} if
     *                       the order was just created
     * @param occurredAt     timestamp of the status change
     */
    public OrderStatusChangedEvent(Object source, Order order,
                                   OrderStatus previousStatus,
                                   LocalDateTime occurredAt) {
        super(source);
        this.order = order;
        this.previousStatus = previousStatus;
        this.newStatus = order.getStatus();
        this.occurredAt = occurredAt;
        this.created = (previousStatus == null);
    }
}
