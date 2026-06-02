package com.ebee.agent.application.controller;

import com.ebee.agent.application.dto.AgentApplicationRequest;
import com.ebee.agent.application.dto.AgentApplicationResponse;
import com.ebee.agent.application.service.AgentApplicationService;
import com.ebee.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent-applications")
@RequiredArgsConstructor
public class AgentApplicationController {

    private final AgentApplicationService service;

    @PostMapping
    public ResponseEntity<ApiResponse<AgentApplicationResponse>> create(@Valid @RequestBody AgentApplicationRequest request) {
        AgentApplicationResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Application submitted successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AgentApplicationResponse>>> getAll() {
        List<AgentApplicationResponse> applications = service.getAll();
        return ResponseEntity.ok(ApiResponse.success("Applications retrieved successfully", applications));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgentApplicationResponse>> getById(@PathVariable UUID id) {
        AgentApplicationResponse response = service.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Application retrieved successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Application deleted successfully"));
    }
}
