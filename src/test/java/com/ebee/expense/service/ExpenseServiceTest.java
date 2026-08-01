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
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.enums.ExpenseStatus;
import com.ebee.expense.mapper.ExpenseMapper;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseMapper expenseMapper;
    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private SecurityService securityService;
    @Mock private AuditLogService auditLogService;
    @Mock private StoragePort storagePort;

    @InjectMocks private ExpenseService expenseService;

    @Captor private ArgumentCaptor<Expense> expenseCaptor;

    private User admin;
    private User agent;
    private UUID currentUserId;
    private ExpenseRequest request;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        admin = User.builder().id(currentUserId).email("admin@ebee.com").role(RoleType.ADMIN).build();
        agent = User.builder().id(UUID.randomUUID()).email("agent@ebee.com").role(RoleType.AGENT).build();

        request = new ExpenseRequest();
        request.setCategory(ExpenseCategory.PETROL);
        request.setAmount(new BigDecimal("5000.00"));
        request.setDescription("Fuel for delivery van");
        request.setDateIncurred(LocalDate.now());
    }

    @Test
    void createExpense_asAdmin_setsApproved() {
        when(securityService.getCurrentUserId()).thenReturn(currentUserId);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(admin));

        Expense expense = Expense.builder().id(UUID.randomUUID()).status(ExpenseStatus.APPROVED).build();
        when(expenseRepository.save(any())).thenReturn(expense);

        ExpenseResponse expectedResponse = new ExpenseResponse();
        when(expenseMapper.toResponse(expense)).thenReturn(expectedResponse);

        var result = expenseService.createExpense(request, null);

        assertThat(result).isEqualTo(expectedResponse);
        verify(expenseRepository).save(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(expenseCaptor.getValue().getApprovedBy()).isEqualTo(admin);
        assertThat(expenseCaptor.getValue().getApprovedAt()).isNotNull();
        verify(auditLogService).record(eq(AuditAction.CREATE), eq("Expense"), any(), anyString());
    }

    @Test
    void createExpense_asAgent_setsPending() {
        when(securityService.getCurrentUserId()).thenReturn(agent.getId());
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        Expense expense = Expense.builder().id(UUID.randomUUID()).status(ExpenseStatus.PENDING).build();
        when(expenseRepository.save(any())).thenReturn(expense);
        when(expenseMapper.toResponse(expense)).thenReturn(new ExpenseResponse());

        expenseService.createExpense(request, null);

        verify(expenseRepository).save(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getStatus()).isEqualTo(ExpenseStatus.PENDING);
    }

    @Test
    void createExpense_withDriver_linksDriver() {
        UUID driverId = UUID.randomUUID();
        request.setDriverId(driverId);
        Driver driver = Driver.builder().id(driverId).build();

        when(securityService.getCurrentUserId()).thenReturn(currentUserId);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(admin));
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        Expense expense = Expense.builder().id(UUID.randomUUID()).status(ExpenseStatus.APPROVED).build();
        when(expenseRepository.save(any())).thenReturn(expense);
        when(expenseMapper.toResponse(expense)).thenReturn(new ExpenseResponse());

        expenseService.createExpense(request, null);

        verify(expenseRepository).save(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getDriver()).isEqualTo(driver);
    }

    @Test
    void createExpense_withReceipt_uploads() {
        var receipt = mock(org.springframework.web.multipart.MultipartFile.class);
        when(receipt.isEmpty()).thenReturn(false);

        when(securityService.getCurrentUserId()).thenReturn(currentUserId);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(admin));
        when(storagePort.uploadFile(receipt, "expenses", "admin@ebee.com_Expense")).thenReturn("https://s3.url/receipt.pdf");

        Expense expense = Expense.builder().id(UUID.randomUUID()).build();
        when(expenseRepository.save(any())).thenReturn(expense);
        when(expenseMapper.toResponse(expense)).thenReturn(new ExpenseResponse());

        expenseService.createExpense(request, receipt);

        verify(expenseRepository).save(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getReceiptUrl()).isEqualTo("https://s3.url/receipt.pdf");
    }

    @Test
    void approveExpense_pendingStatus_succeeds() {
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder()
                .id(expenseId)
                .status(ExpenseStatus.PENDING)
                .category(ExpenseCategory.PETROL)
                .amount(new BigDecimal("5000"))
                .build();

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));
        when(securityService.getCurrentUserId()).thenReturn(currentUserId);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(admin));

        Expense saved = Expense.builder().id(expenseId).status(ExpenseStatus.APPROVED).approvedBy(admin).approvedAt(LocalDateTime.now()).build();
        when(expenseRepository.save(any())).thenReturn(saved);
        when(expenseMapper.toResponse(saved)).thenReturn(new ExpenseResponse());

        expenseService.approveExpense(expenseId);

        verify(expenseRepository).save(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(expenseCaptor.getValue().getApprovedBy()).isEqualTo(admin);
        verify(auditLogService).record(eq(AuditAction.EXPENSE_APPROVE), eq("Expense"), eq(expenseId), anyString());
    }

    @Test
    void approveExpense_alreadyApproved_throws() {
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder().id(expenseId).status(ExpenseStatus.APPROVED).build();
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));

        assertThatThrownBy(() -> expenseService.approveExpense(expenseId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not in PENDING status");
    }

    @Test
    void rejectExpense_pendingStatus_succeeds() {
        UUID expenseId = UUID.randomUUID();
        Expense expense = Expense.builder()
                .id(expenseId)
                .status(ExpenseStatus.PENDING)
                .category(ExpenseCategory.PETROL)
                .amount(new BigDecimal("5000"))
                .build();

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));
        when(securityService.getCurrentUserId()).thenReturn(currentUserId);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(admin));

        Expense saved = Expense.builder().id(expenseId).status(ExpenseStatus.REJECTED).build();
        when(expenseRepository.save(any())).thenReturn(saved);
        when(expenseMapper.toResponse(saved)).thenReturn(new ExpenseResponse());

        expenseService.rejectExpense(expenseId, "Invalid receipt");

        verify(expenseRepository).save(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getStatus()).isEqualTo(ExpenseStatus.REJECTED);
        assertThat(expenseCaptor.getValue().getRejectionReason()).isEqualTo("Invalid receipt");
        verify(auditLogService).record(eq(AuditAction.EXPENSE_REJECT), eq("Expense"), eq(expenseId), anyString());
    }

    @Test
    void getExpenseById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(expenseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.getExpenseById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Expense not found");
    }

    @Test
    void isOwner_returnsTrueForOwner() {
        UUID expenseId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).email("owner@ebee.com").build();
        Expense expense = Expense.builder().id(expenseId).createdBy(owner).build();

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "owner@ebee.com", null, null);

        assertThat(expenseService.isOwner(expenseId, auth)).isTrue();
    }

    @Test
    void isOwner_returnsFalseForNonOwner() {
        UUID expenseId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).email("owner@ebee.com").build();
        Expense expense = Expense.builder().id(expenseId).createdBy(owner).build();

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "other@ebee.com", null, null);

        assertThat(expenseService.isOwner(expenseId, auth)).isFalse();
    }
}
