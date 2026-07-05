package com.ebee.supplier.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        @NotBlank(message = "Name is required")
        String name,
        String contactPerson,
        String email,
        String phone,
        String address
) {}
