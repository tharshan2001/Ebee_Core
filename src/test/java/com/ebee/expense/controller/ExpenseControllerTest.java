package com.ebee.expense.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.expense.dto.ExpenseRequest;
import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.service.ExpenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class ExpenseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ExpenseService expenseService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private RateLimitingService rateLimitingService;
    @MockBean private IpWhitelistService ipWhitelistService;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
        when(ipWhitelistService.isAllowed(any(), anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_asAdmin_returns201() throws Exception {
        when(expenseService.createExpense(any(), any())).thenReturn(new ExpenseResponse());

        MockMultipartFile expensePart = new MockMultipartFile(
                "expense", "", "application/json",
                objectMapper.writeValueAsBytes(createExpenseRequest()));

        mockMvc.perform(multipart("/api/expenses")
                        .file(expensePart)
                        .file("receipt", new byte[0]))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createExpense_asAgent_returns201() throws Exception {
        when(expenseService.createExpense(any(), any())).thenReturn(new ExpenseResponse());

        MockMultipartFile expensePart = new MockMultipartFile(
                "expense", "", "application/json",
                objectMapper.writeValueAsBytes(createExpenseRequest()));

        mockMvc.perform(multipart("/api/expenses")
                        .file(expensePart))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void createExpense_asDriver_returns201() throws Exception {
        when(expenseService.createExpense(any(), any())).thenReturn(new ExpenseResponse());

        MockMultipartFile expensePart = new MockMultipartFile(
                "expense", "", "application/json",
                objectMapper.writeValueAsBytes(createExpenseRequest()));

        mockMvc.perform(multipart("/api/expenses")
                        .file(expensePart))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void createExpense_asUnauthorizedRole_returns403() throws Exception {
        MockMultipartFile expensePart = new MockMultipartFile(
                "expense", "", "application/json",
                objectMapper.writeValueAsBytes(createExpenseRequest()));

        mockMvc.perform(multipart("/api/expenses")
                        .file(expensePart))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllExpenses_asAdmin_returns200() throws Exception {
        Page<ExpenseResponse> page = new PageImpl<>(List.of(new ExpenseResponse()));
        when(expenseService.getAllExpenses(anyInt(), anyInt(), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void getAllExpenses_asNonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approveExpense_asAdmin_returns200() throws Exception {
        UUID expenseId = UUID.randomUUID();
        when(expenseService.approveExpense(expenseId, null)).thenReturn(new ExpenseResponse());

        mockMvc.perform(put("/api/expenses/{id}/approve", expenseId))
                .andExpect(status().isOk());
        verify(expenseService).approveExpense(expenseId, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approveExpense_withAccount_passesTheAccountOn() throws Exception {
        UUID expenseId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(expenseService.approveExpense(expenseId, accountId)).thenReturn(new ExpenseResponse());

        mockMvc.perform(put("/api/expenses/{id}/approve", expenseId).param("accountId", accountId.toString()))
                .andExpect(status().isOk());
        verify(expenseService).approveExpense(expenseId, accountId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllExpenses_withStatus_filtersByIt() throws Exception {
        when(expenseService.getAllExpenses(anyInt(), anyInt(), eq(com.ebee.expense.enums.ExpenseStatus.PENDING)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/expenses").param("status", "PENDING"))
                .andExpect(status().isOk());
        verify(expenseService).getAllExpenses(0, 20, com.ebee.expense.enums.ExpenseStatus.PENDING);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void approveExpense_asNonAdmin_returns403() throws Exception {
        UUID expenseId = UUID.randomUUID();

        mockMvc.perform(put("/api/expenses/{id}/approve", expenseId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectExpense_asAdmin_returns200() throws Exception {
        UUID expenseId = UUID.randomUUID();
        when(expenseService.rejectExpense(expenseId, "Invalid receipt"))
                .thenReturn(new ExpenseResponse());

        mockMvc.perform(put("/api/expenses/{id}/reject", expenseId)
                        .param("reason", "Invalid receipt"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getMyExpenses_asAuthenticatedUser_returns200() throws Exception {
        when(expenseService.getMyExpenses()).thenReturn(List.of(new ExpenseResponse()));

        mockMvc.perform(get("/api/expenses/my"))
                .andExpect(status().isOk());
    }

    private ExpenseRequest createExpenseRequest() {
        ExpenseRequest request = new ExpenseRequest();
        request.setCategory(ExpenseCategory.PETROL);
        request.setAmount(new BigDecimal("5000.00"));
        request.setDescription("Fuel for delivery van");
        request.setDateIncurred(LocalDate.now());
        return request;
    }
}
