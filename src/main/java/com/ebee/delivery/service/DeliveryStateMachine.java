package com.ebee.delivery.service;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Component
class DeliveryStateMachine implements DeliveryStateTransition {

    /**
     * Strict delivery state machine.
     * <p>
     * Happy path:  PENDING → ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED
     * Off-ramps:   ASSIGNED, PICKED_UP, IN_TRANSIT → FAILED
     * Retry:       FAILED → PENDING
     * Terminal:    FAILED → FAILED_PERMANENT (admin only)
     * <p>
     * Notes:
     * <ul>
     *   <li>PENDING transitions to ASSIGNED when order is added to a trip with a driver.</li>
     *   <li>DELIVERED and FAILED_PERMANENT are terminal — no outgoing transitions.</li>
     *   <li>FAILED_PERMANENT requires admin role.</li>
     *   <li>Same-status transitions are a no-op (idempotent).</li>
     * </ul>
     */
    private static final Map<DeliveryStatus, Set<DeliveryStatus>> VALID_TRANSITIONS = Map.of(
            DeliveryStatus.PENDING, Set.of(DeliveryStatus.ASSIGNED, DeliveryStatus.FAILED),
            DeliveryStatus.ASSIGNED, Set.of(DeliveryStatus.PICKED_UP, DeliveryStatus.FAILED),
            DeliveryStatus.PICKED_UP, Set.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.FAILED),
            DeliveryStatus.IN_TRANSIT, Set.of(DeliveryStatus.DELIVERED, DeliveryStatus.FAILED),
            DeliveryStatus.FAILED, Set.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED_PERMANENT),
            DeliveryStatus.DELIVERED, Set.of(),
            DeliveryStatus.FAILED_PERMANENT, Set.of()
    );

    @Override
    public TransitionResult transition(Order order, DeliveryStatus newStatus, String reason, boolean isAdmin) {
        DeliveryStatus currentStatus = order.getDeliveryStatus();

        if (currentStatus == null) {
            throw new IllegalArgumentException("Delivery has not been initialized for this order");
        }

        if (currentStatus == newStatus) {
            return new TransitionResult(false, false, currentStatus);
        }

        Set<DeliveryStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            String allowedList = allowed.isEmpty()
                    ? "none (terminal state)"
                    : String.join(", ",
                            allowed.stream().map(Enum::name).sorted().toList());
            throw new IllegalArgumentException(
                    "Invalid delivery status transition: " + currentStatus + " → " + newStatus
                            + ". Allowed: " + allowedList);
        }

        if (newStatus == DeliveryStatus.FAILED_PERMANENT && !isAdmin) {
            throw new SecurityException("Only admins can mark a delivery as permanently failed");
        }

        if (newStatus == DeliveryStatus.FAILED) {
            order.setDeliveryFailureReason(reason);
        }

        if (currentStatus == DeliveryStatus.FAILED && newStatus == DeliveryStatus.PENDING) {
            order.setDeliveryFailureReason(null);
            order.setDeliveryAssignedTo(null);
            order.setTrip(null);
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
