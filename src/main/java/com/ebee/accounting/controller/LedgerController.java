package com.ebee.accounting.controller;

import com.ebee.accounting.dto.LedgerEntryResponse;
import com.ebee.accounting.entity.AgentLedger;
import com.ebee.accounting.entity.CustomerLedger;
import com.ebee.accounting.entity.SupplierLedger;
import com.ebee.accounting.enums.LedgerTransactionType;
import com.ebee.accounting.enums.LedgerType;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final SecurityService securityService;

    @GetMapping("/customer/{customerId}/balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<BigDecimal>> getCustomerBalance(@PathVariable UUID customerId) {
        BigDecimal balance = ledgerService.getCustomerBalance(customerId);
        return ResponseEntity.ok(ApiResponse.success("Customer balance retrieved", balance));
    }

    @GetMapping("/supplier/{supplierId}/balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BigDecimal>> getSupplierBalance(@PathVariable UUID supplierId) {
        BigDecimal balance = ledgerService.getSupplierBalance(supplierId);
        return ResponseEntity.ok(ApiResponse.success("Supplier balance retrieved", balance));
    }

    @GetMapping("/agent/{agentId}/balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BigDecimal>> getAgentBalance(@PathVariable UUID agentId) {
        BigDecimal balance = ledgerService.getAgentBalance(agentId);
        return ResponseEntity.ok(ApiResponse.success("Agent balance retrieved", balance));
    }

    @PostMapping("/customer/{customerId}/invoice")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> recordCustomerInvoice(
            @PathVariable UUID customerId,
            @RequestParam UUID orderId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        CustomerLedger entry = ledgerService.recordCustomerInvoice(customerId, orderId, amount, description, securityService.getCurrentUserId());
        BigDecimal balance = ledgerService.getCustomerBalance(customerId);
        LedgerEntryResponse response = toResponse(entry, balance);
        return ResponseEntity.ok(ApiResponse.success("Customer invoice recorded", response));
    }

    @PostMapping("/customer/{customerId}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> recordCustomerPayment(
            @PathVariable UUID customerId,
            @RequestParam UUID paymentId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        CustomerLedger entry = ledgerService.recordCustomerPayment(customerId, paymentId, amount, description, securityService.getCurrentUserId());
        BigDecimal balance = ledgerService.getCustomerBalance(customerId);
        LedgerEntryResponse response = toResponse(entry, balance);
        return ResponseEntity.ok(ApiResponse.success("Customer payment recorded", response));
    }

    @PostMapping("/customer/{customerId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> recordCustomerRefund(
            @PathVariable UUID customerId,
            @RequestParam UUID returnId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        CustomerLedger entry = ledgerService.recordCustomerRefund(customerId, returnId, amount, description, securityService.getCurrentUserId());
        BigDecimal balance = ledgerService.getCustomerBalance(customerId);
        LedgerEntryResponse response = toResponse(entry, balance);
        return ResponseEntity.ok(ApiResponse.success("Customer refund recorded", response));
    }

    @PostMapping("/supplier/{supplierId}/invoice")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> recordSupplierInvoice(
            @PathVariable UUID supplierId,
            @RequestParam UUID purchaseOrderId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) LocalDate effectiveDate,
            @RequestParam(required = false) String reference) {
        SupplierLedger entry = ledgerService.recordSupplierInvoice(
                supplierId, purchaseOrderId, amount, description,
                effectiveDate != null ? effectiveDate : LocalDate.now(),
                reference);
        BigDecimal balance = ledgerService.getSupplierBalance(supplierId);
        LedgerEntryResponse response = toResponseSupplier(entry, balance);
        return ResponseEntity.ok(ApiResponse.success("Supplier invoice recorded", response));
    }

    @PostMapping("/supplier/{supplierId}/payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> recordSupplierPayment(
            @PathVariable UUID supplierId,
            @RequestParam UUID supplierPaymentId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) LocalDate effectiveDate,
            @RequestParam(required = false) String reference) {
        SupplierLedger entry = ledgerService.recordSupplierPayment(
                supplierId, supplierPaymentId, amount, description,
                effectiveDate != null ? effectiveDate : LocalDate.now(),
                reference);
        BigDecimal balance = ledgerService.getSupplierBalance(supplierId);
        LedgerEntryResponse response = toResponseSupplier(entry, balance);
        return ResponseEntity.ok(ApiResponse.success("Supplier payment recorded", response));
    }

    @PostMapping("/agent/{agentId}/commission")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> recordAgentCommission(
            @PathVariable UUID agentId,
            @RequestParam UUID commissionId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        AgentLedger entry = ledgerService.recordAgentCommission(agentId, commissionId, amount, description);
        BigDecimal balance = ledgerService.getAgentBalance(agentId);
        LedgerEntryResponse response = toResponseAgent(entry, balance);
        return ResponseEntity.ok(ApiResponse.success("Agent commission recorded", response));
    }

    @PostMapping("/agent/{agentId}/payout")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> recordAgentPayout(
            @PathVariable UUID agentId,
            @RequestParam UUID payoutId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        AgentLedger entry = ledgerService.recordAgentPayout(agentId, payoutId, amount, description);
        BigDecimal balance = ledgerService.getAgentBalance(agentId);
        LedgerEntryResponse response = toResponseAgent(entry, balance);
        return ResponseEntity.ok(ApiResponse.success("Agent payout recorded", response));
    }

    private LedgerEntryResponse toResponse(CustomerLedger entry, BigDecimal balance) {
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

    private LedgerEntryResponse toResponseSupplier(SupplierLedger entry, BigDecimal balance) {
        return new LedgerEntryResponse(
            entry.getId(),
            entry.getSupplierId(),
            entry.getTransactionType(),
            entry.getDebit(),
            entry.getCredit(),
            entry.getDescription(),
            null,
            null,
            null,
            entry.getCreatedAt()
        );
    }

    private LedgerEntryResponse toResponseAgent(AgentLedger entry, BigDecimal balance) {
        return new LedgerEntryResponse(
            entry.getId(),
            entry.getAgentId(),
            entry.getTransactionType(),
            entry.getDebit(),
            entry.getCredit(),
            entry.getDescription(),
            null,
            null,
            null,
            entry.getCreatedAt()
        );
    }
}
