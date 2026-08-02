package com.ebee.delivery.dto;

import com.ebee.delivery.enums.DeliveryStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DeliveryResponse {
    private UUID orderId;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String district;
    private DeliveryStatus status;
    private UUID driverId;
    private String driverName;
    private UUID tripId;
    private String tripName;
    private LocalDate scheduledDate;
    private String notes;
    private String driverNotes;
    private LocalDateTime deliveredAt;
    private String deliveryFailureReason;
    private String deliveryPhotoUrl;
    private String deliverySignatureUrl;
}
