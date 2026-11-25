package com.ebee.inventory.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.common.util.PaginationUtils;
import com.ebee.inventory.dto.InventoryLayerResponse;
import com.ebee.inventory.dto.InventoryResponse;
import com.ebee.inventory.service.InventoryService;
import com.ebee.stock.dto.StockRequest;
import com.ebee.stock.dto.StockResponse;
import com.ebee.stock.enums.StockMovementType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final com.ebee.order.service.LotTraceService lotTraceService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventoryById(@PathVariable UUID id) {
        InventoryResponse response = inventoryService.getInventoryById(id);
        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved successfully", response));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventoryByProductId(@PathVariable UUID productId) {
        InventoryResponse response = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getAllInventories() {
        List<InventoryResponse> response = inventoryService.getAllInventories();
        return ResponseEntity.ok(ApiResponse.success("Inventories retrieved successfully", response));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getLowStockInventories() {
        List<InventoryResponse> response = inventoryService.getLowStockInventories();
        return ResponseEntity.ok(ApiResponse.success("Low stock inventories retrieved successfully", response));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjustStock(@Valid @RequestBody StockRequest request) {
        InventoryResponse response = inventoryService.adjustStock(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock adjusted successfully", response));
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InventoryResponse>> reserveStock(
            @RequestParam UUID productId,
            @RequestParam Integer quantity) {
        InventoryResponse response = inventoryService.reserveStock(productId, quantity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock reserved successfully", response));
    }

    @PostMapping("/release")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InventoryResponse>> releaseReservedStock(
            @RequestParam UUID productId,
            @RequestParam Integer quantity) {
        InventoryResponse response = inventoryService.releaseReservedStock(productId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Reserved stock released successfully", response));
    }

    @GetMapping("/product/{productId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<PageResponse<StockResponse>>> getStockHistoryByProductId(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(PaginationUtils.safePage(page), PaginationUtils.safeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockResponse> response = inventoryService.getStockHistoryByProductId(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Stock history retrieved successfully", PageResponse.from(response)));
    }

    @GetMapping("/product/{productId}/layers")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<PageResponse<InventoryLayerResponse>>> getLayersByProductId(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(PaginationUtils.safePage(page), PaginationUtils.safeSize(size),
                Sort.by(Sort.Direction.ASC, "receivedAt"));
        Page<InventoryLayerResponse> response = inventoryService.getLayersByProductId(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Inventory layers retrieved successfully", PageResponse.from(response)));
    }

    @PostMapping("/order-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockResponse>> recordOrderStock(
            @RequestParam UUID productId,
            @RequestParam Integer quantity,
            @RequestParam StockMovementType movementType,
            @RequestParam(required = false) String notes) {
        StockResponse response = inventoryService.recordOrderStock(productId, quantity, movementType, notes);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order stock recorded successfully", response));
    }

    @PostMapping("/confirm-order-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InventoryResponse>> confirmOrderStock(
            @RequestParam UUID productId,
            @RequestParam Integer quantity) {
        InventoryResponse response = inventoryService.confirmOrderStock(productId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Order stock confirmed successfully", response));
    }

    // ── lots & expiry ──────────────────────────────────────────────────

    /** Batches that have expired or expire within {@code days} days (default 30), optionally at one location. */
    @GetMapping("/expiring")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<com.ebee.inventory.dto.ExpiringStockResponse>>> expiring(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) java.util.UUID locationId) {
        return ResponseEntity.ok(ApiResponse.success("Expiring stock", inventoryService.expiringStock(days, locationId)));
    }

    /** Write off all expired stock (optionally at one location). */
    @PostMapping("/expired/write-off")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.ebee.inventory.dto.WriteOffResult>> writeOffExpired(
            @RequestParam(required = false) java.util.UUID locationId) {
        return ResponseEntity.ok(ApiResponse.success("Expired stock written off", inventoryService.writeOffExpired(locationId)));
    }

    /** Recall: every order that received this product's lot. */
    @GetMapping("/lots/{productId}/{lotNumber}/sales")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<com.ebee.order.service.LotTraceService.LotSale>>> lotSales(
            @PathVariable java.util.UUID productId, @PathVariable String lotNumber) {
        return ResponseEntity.ok(ApiResponse.success("Sales of lot", lotTraceService.salesOfLot(productId, lotNumber)));
    }
}
