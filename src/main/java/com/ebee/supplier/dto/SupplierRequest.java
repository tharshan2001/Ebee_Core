package com.ebee.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank(message = "Name is required")
        String name,
        String contactPerson,
        String email,
        String phone,
        @NotBlank(message = "Address line 1 is required")
        String addressLine1,
        String addressLine2,
        @NotBlank(message = "City is required")
        String city,
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        String postalCode,
        String district,
        String country
) {}
