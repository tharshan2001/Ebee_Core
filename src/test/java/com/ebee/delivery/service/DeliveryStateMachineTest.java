package com.ebee.delivery.service;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryStateMachineTest {

    private DeliveryStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new DeliveryStateMachine();
    }

    private Order orderWithStatus(DeliveryStatus status) {
        return Order.builder()
                .id(UUID.randomUUID())
                .deliveryStatus(status)
                .build();
    }

    // ---------- PENDING ----------

    @Test
    void pending_canGoToAssigned() {
        Order order = orderWithStatus(DeliveryStatus.PENDING);

        var result = stateMachine.transition(order, DeliveryStatus.ASSIGNED, null, true);

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(result.wasDelivered()).isFalse();
        assertThat(result.wasFailedPermanent()).isFalse();
    }

    @Test
    void pending_canGoToFailed() {
        Order order = orderWithStatus(DeliveryStatus.PENDING);

        var result = stateMachine.transition(order, DeliveryStatus.FAILED, "Customer unavailable", true);

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(order.getDeliveryFailureReason()).isEqualTo("Customer unavailable");
        assertThat(result.wasDelivered()).isFalse();
        assertThat(result.wasFailedPermanent()).isFalse();
    }

    @Test
    void pending_throwsForInvalidTransition() {
        Order order = orderWithStatus(DeliveryStatus.PENDING);

        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.PICKED_UP, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition");
    }

    // ---------- Happy path ----------

    @Test
    void assigned_canGoToPickedUp() {
        Order order = orderWithStatus(DeliveryStatus.ASSIGNED);
        var result = stateMachine.transition(order, DeliveryStatus.PICKED_UP, null, false);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(result.wasDelivered()).isFalse();
        assertThat(result.wasFailedPermanent()).isFalse();
        assertThat(result.previousStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    @Test
    void assigned_canGoToFailed() {
        Order order = orderWithStatus(DeliveryStatus.ASSIGNED);
        stateMachine.transition(order, DeliveryStatus.FAILED, "Customer refused", false);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(order.getDeliveryFailureReason()).isEqualTo("Customer refused");
    }

    @Test
    void pickedUp_canGoToInTransit() {
        Order order = orderWithStatus(DeliveryStatus.PICKED_UP);
        stateMachine.transition(order, DeliveryStatus.IN_TRANSIT, null, false);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    void pickedUp_canGoToFailed() {
        Order order = orderWithStatus(DeliveryStatus.PICKED_UP);
        stateMachine.transition(order, DeliveryStatus.FAILED, "Vehicle breakdown", false);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void inTransit_canGoToDelivered() {
        Order order = orderWithStatus(DeliveryStatus.IN_TRANSIT);
        var result = stateMachine.transition(order, DeliveryStatus.DELIVERED, null, false);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isNotNull();
        assertThat(result.wasDelivered()).isTrue();
    }

    @Test
    void inTransit_canGoToFailed() {
        Order order = orderWithStatus(DeliveryStatus.IN_TRANSIT);
        stateMachine.transition(order, DeliveryStatus.FAILED, "Address not found", false);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    // ---------- Strict flow: backward and skip transitions rejected ----------

    @Test
    void assigned_cannotGoBackToPending() {
        Order order = orderWithStatus(DeliveryStatus.ASSIGNED);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.PENDING, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition")
                .hasMessageContaining("ASSIGNED");
    }

    @Test
    void pickedUp_cannotGoBackToAssigned() {
        Order order = orderWithStatus(DeliveryStatus.PICKED_UP);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.ASSIGNED, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition")
                .hasMessageContaining("PICKED_UP → ASSIGNED");
    }

    @Test
    void inTransit_cannotGoBackToPickedUp() {
        Order order = orderWithStatus(DeliveryStatus.IN_TRANSIT);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.PICKED_UP, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition");
    }

    @Test
    void assigned_cannotSkipToDelivered() {
        Order order = orderWithStatus(DeliveryStatus.ASSIGNED);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.DELIVERED, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition");
    }

    @Test
    void pickedUp_cannotSkipToDelivered() {
        Order order = orderWithStatus(DeliveryStatus.PICKED_UP);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.DELIVERED, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition");
    }

    // ---------- Failed transitions ----------

    @Test
    void failed_canRetryToPending() {
        Order order = orderWithStatus(DeliveryStatus.FAILED);
        order.setDeliveryAssignedTo(null);
        stateMachine.transition(order, DeliveryStatus.PENDING, null, true);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(order.getDeliveryFailureReason()).isNull();
    }

    @Test
    void failed_canBePermanentlyFailed_byAdmin() {
        Order order = orderWithStatus(DeliveryStatus.FAILED);
        var result = stateMachine.transition(order, DeliveryStatus.FAILED_PERMANENT, "Unreachable after 3 tries", true);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED_PERMANENT);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getDeliveryFailureReason()).isEqualTo("Unreachable after 3 tries");
        assertThat(result.wasFailedPermanent()).isTrue();
    }

    @Test
    void failed_permanentRejectedForNonAdmin() {
        Order order = orderWithStatus(DeliveryStatus.FAILED);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.FAILED_PERMANENT, "x", false))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Only admins");
    }

    @Test
    void failed_cannotSkipToDelivered() {
        Order order = orderWithStatus(DeliveryStatus.FAILED);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.DELIVERED, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition");
    }

    @Test
    void failed_cannotSkipToAssigned() {
        Order order = orderWithStatus(DeliveryStatus.FAILED);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.ASSIGNED, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition");
    }

    // ---------- Terminal states ----------

    @Test
    void delivered_isTerminal() {
        Order order = orderWithStatus(DeliveryStatus.DELIVERED);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.IN_TRANSIT, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition")
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void failedPermanent_isTerminal() {
        Order order = orderWithStatus(DeliveryStatus.FAILED_PERMANENT);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.PENDING, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid delivery status transition");
    }

    // ---------- Same status (idempotency) ----------

    @Test
    void sameStatus_isNoOp() {
        Order order = orderWithStatus(DeliveryStatus.PICKED_UP);
        var result = stateMachine.transition(order, DeliveryStatus.PICKED_UP, null, false);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(result.wasDelivered()).isFalse();
        assertThat(result.wasFailedPermanent()).isFalse();
    }

    // ---------- Null / uninitialized ----------

    @Test
    void nullStatus_throws() {
        Order order = orderWithStatus(null);
        assertThatThrownBy(() -> stateMachine.transition(order, DeliveryStatus.PICKED_UP, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not been initialized");
    }
}
