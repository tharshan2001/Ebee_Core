package com.ebee.trip.event;

import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.order.repository.OrderRepository;
import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Broadcasts the full list of IN_PROGRESS trips to {@code /topic/active-trips}
 * whenever trip-related state commits. Subscribers render the latest snapshot
 * wholesale (replace-style), so no delta logic is needed on the client.
 *
 * <p>Rebuilds are triggered by three events:
 * <ul>
 *   <li>{@link TripStatusChangedEvent} — a trip started, completed or cancelled
 *       changes membership of the snapshot;</li>
 *   <li>{@link TripOrdersChangedEvent} — orders added to or removed from a trip
 *       change its totals;</li>
 *   <li>{@link DeliveryStatusChangedEvent} — a delivery progressing on an
 *       IN_PROGRESS trip updates its delivered/failed counts. These are the
 *       highest volume, so the rebuild is skipped unless the order actually
 *       belongs to an IN_PROGRESS trip.</li>
 * </ul>
 *
 * <p>Delivery events are filtered via {@link OrderRepository#findTripStatusByOrderId}
 * rather than traversing the order's lazy {@code trip} reference, because the
 * listener runs after commit with no open persistence context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveTripsSnapshotListener {

    public static final String TOPIC = "/topic/active-trips";

    private final SimpMessagingTemplate messagingTemplate;
    private final TripService tripService;
    private final OrderRepository orderRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripStatusChanged(TripStatusChangedEvent event) {
        broadcast();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripOrdersChanged(TripOrdersChangedEvent event) {
        broadcast();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryStatusChanged(DeliveryStatusChangedEvent event) {
        TripStatus tripStatus = orderRepository.findTripStatusByOrderId(event.getOrderId());
        if (tripStatus != TripStatus.IN_PROGRESS) {
            return;
        }
        broadcast();
    }

    private void broadcast() {
        try {
            List<TripResponse> activeTrips =
                    tripService.getAllTrips(TripStatus.IN_PROGRESS, null, null, null);
            messagingTemplate.convertAndSend(
                    TOPIC, new ActiveTripsMessage(activeTrips, activeTrips.size()));
            log.debug("Broadcast active trips snapshot ({} trips)", activeTrips.size());
        } catch (Exception e) {
            log.error("Failed to broadcast active trips snapshot: {}", e.getMessage(), e);
        }
    }
}
