package com.ebee.trip.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripStatusChangedEventListener {

    public static final String TOPIC = "/topic/trips";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripStatusChanged(TripStatusChangedEvent event) {
        TripStatusChangedMessage message = TripStatusChangedMessage.builder()
                .tripId(event.getTripId())
                .tripName(event.getTripName())
                .previousStatus(event.getPreviousStatus())
                .newStatus(event.getNewStatus())
                .driverId(event.getDriverId())
                .driverName(event.getDriverName())
                .orderCount(event.getOrderCount())
                .occurredAt(event.getOccurredAt())
                .build();

        try {
            messagingTemplate.convertAndSend(TOPIC, message);
            log.info("Broadcast trip {} {} → {}",
                    event.getTripName(), event.getPreviousStatus(), event.getNewStatus());
        } catch (Exception e) {
            log.error("Failed to broadcast trip status change for {}: {}",
                    event.getTripName(), e.getMessage(), e);
        }
    }
}
