package com.ebee.expense.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.common.port.StoragePort;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.expense.dto.ExpenseRequest;
import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.expense.entity.Expense;
import com.ebee.expense.enums.ExpenseStatus;
import com.ebee.expense.mapper.ExpenseMapper;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final SecurityService securityService;
    private final AuditLogService auditLogService;
    private final StoragePort storagePort;

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request, MultipartFile receipt) {
        User currentUser = userRepository.findById(securityService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", securityService.getCurrentUserId()));

        Expense.ExpenseBuilder builder = Expense.builder()
                .category(request.getCategory())
                .amount(request.getAmount())
                .description(request.getDescription())
                .dateIncurred(request.getDateIncurred())
                .createdBy(currentUser);

        if (request.getDriverId() != null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", request.getDriverId()));
            builder.driver(driver);
        }

        if (currentUser.getRole() == RoleType.ADMIN) {
            builder.status(ExpenseStatus.APPROVED);
            builder.approvedBy(currentUser);
            builder.approvedAt(LocalDateTime.now());
        }

        Expense expense = builder.build();

        if (receipt != null && !receipt.isEmpty()) {
            expense.setReceiptUrl(storagePort.uploadFile(receipt, "expenses", currentUser.getEmail() + "_Expense"));
        }

        expense = expenseRepository.save(expense);

        auditLogService.record(AuditAction.CREATE, "Expense", expense.getId(),
                "Expense " + expense.getCategory() + " LKR " + expense.getAmount() + " created by " + currentUser.getEmail());

        log.info("Expense {} created with status {} by user {}", expense.getId(), expense.getStatus(), currentUser.getEmail());
        return expenseMapper.toResponse(expense);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(UUID id) {
        Expense expense = findExpense(id);
        return expenseMapper.toResponse(expense);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getAllExpenses(int page, int size) {
        int cappedSize = Math.min(size, 200);
        return expenseRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, cappedSize))
                .map(expenseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getMyExpenses() {
        UUID userId = securityService.getCurrentUserId();
        return expenseMapper.toResponseList(expenseRepository.findByCreatedByIdOrderByCreatedAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID expenseId, org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        return expenseRepository.findById(expenseId)
                .map(expense -> expense.getCreatedBy().getEmail().equals(authentication.getName()))
                .orElse(false);
    }

    @Transactional
    public ExpenseResponse approveExpense(UUID id) {
        Expense expense = findExpense(id);

        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new IllegalStateException("Expense " + id + " is not in PENDING status (current: " + expense.getStatus() + ")");
        }

        User admin = userRepository.findById(securityService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", securityService.getCurrentUserId()));

        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setApprovedBy(admin);
        expense.setApprovedAt(LocalDateTime.now());
        expense = expenseRepository.save(expense);

        auditLogService.record(AuditAction.EXPENSE_APPROVE, "Expense", expense.getId(),
                "Expense " + expense.getCategory() + " LKR " + expense.getAmount() + " approved by " + admin.getEmail());

        log.info("Expense {} approved by {}", expense.getId(), admin.getEmail());
        return expenseMapper.toResponse(expense);
    }

    @Transactional
    public ExpenseResponse rejectExpense(UUID id, String reason) {
        Expense expense = findExpense(id);

        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new IllegalStateException("Expense " + id + " is not in PENDING status (current: " + expense.getStatus() + ")");
        }

        User admin = userRepository.findById(securityService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", securityService.getCurrentUserId()));

        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setRejectionReason(reason);
        expense = expenseRepository.save(expense);

        auditLogService.record(AuditAction.EXPENSE_REJECT, "Expense", expense.getId(),
                "Expense " + expense.getCategory() + " LKR " + expense.getAmount() + " rejected by " + admin.getEmail()
                        + (reason != null ? " reason: " + reason : ""));

        log.info("Expense {} rejected by {}", expense.getId(), admin.getEmail());
        return expenseMapper.toResponse(expense);
    }

    private Expense findExpense(UUID id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "id", id));
    }
}
