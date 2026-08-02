package com.ebee.supplier.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.purchase.enums.PurchasePaymentMethod;
import com.ebee.supplier.dto.*;
import com.ebee.supplier.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasRole('ADMIN')")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse response = supplierService.createSupplier(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Supplier created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> getSupplier(@PathVariable UUID id) {
        SupplierResponse response = supplierService.getSupplierById(id);
        return ResponseEntity.ok(ApiResponse.success("Supplier retrieved successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SupplierResponse>>> getAllSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        String[] sortParams = sort.split(",");
        Sort sortBy = Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]);
        Pageable pageable = PageRequest.of(page, size, sortBy);
        Page<SupplierResponse> suppliers = supplierService.getAllSuppliers(search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Suppliers retrieved successfully", PageResponse.from(suppliers)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SupplierResponse>>> searchSuppliers(@RequestParam String q) {
        List<SupplierResponse> responses = supplierService.searchSuppliers(q.trim());
        return ResponseEntity.ok(ApiResponse.success("Suppliers retrieved successfully", responses));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
            @PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        SupplierResponse response = supplierService.updateSupplier(id, request);
        return ResponseEntity.ok(ApiResponse.success("Supplier updated successfully", response));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateSupplier(@PathVariable UUID id) {
        supplierService.deactivateSupplier(id);
        return ResponseEntity.ok(ApiResponse.success("Supplier deactivated successfully"));
    }

    @GetMapping("/with-outstandings")
    public ResponseEntity<ApiResponse<List<SupplierResponse>>> getSuppliersWithDues() {
        List<SupplierResponse> responses = supplierService.getSuppliersWithDues();
        return ResponseEntity.ok(ApiResponse.success("Suppliers with outstanding retrieved successfully", responses));
    }

    @GetMapping("/{id}/outstanding-orders")
    public ResponseEntity<ApiResponse<SupplierOutstandingOrdersResponse>> getSupplierOutstandingOrders(@PathVariable UUID id) {
        SupplierOutstandingOrdersResponse response = supplierService.getSupplierOutstandingOrders(id);
        return ResponseEntity.ok(ApiResponse.success("Supplier outstanding orders retrieved successfully", response));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<SupplierPaymentResponse>> recordSupplierPayment(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount,
            @RequestParam PurchasePaymentMethod paymentMethod,
            @RequestParam(required = false) String description) {
        SupplierPaymentResponse response = supplierService.recordSupplierPayment(id, amount, paymentMethod, description);
        return ResponseEntity.ok(ApiResponse.success("Supplier payment recorded successfully", response));
    }

    @PostMapping("/{id}/adjust-outstanding")
    public ResponseEntity<ApiResponse<SupplierAdjustmentResponse>> adjustOutstanding(
            @PathVariable UUID id,
            @Valid @RequestBody AdjustOutstandingRequest request) {
        SupplierAdjustmentResponse response = supplierService.manualAdjustOutstanding(id, request);
        return ResponseEntity.ok(ApiResponse.success("Outstanding adjustment recorded successfully", response));
    }
}
