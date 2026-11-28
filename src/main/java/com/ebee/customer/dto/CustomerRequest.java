package com.ebee.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CustomerRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    private String lastName;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^0\\d{9}$", message = "Phone must be 10 digits starting with 0")
    private String phoneNumber;

    /** Optional; null leaves it unchanged on update, "" removes it. */
    @Pattern(regexp = "^$|^0\\d{9}$", message = "Phone 2 must be 10 digits starting with 0")
    private String phoneNumber2;

    @Email(message = "Invalid email format")
    private String email;

    private Boolean clearEmail;

    private String bookNo;

    private String vatNumber;

    @Valid
    @jakarta.validation.constraints.NotNull(message = "Address is required")
    private AddressRequest address;
}
