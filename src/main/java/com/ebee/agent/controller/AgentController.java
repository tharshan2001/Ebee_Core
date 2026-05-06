package com.ebee.agent.controller;

import com.ebee.agent.dto.AgentRequest;
import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.dto.AgentUpdateRequest;
import com.ebee.agent.service.AgentService;
import com.ebee.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> createAgent(
            @RequestParam("userId") String userId,
            @RequestParam("agentCode") String agentCode,
            @RequestParam("commissionRate") Double commissionRate,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "nicNumber", required = false) String nicNumber,
            @RequestParam(value = "nicDocument", required = false) MultipartFile nicDocument,
            @RequestParam(value = "bankPassbookDocument", required = false) MultipartFile bankPassbookDocument,
            @RequestParam(value = "bankAccountNumber", required = false) String bankAccountNumber,
            @RequestParam(value = "bankName", required = false) String bankName,
            @RequestParam(value = "bankBranch", required = false) String bankBranch) throws java.io.IOException {

        AgentRequest request = new AgentRequest();
        request.setUserId(java.util.UUID.fromString(userId));
        request.setAgentCode(agentCode);
        request.setCommissionRate(commissionRate);
        request.setPhoneNumber(phoneNumber);
        request.setNicNumber(nicNumber);
        request.setNicDocument(nicDocument);
        request.setBankPassbookDocument(bankPassbookDocument);
        request.setBankAccountNumber(bankAccountNumber);
        request.setBankName(bankName);
        request.setBankBranch(bankBranch);

        AgentResponse response = agentService.createAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Agent created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgentById(@PathVariable UUID id) {
        AgentResponse response = agentService.getAgentById(id);
        return ResponseEntity.ok(ApiResponse.success("Agent retrieved successfully", response));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgentByUserId(@PathVariable UUID userId) {
        AgentResponse response = agentService.getAgentByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Agent retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AgentResponse>>> getAllAgents() {
        List<AgentResponse> response = agentService.getAllAgents();
        return ResponseEntity.ok(ApiResponse.success("Agents retrieved successfully", response));
    }

    @GetMapping("/code/{agentCode}")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgentByAgentCode(@PathVariable String agentCode) {
        AgentResponse response = agentService.getAgentByAgentCode(agentCode);
        return ResponseEntity.ok(ApiResponse.success("Agent retrieved successfully", response));
    }

    @PatchMapping(value = "/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> updateAgent(
            @PathVariable UUID id,
            @RequestParam("agentCode") String agentCode,
            @RequestParam("commissionRate") Double commissionRate,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "nicNumber", required = false) String nicNumber,
            @RequestParam(value = "nicDocument", required = false) MultipartFile nicDocument,
            @RequestParam(value = "bankPassbookDocument", required = false) MultipartFile bankPassbookDocument,
            @RequestParam(value = "bankAccountNumber", required = false) String bankAccountNumber,
            @RequestParam(value = "bankName", required = false) String bankName,
            @RequestParam(value = "bankBranch", required = false) String bankBranch) throws java.io.IOException {

        AgentUpdateRequest request = new AgentUpdateRequest();
        request.setAgentCode(agentCode);
        request.setCommissionRate(commissionRate);
        request.setPhoneNumber(phoneNumber);
        request.setNicNumber(nicNumber);
        request.setNicDocument(nicDocument);
        request.setBankPassbookDocument(bankPassbookDocument);
        request.setBankAccountNumber(bankAccountNumber);
        request.setBankName(bankName);
        request.setBankBranch(bankBranch);

        AgentResponse response = agentService.updateAgent(id, request);
        return ResponseEntity.ok(ApiResponse.success("Agent updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAgent(@PathVariable UUID id) {
        agentService.deleteAgent(id);
        return ResponseEntity.ok(ApiResponse.success("Agent deleted successfully"));
    }

    @PatchMapping("/{id}/commission-rate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> updateCommissionRate(
            @PathVariable UUID id,
            @RequestParam Double commissionRate) {
        AgentResponse response = agentService.updateCommissionRate(id, commissionRate);
        return ResponseEntity.ok(ApiResponse.success("Commission rate updated successfully", response));
    }
}
