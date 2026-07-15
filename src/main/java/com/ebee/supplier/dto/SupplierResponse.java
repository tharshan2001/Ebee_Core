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
        String address,
        boolean isActive,
        BigDecimal outstandingBalance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
