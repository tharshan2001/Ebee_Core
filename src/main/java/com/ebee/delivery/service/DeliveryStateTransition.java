package com.ebee.delivery.service;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;

public interface DeliveryStateTransition {

    record TransitionResult(boolean wasDelivered, boolean wasFailedPermanent, DeliveryStatus previousStatus) {}

    TransitionResult transition(Order order, DeliveryStatus newStatus, String reason, boolean isAdmin);
}
