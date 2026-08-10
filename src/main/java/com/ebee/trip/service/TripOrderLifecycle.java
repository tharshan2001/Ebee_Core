package com.ebee.trip.service;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared trip-to-order lifecycle rules used by both the admin-facing
 * {@link TripService} and the driver-facing
 * {@link com.ebee.driver.service.DriverAppService} so that trip completion and
 * cancellation detach and reset orders identically on both paths.
 * <p>
 * "Unresolved" means the delivery is still in a resettable state (PENDING,
 * ASSIGNED, PICKED_UP, IN_TRANSIT, or retryable FAILED) and the order itself is
 * not in a terminal sale state (CANCELLED, RETURNED, RETURN_EXCHANGE).
 */
@Component
@RequiredArgsConstructor
public class TripOrderLifecycle {

    private static final Set<DeliveryStatus> RESETTABLE_DELIVERY_STATUSES = Set.of(
            DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED,
            DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.FAILED);

    private static final Set<OrderStatus> TERMINAL_ORDER_STATUSES = Set.of(
            OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.RETURN_EXCHANGE);

    private final OrderRepository orderRepository;

    public List<Order> getTripOrders(UUID tripId) {
        return orderRepository.findByTripId(tripId);
    }

    public boolean isUnresolved(Order order) {
        return order.getDeliveryStatus() != null
                && RESETTABLE_DELIVERY_STATUSES.contains(order.getDeliveryStatus())
                && !TERMINAL_ORDER_STATUSES.contains(order.getStatus());
    }

    public boolean isPendingForAssignment(Order order) {
        return order.getDeliveryStatus() == DeliveryStatus.PENDING && isUnresolved(order);
    }

    public List<Order> getUnresolvedOrders(UUID tripId) {
        return getTripOrders(tripId).stream()
                .filter(this::isUnresolved)
                .toList();
    }

    /**
     * Whether an order is in a state that allows the trip to complete:
     * delivered, permanently failed, or its sale was already closed out
     * (cancelled/returned). Orders in any resettable delivery state, or with
     * no delivery state at all, are considered non-terminal and block
     * completion.
     */
    public boolean isTerminalForTrip(Order order) {
        DeliveryStatus deliveryStatus = order.getDeliveryStatus();
        return (deliveryStatus == DeliveryStatus.DELIVERED || deliveryStatus == DeliveryStatus.FAILED_PERMANENT)
                || TERMINAL_ORDER_STATUSES.contains(order.getStatus());
    }

    /**
     * Detach an order from its trip and reset its delivery to a fresh,
     * unassigned PENDING state so it can be retried or re-assigned.
     */
    public void detachAndReset(Order order) {
        order.setTrip(null);
        order.setDeliveryAssignedTo(null);
        order.setDeliveryStatus(DeliveryStatus.PENDING);
        order.setDeliveryFailureReason(null);
        orderRepository.save(order);
    }
}
