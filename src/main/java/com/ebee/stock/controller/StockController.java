package com.ebee.stock.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.stock.dto.StockRequest;
import com.ebee.stock.dto.StockResponse;
import com.ebee.stock.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockResponse>> recordStockMovement(@Valid @RequestBody StockRequest request) {
        StockResponse response = stockService.recordStockMovement(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock movement recorded successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockResponse>> getStockById(@PathVariable UUID id) {
        StockResponse response = stockService.getStockById(id);
        return ResponseEntity.ok(ApiResponse.success("Stock record retrieved successfully", response));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<StockResponse>>> getStockHistoryByProductId(@PathVariable UUID productId) {
        List<StockResponse> response = stockService.getStockHistoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("Stock history retrieved successfully", response));
    }
}
