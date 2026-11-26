package com.ebee.finance.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.finance.dto.AccountStatement;
import com.ebee.finance.dto.BalanceSheet;
import com.ebee.finance.dto.CashFlow;
import com.ebee.finance.dto.MoneyMoveRequest;
import com.ebee.finance.dto.PaymentAccountRequest;
import com.ebee.finance.dto.PaymentAccountResponse;
import com.ebee.finance.entity.AccountTransaction.Direction;
import com.ebee.finance.service.FinancialStatementService;
import com.ebee.finance.service.MoneyAccountService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance")
@PreAuthorize("hasRole('ADMIN')")
public class PaymentAccountController {

    private final MoneyAccountService accounts;
    private final FinancialStatementService statements;

    public PaymentAccountController(MoneyAccountService accounts, FinancialStatementService statements) {
        this.accounts = accounts;
        this.statements = statements;
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<PaymentAccountResponse>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ok("Accounts", accounts.listAccounts(asOf != null ? asOf : LocalDate.now()));
    }

    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<PaymentAccountResponse>> create(@Valid @RequestBody PaymentAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created", accounts.createAccount(request)));
    }

    @PutMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<PaymentAccountResponse>> update(@PathVariable UUID id,
                                                                      @Valid @RequestBody PaymentAccountRequest request) {
        return ok("Account updated", accounts.updateAccount(id, request));
    }

    @PutMapping("/accounts/{id}/opening-balance")
    public ResponseEntity<ApiResponse<PaymentAccountResponse>> openingBalance(@PathVariable UUID id,
            @Valid @RequestBody com.ebee.finance.dto.OpeningBalanceRequest request) {
        return ok("Opening balance set", accounts.setOpeningBalance(id, request.amount(), request.date()));
    }

    @GetMapping("/accounts/{id}/statement")
    public ResponseEntity<ApiResponse<AccountStatement>> statement(@PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok("Account statement", accounts.statement(id, from, to));
    }

    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<UUID>> transfer(@Valid @RequestBody MoneyMoveRequest r) {
        return ok("Transfer recorded", accounts.transfer(r.fromAccountId(), requireTo(r), r.amount(), r.date(), r.note()));
    }

    @PostMapping("/cheque-deposits")
    public ResponseEntity<ApiResponse<UUID>> depositCheques(@Valid @RequestBody MoneyMoveRequest r) {
        return ok("Cheques deposited", accounts.depositCheques(r.fromAccountId(), requireTo(r), r.amount(), r.date(), r.note()));
    }

    @PostMapping("/owner-deposits")
    public ResponseEntity<ApiResponse<UUID>> ownerDeposit(@Valid @RequestBody MoneyMoveRequest r) {
        return ok("Owner deposit recorded",
                accounts.ownerMovement(r.fromAccountId(), Direction.IN, r.amount(), r.date(), r.note()).getId());
    }

    @PostMapping("/owner-withdrawals")
    public ResponseEntity<ApiResponse<UUID>> ownerWithdrawal(@Valid @RequestBody MoneyMoveRequest r) {
        return ok("Owner withdrawal recorded",
                accounts.ownerMovement(r.fromAccountId(), Direction.OUT, r.amount(), r.date(), r.note()).getId());
    }

    @GetMapping("/account-defaults")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> defaults() {
        return ok("Default accounts", accounts.getDefaults());
    }

    @PutMapping("/account-defaults/{purpose}")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> setDefault(@PathVariable String purpose,
                                                                     @RequestParam UUID accountId) {
        return ok("Default account updated", accounts.setDefault(purpose, accountId));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<ApiResponse<BalanceSheet>> balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ok("Balance sheet", statements.balanceSheet(asOf != null ? asOf : LocalDate.now()));
    }

    @GetMapping("/cash-flow")
    public ResponseEntity<ApiResponse<CashFlow>> cashFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok("Cash flow", statements.cashFlow(from, to));
    }

    private static UUID requireTo(MoneyMoveRequest r) {
        if (r.toAccountId() == null) {
            throw new IllegalArgumentException("toAccountId is required");
        }
        return r.toAccountId();
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }
}
