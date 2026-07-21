package com.ebee.auth.dto;

import com.ebee.user.enums.RoleType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).+$",
             message = "Password must contain uppercase, lowercase, digit, and special character")
    private String password;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Pattern(regexp = "^0\\d{9}$", message = "Phone must be 10 digits starting with 0")
    private String phoneNumber;

    @NotNull(message = "Role is required")
    private RoleType role;

    @AssertTrue(message = "Cannot register as SUPER_ADMIN")
    public boolean isValidRole() {
        return role != RoleType.SUPER_ADMIN;
    }
}
