package com.ebee.trip.event;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.order.entity.Order;
import com.ebee.order.repository.OrderRepository;
import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveTripsSnapshotListenerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private TripService tripService;
    @Mock private OrderRepository orderRepository;

    private ActiveTripsSnapshotListener listener;

    @BeforeEach
    void setUp() {
        listener = new ActiveTripsSnapshotListener(messagingTemplate, tripService, orderRepository);
    }

    private TripResponse activeTrip() {
        TripResponse trip = new TripResponse();
        trip.setId(UUID.randomUUID());
        trip.setName("Morning Route");
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setDeliveredOrderCount(1);
        return trip;
    }

    private Trip trip(TripStatus status) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .name("Morning Route")
                .status(status)
                .build();
    }

    private Order order() {
        return Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("JF-A-20260810-001")
                .deliveryStatus(DeliveryStatus.IN_TRANSIT)
                .build();
    }

    private void stubSnapshot(TripResponse... trips) {
        when(tripService.getAllTrips(TripStatus.IN_PROGRESS, null, null, null))
                .thenReturn(List.of(trips));
    }

    private ActiveTripsMessage capturedMessage() {
        ArgumentCaptor<ActiveTripsMessage> captor = ArgumentCaptor.forClass(ActiveTripsMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(ActiveTripsSnapshotListener.TOPIC),
                captor.capture());
        return captor.getValue();
    }

    @Test
    void broadcastsSnapshotOnTripStatusChanged() {
        TripResponse active = activeTrip();
        stubSnapshot(active);

        listener.onTripStatusChanged(
                new TripStatusChangedEvent("source", trip(TripStatus.IN_PROGRESS), TripStatus.PLANNED, 1));

        ActiveTripsMessage msg = capturedMessage();
        assertThat(msg.activeTrips()).containsExactly(active);
        assertThat(msg.activeTripsCount()).isEqualTo(1);
    }

    @Test
    void broadcastsSnapshotOnTripOrdersChanged() {
        TripResponse active = activeTrip();
        stubSnapshot(active);

        listener.onTripOrdersChanged(
                new TripOrdersChangedEvent("source", trip(TripStatus.IN_PROGRESS), List.of()));

        ActiveTripsMessage msg = capturedMessage();
        assertThat(msg.activeTrips()).containsExactly(active);
        assertThat(msg.activeTripsCount()).isEqualTo(1);
    }

    @Test
    void broadcastsSnapshotWhenDeliveryChangesOnActiveTrip() {
        TripResponse active = activeTrip();
        stubSnapshot(active);

        when(orderRepository.findTripStatusByOrderId(any())).thenReturn(TripStatus.IN_PROGRESS);

        listener.onDeliveryStatusChanged(new DeliveryStatusChangedEvent(
                "source", order(), DeliveryStatus.IN_TRANSIT, null, LocalDateTime.now()));

        ActiveTripsMessage msg = capturedMessage();
        assertThat(msg.activeTrips()).containsExactly(active);
        assertThat(msg.activeTripsCount()).isEqualTo(1);
    }

    @Test
    void skipsBroadcastWhenDeliveryChangesOnNonActiveTrip() {
        when(orderRepository.findTripStatusByOrderId(any())).thenReturn(TripStatus.COMPLETED);

        listener.onDeliveryStatusChanged(new DeliveryStatusChangedEvent(
                "source", order(), DeliveryStatus.IN_TRANSIT, null, LocalDateTime.now()));

        verifyNoInteractions(messagingTemplate, tripService);
    }

    @Test
    void skipsBroadcastWhenOrderHasNoTrip() {
        when(orderRepository.findTripStatusByOrderId(any())).thenReturn(null);

        listener.onDeliveryStatusChanged(new DeliveryStatusChangedEvent(
                "source", order(), DeliveryStatus.IN_TRANSIT, null, LocalDateTime.now()));

        verifyNoInteractions(messagingTemplate, tripService);
    }

    @Test
    void swallowsMessagingTemplateException() {
        stubSnapshot(activeTrip());
        doThrow(new RuntimeException("broker down"))
                .when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        listener.onTripStatusChanged(
                new TripStatusChangedEvent("source", trip(TripStatus.IN_PROGRESS), TripStatus.PLANNED, 1));

        verify(messagingTemplate).convertAndSend(any(String.class), any(Object.class));
    }
}
