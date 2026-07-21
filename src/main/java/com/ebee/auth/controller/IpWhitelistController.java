package com.ebee.auth.controller;

import com.ebee.auth.dto.IpWhitelistRequest;
import com.ebee.auth.dto.IpWhitelistResponse;
import com.ebee.auth.dto.WhitelistStatusResponse;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.security.SecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ip-whitelist")
@RequiredArgsConstructor
public class IpWhitelistController {

    private final IpWhitelistService ipWhitelistService;
    private final SecurityService securityService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<IpWhitelistResponse>>> getAllEntries() {
        if (securityService.isSuperAdmin()) {
            return ResponseEntity.ok(ApiResponse.success("IP whitelist entries retrieved", ipWhitelistService.getAllEntries()));
        }
        UUID currentUserId = securityService.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("IP whitelist entries retrieved", ipWhitelistService.getEntriesForUser(currentUserId)));
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WhitelistStatusResponse>> getStatus() {
        return ResponseEntity.ok(ApiResponse.success("Whitelist status retrieved",
                new WhitelistStatusResponse(ipWhitelistService.isGloballyEnabled())));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<IpWhitelistResponse>> addEntry(@Valid @RequestBody IpWhitelistRequest request) {
        IpWhitelistResponse response = ipWhitelistService.addEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("IP whitelist entry added", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeEntry(@PathVariable UUID id) {
        ipWhitelistService.removeEntry(id);
        return ResponseEntity.ok(ApiResponse.success("IP whitelist entry removed"));
    }

    @PutMapping("/enable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WhitelistStatusResponse>> enable() {
        return ResponseEntity.ok(ApiResponse.success("IP whitelist enabled",
                ipWhitelistService.setWhitelistEnabled(true)));
    }

    @PutMapping("/disable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WhitelistStatusResponse>> disable() {
        return ResponseEntity.ok(ApiResponse.success("IP whitelist disabled",
                ipWhitelistService.setWhitelistEnabled(false)));
    }
}
