package com.ebee.lead.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.lead.dto.CreateLeadRequest;
import com.ebee.lead.dto.LeadResponse;
import com.ebee.lead.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @PostMapping
    public ResponseEntity<ApiResponse<LeadResponse>> createLead(
            @Valid @RequestBody CreateLeadRequest request) {
        LeadResponse response = leadService.createLead(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lead created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeadResponse>>> getAllLeads() {
        List<LeadResponse> response = leadService.getAllLeads();
        return ResponseEntity.ok(ApiResponse.success("Leads retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LeadResponse>> getLeadById(@PathVariable UUID id) {
        LeadResponse response = leadService.getLeadById(id);
        return ResponseEntity.ok(ApiResponse.success("Lead retrieved successfully", response));
    }

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<LeadResponse>>> getLeadsByAgentId(
            @PathVariable UUID agentId) {
        List<LeadResponse> response = leadService.getLeadsByAgentId(agentId);
        return ResponseEntity.ok(ApiResponse.success("Leads retrieved successfully", response));
    }

    @PutMapping("/{leadId}/convert")
    public ResponseEntity<ApiResponse<LeadResponse>> convertLead(
            @PathVariable UUID leadId,
            @RequestParam UUID orderId) {
        LeadResponse response = leadService.convertLead(leadId, orderId);
        return ResponseEntity.ok(ApiResponse.success("Lead converted successfully", response));
    }
}
