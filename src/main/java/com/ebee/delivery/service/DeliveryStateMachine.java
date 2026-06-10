package com.ebee.delivery.service;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class DeliveryStateMachine implements DeliveryStateTransition {

    @Override
    public TransitionResult transition(Order order, DeliveryStatus newStatus, String reason, boolean isAdmin) {
        DeliveryStatus currentStatus = order.getDeliveryStatus();

        if (currentStatus == null) {
            throw new IllegalArgumentException("Delivery has not been initialized for this order");
        }

        if (currentStatus == DeliveryStatus.DELIVERED || currentStatus == DeliveryStatus.FAILED_PERMANENT) {
            throw new IllegalArgumentException("Cannot update status of a " +
                    (currentStatus == DeliveryStatus.DELIVERED ? "delivered" : "permanently failed") + " delivery");
        }

        if (currentStatus == DeliveryStatus.FAILED
                && newStatus != DeliveryStatus.PENDING
                && newStatus != DeliveryStatus.FAILED_PERMANENT) {
            throw new IllegalArgumentException("Failed delivery can only be retried (PENDING) or marked permanent (FAILED_PERMANENT)");
        }

        if (newStatus == DeliveryStatus.FAILED) {
            order.setDeliveryFailureReason(reason);
        }

        if (currentStatus == DeliveryStatus.FAILED && newStatus == DeliveryStatus.PENDING) {
            order.setDeliveryFailureReason(null);
            order.setDeliveryAssignedTo(null);
        }

        if (newStatus == DeliveryStatus.FAILED_PERMANENT && !isAdmin) {
            throw new SecurityException("Only admins can mark a delivery as permanently failed");
        }

        if (currentStatus == DeliveryStatus.FAILED && newStatus == DeliveryStatus.FAILED_PERMANENT) {
            if (reason != null) {
                order.setDeliveryFailureReason(reason);
            }
        }

        order.setDeliveryStatus(newStatus);

        boolean wasDelivered = newStatus == DeliveryStatus.DELIVERED;
        boolean wasFailedPermanent = newStatus == DeliveryStatus.FAILED_PERMANENT;

        if (wasDelivered) {
            order.setDeliveredAt(LocalDateTime.now());
            order.setStatus(OrderStatus.DELIVERED);
        }

        if (wasFailedPermanent) {
            order.setStatus(OrderStatus.PENDING);
            order.setDeliveryAssignedTo(null);
            order.setDeliveryScheduledDate(null);
            order.setDeliveryNotes(null);
        }

        return new TransitionResult(wasDelivered, wasFailedPermanent, currentStatus);
    }
}
