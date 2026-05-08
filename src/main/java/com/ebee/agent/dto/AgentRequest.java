package com.ebee.agent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class AgentRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String phoneNumber;

    @NotBlank(message = "Agent code is required")
    private String agentCode;

    @NotNull(message = "Commission rate is required")
    private Double commissionRate;

    private String nicNumber;

    private MultipartFile nicDocument;

    private MultipartFile bankPassbookDocument;

    private String bankAccountNumber;

    private String bankName;

    private String bankBranch;
}
