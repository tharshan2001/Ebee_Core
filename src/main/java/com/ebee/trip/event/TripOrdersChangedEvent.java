package com.ebee.trip.event;

import com.ebee.trip.dto.TripOrderMarkerDto;
import com.ebee.trip.entity.Trip;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.UUID;

@Getter
public class TripOrdersChangedEvent extends ApplicationEvent {

    private final UUID tripId;
    private final UUID driverId;
    private final List<TripOrderMarkerDto> orderMarkers;

    public TripOrdersChangedEvent(Object source, Trip trip, List<TripOrderMarkerDto> orderMarkers) {
        super(source);
        this.tripId = trip.getId();
        this.driverId = trip.getDriver() != null ? trip.getDriver().getId() : null;
        this.orderMarkers = orderMarkers;
    }
}
