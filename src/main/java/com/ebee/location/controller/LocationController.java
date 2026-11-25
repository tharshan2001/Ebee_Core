package com.ebee.location.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.inventory.dto.InventoryResponse;
import com.ebee.inventory.service.InventoryService;
import com.ebee.location.dto.LocationRequest;
import com.ebee.location.dto.LocationResponse;
import com.ebee.location.dto.StockTransferRequest;
import com.ebee.location.dto.StockTransferResponse;
import com.ebee.location.service.LocationService;
import com.ebee.location.service.StockTransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;
    private final StockTransferService transferService;
    private final InventoryService inventoryService;

    public LocationController(LocationService locationService, StockTransferService transferService,
                              InventoryService inventoryService) {
        this.locationService = locationService;
        this.transferService = transferService;
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> list() {
        return ok("Locations", locationService.list().stream().map(LocationResponse::of).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LocationResponse>> create(@Valid @RequestBody LocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Location created",
                LocationResponse.of(locationService.create(request))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LocationResponse>> update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return ok("Location updated", LocationResponse.of(locationService.update(id, request)));
    }

    /** The location an agent's orders ship from by default (omit locationId to clear it). */
    @PutMapping("/agents/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setAgentHome(@PathVariable UUID agentId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID locationId) {
        locationService.setAgentHomeLocation(agentId, locationId);
        return ResponseEntity.ok(ApiResponse.success("Agent home location updated"));
    }

    /** Stock of one product at every location. */
    @GetMapping("/stock/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> stockByLocation(@PathVariable UUID productId) {
        return ok("Stock by location", inventoryService.getStockByLocation(productId));
    }

    // ── transfers ──────────────────────────────────────────────────────

    @GetMapping("/transfers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> transfers() {
        return ok("Stock transfers", transferService.list().stream().map(StockTransferResponse::of).toList());
    }

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> transfer(@PathVariable UUID id) {
        return ok("Stock transfer", StockTransferResponse.of(transferService.get(id)));
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> createTransfer(@Valid @RequestBody StockTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Stock transfer created",
                StockTransferResponse.of(transferService.create(request))));
    }

    @PostMapping("/transfers/{id}/dispatch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> dispatch(@PathVariable UUID id) {
        return ok("Stock dispatched", StockTransferResponse.of(transferService.dispatch(id)));
    }

    @PostMapping("/transfers/{id}/receive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> receive(@PathVariable UUID id) {
        return ok("Stock received", StockTransferResponse.of(transferService.receive(id)));
    }

    @PostMapping("/transfers/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> cancel(@PathVariable UUID id) {
        return ok("Stock transfer cancelled", StockTransferResponse.of(transferService.cancel(id)));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }
}
