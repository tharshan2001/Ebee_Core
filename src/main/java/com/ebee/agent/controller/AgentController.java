package com.ebee.agent.controller;

import com.ebee.agent.dto.AgentRequest;
import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.dto.AgentUpdateRequest;
import com.ebee.common.util.PaginationUtils;
import com.ebee.common.util.SortableFields;
import com.ebee.agent.dto.AgentWithCredentialsResponse;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.agent.service.AgentService;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ebee.common.util.SortUtils;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentWithCredentialsResponse>> createAgent(
            @RequestParam("email") String email,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("address") String address,
            @RequestParam("district") String district,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "nicNumber", required = false) String nicNumber,
            @RequestParam(value = "nicDocument", required = false) MultipartFile nicDocument,
            @RequestParam(value = "bankPassbookDocument", required = false) MultipartFile bankPassbookDocument,
            @RequestParam(value = "bankAccountNumber", required = false) String bankAccountNumber,
            @RequestParam(value = "bankName", required = false) String bankName,
            @RequestParam(value = "bankBranch", required = false) String bankBranch) {

        AgentRequest request = new AgentRequest();
        request.setEmail(email);
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setAddress(address);
        request.setDistrict(district);
        request.setPhoneNumber(phoneNumber);
        request.setNicNumber(nicNumber);
        request.setNicDocument(nicDocument);
        request.setBankPassbookDocument(bankPassbookDocument);
        request.setBankAccountNumber(bankAccountNumber);
        request.setBankName(bankName);
        request.setBankBranch(bankBranch);

        AgentWithCredentialsResponse response = agentService.createAgent(request, true);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Agent created successfully", response));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> activateAgent(@PathVariable UUID id) {
        AgentResponse response = agentService.activateAgent(id);
        return ResponseEntity.ok(ApiResponse.success("Agent activated successfully", response));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> deactivateAgent(@PathVariable UUID id) {
        AgentResponse response = agentService.deactivateAgent(id);
        return ResponseEntity.ok(ApiResponse.success("Agent deactivated successfully", response));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> approveAgent(@PathVariable UUID id) {
        AgentResponse response = agentService.approveAgent(id);
        return ResponseEntity.ok(ApiResponse.success("Agent approved successfully", response));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> rejectAgent(@PathVariable UUID id) {
        AgentResponse response = agentService.rejectAgent(id);
        return ResponseEntity.ok(ApiResponse.success("Agent rejected successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgentById(@PathVariable UUID id) {
        AgentResponse response = agentService.getAgentById(id);
        return ResponseEntity.ok(ApiResponse.success("Agent retrieved successfully", response));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgentByUserId(@PathVariable UUID userId) {
        AgentResponse response = agentService.getAgentByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Agent retrieved successfully", response));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AgentResponse>>> searchAgents(@RequestParam("q") String query) {
        List<AgentResponse> response = agentService.searchAgents(query.trim());
        return ResponseEntity.ok(ApiResponse.success("Agents retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AgentResponse>>> getAllAgents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(
                PaginationUtils.safePage(page),
                PaginationUtils.safeSize(size),
                SortUtils.parse(sort, SortableFields.AllowedFor.AGENT));

        Page<AgentResponse> agentsPage = search != null && !search.trim().isEmpty()
                ? agentService.searchAgentsPaginated(search.trim(), pageable)
                : agentService.getAllAgentsPaginated(pageable);

        PageResponse<AgentResponse> response = PageResponse.from(agentsPage);

        return ResponseEntity.ok(ApiResponse.success("Agents retrieved successfully", response));
    }

    @GetMapping("/code/{agentCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgentByAgentCode(@PathVariable String agentCode) {
        AgentResponse response = agentService.getAgentByAgentCode(agentCode);
        return ResponseEntity.ok(ApiResponse.success("Agent retrieved successfully", response));
    }

    @PatchMapping(value = "/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> updateAgent(
            @PathVariable UUID id,
            @RequestParam("agentCode") String agentCode,
            @RequestParam(value = "commissionRate", required = false) Double commissionRate,
            @RequestParam(value = "commissionType", required = false) AgentCommissionType commissionType,
            @RequestParam("address") String address,
            @RequestParam("district") String district,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "nicNumber", required = false) String nicNumber,
            @RequestParam(value = "nicDocument", required = false) MultipartFile nicDocument,
            @RequestParam(value = "bankPassbookDocument", required = false) MultipartFile bankPassbookDocument,
            @RequestParam(value = "bankAccountNumber", required = false) String bankAccountNumber,
            @RequestParam(value = "bankName", required = false) String bankName,
            @RequestParam(value = "bankBranch", required = false) String bankBranch) {

        AgentUpdateRequest request = new AgentUpdateRequest();
        request.setAgentCode(agentCode);
        request.setCommissionRate(commissionRate);
        request.setCommissionType(commissionType);
        request.setAddress(address);
        request.setDistrict(district);
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
    public ResponseEntity<Void> deleteAgent(@PathVariable UUID id) {
        agentService.deleteAgent(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/commission-rate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentResponse>> updateCommissionRate(
            @PathVariable UUID id,
            @RequestParam Double commissionRate) {
        AgentResponse response = agentService.updateCommissionRate(id, commissionRate);
        return ResponseEntity.ok(ApiResponse.success("Commission rate updated successfully", response));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable UUID id) {
        agentService.resetPassword(id);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. The new password has been sent to the agent's email."));
    }
}
