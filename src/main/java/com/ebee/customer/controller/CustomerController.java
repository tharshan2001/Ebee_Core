package com.ebee.customer.controller;

import com.ebee.accounting.dto.LedgerEntryResponse;
import com.ebee.accounting.entity.CustomerLedger;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.common.util.PaginationUtils;
import com.ebee.common.util.SortableFields;
import com.ebee.customer.dto.AdjustAdvanceRequest;
import com.ebee.customer.dto.AdjustOutstandingRequest;
import com.ebee.customer.dto.AdvanceTransactionResponse;
import com.ebee.customer.dto.CreditLimitRequest;
import com.ebee.customer.dto.CustomerBalanceResponse;
import com.ebee.customer.dto.CustomerPaymentResponse;
import com.ebee.customer.dto.CustomerRequest;
import com.ebee.customer.dto.CustomerResponse;
import com.ebee.customer.dto.OutstandingAdjustmentResponse;
import com.ebee.customer.entity.CustomerAdvanceTransaction;
import com.ebee.customer.service.CustomerService;
import com.ebee.order.dto.RecordPaymentRequest;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @GetMapping("/book-no/{bookNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerByBookNo(@PathVariable String bookNo) {
        CustomerResponse response = customerService.getCustomerByBookNo(bookNo);
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

    @GetMapping("/with-dues")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getCustomersWithDues() {
        List<CustomerResponse> response = customerService.getCustomersWithDues();
        return ResponseEntity.ok(ApiResponse.success("Customers with dues retrieved successfully", response));
    }

    @PatchMapping("/{id}/credit-limit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCreditLimit(
            @PathVariable UUID id,
            @Valid @RequestBody CreditLimitRequest request) {
        CustomerResponse response = customerService.updateCreditLimit(id, request);
        return ResponseEntity.ok(ApiResponse.success("Credit limit updated successfully", response));
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomerPaymentResponse>> recordCustomerPayment(
            @PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest request) {
        CustomerPaymentResponse response = customerService.recordCustomerPayment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Payment recorded successfully", response));
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomerBalanceResponse>> getCustomerBalance(@PathVariable UUID id) {
        CustomerResponse customer = customerService.getCustomerById(id);
        CustomerBalanceResponse response = new CustomerBalanceResponse(
                customer.getId(),
                customer.getFirstName() + " " + customer.getLastName(),
                customer.getOutstandingBalance(),
                customer.getAdvanceBalance()
        );
        return ResponseEntity.ok(ApiResponse.success("Customer balance retrieved successfully", response));
    }

    @GetMapping("/{id}/advance-history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdvanceTransactionResponse>>> getAdvanceHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AdvanceTransactionResponse> history = customerService.getAdvanceHistory(id, pageable);
        PageResponse<AdvanceTransactionResponse> response = PageResponse.from(history);
        return ResponseEntity.ok(ApiResponse.success("Advance history retrieved successfully", response));
    }

    @PostMapping("/{id}/adjust-advance")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdvanceTransactionResponse>> adjustAdvance(
            @PathVariable UUID id,
            @Valid @RequestBody AdjustAdvanceRequest request) {
        CustomerAdvanceTransaction transaction = customerService.manualAdjustAdvance(id, request);
        return ResponseEntity.ok(ApiResponse.success("Advance balance adjusted successfully", toAdvanceResponse(transaction)));
    }

    @PostMapping("/{id}/adjust-outstanding")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<OutstandingAdjustmentResponse>> adjustOutstanding(
            @PathVariable UUID id,
            @Valid @RequestBody AdjustOutstandingRequest request) {
        OutstandingAdjustmentResponse response = customerService.manualAdjustOutstanding(id, request);
        return ResponseEntity.ok(ApiResponse.success("Outstanding balance adjusted successfully", response));
    }

    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<LedgerEntryResponse>>> getCustomerLedger(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        Pageable pageable = PageRequest.of(page, size);

        List<CustomerLedger> filteredEntries;
        if (entryType != null || startDate != null || endDate != null) {
            filteredEntries = customerService.getCustomerLedgerFiltered(id, entryType, startDate, endDate);
        } else {
            filteredEntries = customerService.getCustomerLedger(id, pageable).getContent();
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + size, filteredEntries.size());
        List<CustomerLedger> pageContent = start < filteredEntries.size()
                ? filteredEntries.subList(start, end)
                : List.of();
        Page<CustomerLedger> filtered = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filteredEntries.size());

        PageResponse<LedgerEntryResponse> response = PageResponse.from(filtered.map(this::toLedgerEntryResponse));
        return ResponseEntity.ok(ApiResponse.success("Customer ledger retrieved successfully", response));
    }

    @GetMapping("/{id}/ledger/outstanding")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BigDecimal>> getCustomerLedgerOutstanding(@PathVariable UUID id) {
        BigDecimal balance = customerService.getCustomerLedgerBalance(id);
        return ResponseEntity.ok(ApiResponse.success("Customer ledger balance retrieved", balance));
    }

    private AdvanceTransactionResponse toAdvanceResponse(CustomerAdvanceTransaction tx) {
        return new AdvanceTransactionResponse(
                tx.getId(),
                tx.getCustomer().getId(),
                tx.getOrder() != null ? tx.getOrder().getId() : null,
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getTransactionType(),
                tx.getReferenceType(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }

    private LedgerEntryResponse toLedgerEntryResponse(CustomerLedger entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getCustomerId(),
                entry.getTransactionType(),
                entry.getDebit(),
                entry.getCredit(),
                entry.getDescription(),
                entry.getOrderId(),
                entry.getPaymentId(),
                entry.getReturnId(),
                entry.getCreatedAt()
        );
    }
}
