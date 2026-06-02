package com.ebee.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class AgentUpdateRequest {

    @NotBlank(message = "Agent code is required")
    private String agentCode;

    private String phoneNumber;

    @NotNull(message = "Commission rate is required")
    private Double commissionRate;

    private String address;

    @NotBlank(message = "District is required")
    private String district;

    private String nicNumber;

    private MultipartFile nicDocument;

    private MultipartFile bankPassbookDocument;

    private String bankAccountNumber;

    private String bankName;

    private String bankBranch;
}
