package com.ebee.driver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DriverRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private String phoneNumber;

    private String licenseNumber;

    private String contactNumber;
}
