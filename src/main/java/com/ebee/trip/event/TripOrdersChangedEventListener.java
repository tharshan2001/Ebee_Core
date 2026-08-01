package com.ebee.trip.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripOrdersChangedEventListener {

    private static final String TOPIC_PREFIX = "/topic/trip-orders/";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripOrdersChanged(TripOrdersChangedEvent event) {
        if (event.getDriverId() == null) {
            log.debug("Skipping TripOrdersChangedEvent for trip {} - no driver assigned", event.getTripId());
            return;
        }

        TripOrdersChangedMessage message = TripOrdersChangedMessage.builder()
                .tripId(event.getTripId())
                .driverId(event.getDriverId())
                .orderMarkers(event.getOrderMarkers())
                .build();

        String destination = TOPIC_PREFIX + event.getDriverId();
        try {
            messagingTemplate.convertAndSend(destination, message);
            log.info("Broadcast trip orders for driver {} on trip {} - {} orders",
                    event.getDriverId(), event.getTripId(),
                    event.getOrderMarkers() != null ? event.getOrderMarkers().size() : 0);
        } catch (Exception e) {
            log.error("Failed to broadcast trip orders for driver {}: {}",
                    event.getDriverId(), e.getMessage(), e);
        }
    }
}
