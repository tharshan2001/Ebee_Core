package com.ebee.trip.event;

import com.ebee.trip.enums.TripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStatusChangedMessage {

    private UUID tripId;
    private String tripName;
    private TripStatus previousStatus;
    private TripStatus newStatus;
    private UUID driverId;
    private String driverName;
    private int orderCount;
    private LocalDateTime occurredAt;
}
