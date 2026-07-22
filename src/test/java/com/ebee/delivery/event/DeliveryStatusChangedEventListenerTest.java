package com.ebee.delivery.event;

import com.ebee.common.enums.District;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.delivery.dto.DeliveryStatusChangedMessage;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.order.entity.Order;
import com.ebee.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryStatusChangedEventListenerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    private DeliveryStatusChangedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DeliveryStatusChangedEventListener(messagingTemplate);
    }

    @Test
    void broadcastsToDeliveriesTopic() {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Doe")
                .address(Address.builder().id(UUID.randomUUID()).district(District.JF).build())
                .build();
        User driver = User.builder()
                .id(UUID.randomUUID())
                .firstName("Bob")
                .lastName("Smith")
                .build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("JF-A-20260101-001")
                .deliveryStatus(DeliveryStatus.IN_TRANSIT)
                .deliveryAssignedTo(driver)
                .customer(customer)
                .build();

        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        DeliveryStatusChangedEvent event = new DeliveryStatusChangedEvent(
                "source", order, DeliveryStatus.PICKED_UP, null, now);

        listener.onDeliveryStatusChanged(event);

        ArgumentCaptor<DeliveryStatusChangedMessage> captor =
                ArgumentCaptor.forClass(DeliveryStatusChangedMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/deliveries"),
                captor.capture());

        DeliveryStatusChangedMessage msg = captor.getValue();
        assertThat(msg.orderId()).isEqualTo(order.getId());
        assertThat(msg.orderNumber()).isEqualTo("JF-A-20260101-001");
        assertThat(msg.previousStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(msg.newStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(msg.driverId()).isEqualTo(driver.getId());
        assertThat(msg.driverName()).isEqualTo("Bob Smith");
        assertThat(msg.customerName()).isEqualTo("Jane Doe");
        assertThat(msg.district()).isEqualTo("Jaffna");
        assertThat(msg.occurredAt()).isEqualTo(now);
    }

    @Test
    void handlesNullDriverAndCustomer() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("JF-A-20260101-002")
                .deliveryStatus(DeliveryStatus.PENDING)
                .build();
        DeliveryStatusChangedEvent event = new DeliveryStatusChangedEvent(
                "source", order, null, null, LocalDateTime.now());

        listener.onDeliveryStatusChanged(event);

        ArgumentCaptor<DeliveryStatusChangedMessage> captor =
                ArgumentCaptor.forClass(DeliveryStatusChangedMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/deliveries"),
                captor.capture());

        DeliveryStatusChangedMessage msg = captor.getValue();
        assertThat(msg.driverId()).isNull();
        assertThat(msg.driverName()).isNull();
        assertThat(msg.customerName()).isNull();
        assertThat(msg.district()).isNull();
    }

    @Test
    void swallowsMessagingTemplateException() {
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(messagingTemplate).convertAndSend(
                        org.mockito.ArgumentMatchers.eq("/topic/deliveries"),
                        org.mockito.ArgumentMatchers.any(Object.class));

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("JF-A-20260101-003")
                .deliveryStatus(DeliveryStatus.DELIVERED)
                .build();
        DeliveryStatusChangedEvent event = new DeliveryStatusChangedEvent(
                "source", order, DeliveryStatus.IN_TRANSIT, null, LocalDateTime.now());

        listener.onDeliveryStatusChanged(event);
    }
}
