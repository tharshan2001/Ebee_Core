package com.ebee.customer.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.customer.service.CustomerLedgerReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only: compares each customer's receivable ledger against their orders. */
@RestController
@RequestMapping("/api/accounting/reconciliation")
public class CustomerReconciliationController {

    private final CustomerLedgerReconciliationService reconciliationService;

    public CustomerReconciliationController(CustomerLedgerReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/customers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CustomerLedgerReconciliationService.Row>>> customers(
            @RequestParam(defaultValue = "true") boolean mismatchesOnly) {
        return ResponseEntity.ok(ApiResponse.success("Customer ledger reconciliation",
                reconciliationService.reconcile(mismatchesOnly)));
    }
}
