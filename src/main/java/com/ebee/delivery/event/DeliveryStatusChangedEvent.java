package com.ebee.delivery.event;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

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
