package com.ebee.delivery.dto;

import com.ebee.delivery.enums.TripStatus;
import com.ebee.order.dto.OrderResponse;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TripResponse {
    private UUID id;
    private LocalDate tripDate;
    private String district;
    private TripStatus status;
    private UUID driverId;
    private String driverName;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderResponse> orders;
}
