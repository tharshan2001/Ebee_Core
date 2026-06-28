package com.ebee.driver.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DriverProfileRequest {
    private String firstName;
    private String lastName;

    @Pattern(regexp = "^0\\d{9}$", message = "Phone number must be 10 digits starting with 0")
    private String phoneNumber;

    private String licenseNumber;
}
