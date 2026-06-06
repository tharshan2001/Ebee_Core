package com.ebee.agent.dto;

import com.ebee.agent.enums.AgentCommissionType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class AgentUpdateRequest {

    @NotBlank(message = "Agent code is required")
    private String agentCode;

    private String phoneNumber;

    private Double commissionRate;

    private AgentCommissionType commissionType;

    private String address;

    private String district;

    private String nicNumber;

    private MultipartFile nicDocument;

    private MultipartFile bankPassbookDocument;

    private String bankAccountNumber;

    private String bankName;

    private String bankBranch;
}
