package com.ebee.order.event;

import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class OrderStatusChangedEvent extends ApplicationEvent {

    private final Order order;
    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;
    private final LocalDateTime occurredAt;
    private final boolean created;

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
