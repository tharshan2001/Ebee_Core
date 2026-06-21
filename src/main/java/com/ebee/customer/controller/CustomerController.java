package com.ebee.customer.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.common.util.PaginationUtils;
import com.ebee.common.util.SortableFields;
import com.ebee.customer.dto.CustomerRequest;
import com.ebee.customer.dto.CustomerResponse;
import com.ebee.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ebee.common.util.SortUtils;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(@PathVariable UUID id) {
        CustomerResponse response = customerService.getCustomerById(id);
        return ResponseEntity.ok(ApiResponse.success("Customer retrieved successfully", response));
    }

    @GetMapping("/phone/{phoneNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerByPhone(@PathVariable String phoneNumber) {
        CustomerResponse response = customerService.getCustomerByPhone(phoneNumber);
        return ResponseEntity.ok(ApiResponse.success("Customer retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest request) {
        CustomerResponse response = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", response));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> searchCustomers(@RequestParam("q") String query) {
        List<CustomerResponse> response = customerService.searchCustomers(query.trim());
        return ResponseEntity.ok(ApiResponse.success("Customers retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(
                PaginationUtils.safePage(page),
                PaginationUtils.safeSize(size),
                SortUtils.parse(sort, SortableFields.AllowedFor.CUSTOMER));

        Page<CustomerResponse> customersPage = search != null && !search.trim().isEmpty()
                ? customerService.searchCustomersPaginated(search.trim(), pageable)
                : customerService.getAllCustomersPaginated(pageable);

        PageResponse<CustomerResponse> response = PageResponse.from(customersPage);

        return ResponseEntity.ok(ApiResponse.success("Customers retrieved successfully", response));
    }
}
