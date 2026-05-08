package com.ebee.inventory.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.inventory.dto.InventoryResponse;
import com.ebee.inventory.service.InventoryService;
import com.ebee.stock.dto.StockRequest;
import com.ebee.stock.dto.StockResponse;
import com.ebee.stock.enums.StockMovementType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventoryById(@PathVariable UUID id) {
        InventoryResponse response = inventoryService.getInventoryById(id);
        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved successfully", response));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventoryByProductId(@PathVariable UUID productId) {
        InventoryResponse response = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getAllInventories() {
        List<InventoryResponse> response = inventoryService.getAllInventories();
        return ResponseEntity.ok(ApiResponse.success("Inventories retrieved successfully", response));
    }

    @GetMapping("/low-stock")
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
    public ResponseEntity<ApiResponse<List<StockResponse>>> getStockHistoryByProductId(@PathVariable UUID productId) {
        List<StockResponse> response = inventoryService.getStockHistoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("Stock history retrieved successfully", response));
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
}