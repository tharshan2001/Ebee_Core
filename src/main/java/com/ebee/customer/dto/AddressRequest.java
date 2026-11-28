package com.ebee.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {

    @Size(max = 255, message = "Street must not exceed 255 characters")
    private String street;

    @Size(max = 255, message = "Address line 2 too long")
    private String addressLine2;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotBlank(message = "District is required")
    private String district;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;

    private String country;
}
