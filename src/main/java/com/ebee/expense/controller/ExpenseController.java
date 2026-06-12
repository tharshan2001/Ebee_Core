package com.ebee.expense.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.expense.dto.ExpenseRequest;
import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.expense.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'DRIVER')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @RequestPart("expense") @Valid ExpenseRequest request,
            @RequestParam(value = "receipt", required = false) MultipartFile receipt) {
        ExpenseResponse response = expenseService.createExpense(request, receipt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Expense created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ExpenseResponse>>> getAllExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ExpenseResponse> expenses = expenseService.getAllExpenses(page, size);
        return ResponseEntity.ok(ApiResponse.success("Expenses retrieved successfully", expenses));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getMyExpenses() {
        List<ExpenseResponse> expenses = expenseService.getMyExpenses();
        return ResponseEntity.ok(ApiResponse.success("My expenses retrieved successfully", expenses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @expenseService.isOwner(#id, authentication)")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpenseById(@PathVariable UUID id) {
        ExpenseResponse response = expenseService.getExpenseById(id);
        return ResponseEntity.ok(ApiResponse.success("Expense retrieved successfully", response));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> approveExpense(@PathVariable UUID id) {
        ExpenseResponse response = expenseService.approveExpense(id);
        return ResponseEntity.ok(ApiResponse.success("Expense approved successfully", response));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> rejectExpense(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        ExpenseResponse response = expenseService.rejectExpense(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Expense rejected successfully", response));
    }
}
