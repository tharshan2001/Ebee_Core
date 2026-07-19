package com.ebee.supplier.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String contactPerson,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String district,
        String country,
        boolean isActive,
        BigDecimal outstandingBalance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
