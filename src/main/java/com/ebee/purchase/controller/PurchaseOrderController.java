package com.ebee.purchase.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.common.security.SecurityService;
import com.ebee.purchase.dto.PurchaseOrderRequest;
import com.ebee.purchase.dto.PurchaseOrderResponse;
import com.ebee.purchase.dto.PurchaseOrderStatusRequest;
import com.ebee.purchase.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchase-orders")
@PreAuthorize("hasRole('ADMIN')")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final SecurityService securityService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService,
                                   SecurityService securityService) {
        this.purchaseOrderService = purchaseOrderService;
        this.securityService = securityService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createOrder(@Valid @RequestBody PurchaseOrderRequest request) {
        UUID userId = securityService.getCurrentUserId();
        PurchaseOrderResponse response = purchaseOrderService.createOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase order created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getOrder(@PathVariable UUID id) {
        PurchaseOrderResponse response = purchaseOrderService.getOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Purchase order retrieved successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrderResponse>>> listOrders(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PurchaseOrderResponse> orders = purchaseOrderService.listOrders(search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Purchase orders retrieved successfully", PageResponse.from(orders)));
    }

    @GetMapping("/by-supplier/{supplierId}")
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> findBySupplier(@PathVariable UUID supplierId) {
        List<PurchaseOrderResponse> responses = purchaseOrderService.findBySupplier(supplierId);
        return ResponseEntity.ok(ApiResponse.success("Purchase orders retrieved successfully", responses));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderStatusRequest request) {
        PurchaseOrderResponse response = purchaseOrderService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Purchase order status updated successfully", response));
    }

    @PostMapping(value = "/{orderId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> addAttachment(
            @PathVariable UUID orderId,
            @RequestParam("file") MultipartFile file) {
        PurchaseOrderResponse response = purchaseOrderService.addAttachment(orderId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attachment added successfully", response));
    }

    @DeleteMapping("/{orderId}/attachments/{attachmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @PathVariable UUID orderId,
            @PathVariable UUID attachmentId) {
        purchaseOrderService.deleteAttachment(orderId, attachmentId);
        return ResponseEntity.ok(ApiResponse.success("Attachment deleted successfully"));
    }
}
