package com.ebee.trip.event;

import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class TripStatusChangedEvent extends ApplicationEvent {

    private final UUID tripId;
    private final String tripName;
    private final TripStatus previousStatus;
    private final TripStatus newStatus;
    private final UUID driverId;
    private final String driverName;
    private final int orderCount;
    private final LocalDateTime occurredAt;

    public TripStatusChangedEvent(Object source, Trip trip, TripStatus previousStatus) {
        super(source);
        this.tripId = trip.getId();
        this.tripName = trip.getName();
        this.previousStatus = previousStatus;
        this.newStatus = trip.getStatus();
        this.driverId = trip.getDriver() != null ? trip.getDriver().getId() : null;
        this.driverName = trip.getDriver() != null
                ? trip.getDriver().getUser().getFirstName() + " " + trip.getDriver().getUser().getLastName()
                : null;
        this.orderCount = trip.getOrders() != null ? trip.getOrders().size() : 0;
        this.occurredAt = LocalDateTime.now();
    }
}
