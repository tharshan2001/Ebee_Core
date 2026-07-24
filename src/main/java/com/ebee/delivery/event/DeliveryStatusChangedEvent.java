package com.ebee.delivery.event;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published after a delivery status change commits. Consumed by
 * {@link DeliveryStatusChangedEventListener} which broadcasts a STOMP message
 * to {@code /topic/deliveries} for real-time admin/front-end updates.
 *
 * <p>Unlike {@link com.ebee.order.event.OrderStatusChangedEvent} which carries
 * the full {@code Order} entity, this event extracts the fields it needs at
 * construction time. This avoids accidentally leaking uncommitted entity state
 * through the event payload.
 *
 * <p>The {@code driverId} and {@code driverName} fields are nullable — they are
 * only populated when an assignment exists on the order.
 */
@Getter
public class DeliveryStatusChangedEvent extends ApplicationEvent {

    private final UUID orderId;
    private final String orderNumber;
    private final DeliveryStatus previousStatus;
    private final DeliveryStatus newStatus;
    private final UUID driverId;
    private final String driverName;
    private final String customerName;
    private final String district;
    private final String reason;
    private final LocalDateTime occurredAt;

    /**
     * @param source         the publishing service (typically {@code this})
     * @param order          the order whose delivery status changed
     * @param previousStatus the delivery status before the change
     * @param reason         human-readable reason for the change (e.g.
     *                       customer rejection, failed attempt); may be
     *                       {@code null} for status transitions that don't
     *                       require one
     * @param occurredAt     timestamp of the status change
     */
    public DeliveryStatusChangedEvent(Object source, Order order,
                                      DeliveryStatus previousStatus,
                                      String reason,
                                      LocalDateTime occurredAt) {
        super(source);
        this.orderId = order.getId();
        this.orderNumber = order.getOrderNumber();
        this.previousStatus = previousStatus;
        this.newStatus = order.getDeliveryStatus();
        this.driverId = order.getDeliveryAssignedTo() != null
                ? order.getDeliveryAssignedTo().getId() : null;
        this.driverName = order.getDeliveryAssignedTo() != null
                ? order.getDeliveryAssignedTo().getFirstName() + " " + order.getDeliveryAssignedTo().getLastName()
                : null;
        this.customerName = order.getCustomer() != null
                ? order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName()
                : null;
        this.district = order.getDistrict();
        this.reason = reason;
        this.occurredAt = occurredAt;
    }
}
