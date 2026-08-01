package com.ebee.trip.event;

import com.ebee.trip.dto.TripOrderMarkerDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOrdersChangedMessage {

    private UUID tripId;
    private UUID driverId;
    private List<TripOrderMarkerDto> orderMarkers;
}
