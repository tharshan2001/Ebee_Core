package com.ebee.customer.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.customer.dto.CreditLimitRequest;
import com.ebee.customer.dto.CustomerPaymentResponse;
import com.ebee.customer.dto.CustomerResponse;
import com.ebee.customer.service.CustomerService;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.enums.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class CustomerDuesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private IpWhitelistService ipWhitelistService;

    @MockBean
    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCustomersWithDues_shouldReturnList() throws Exception {
        CustomerResponse r1 = new CustomerResponse();
        r1.setId(UUID.randomUUID());
        r1.setFirstName("Kamal");
        r1.setOutstandingBalance(new BigDecimal("25000.00"));

        CustomerResponse r2 = new CustomerResponse();
        r2.setId(UUID.randomUUID());
        r2.setFirstName("Nimal");
        r2.setOutstandingBalance(new BigDecimal("8500.00"));

        when(customerService.getCustomersWithDues()).thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/customers/with-dues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].firstName").value("Kamal"))
                .andExpect(jsonPath("$.data[0].outstandingBalance").value(25000.00))
                .andExpect(jsonPath("$.data[1].firstName").value("Nimal"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void getCustomersWithDues_shouldDenyAgent() throws Exception {
        mockMvc.perform(get("/api/customers/with-dues"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCreditLimit_shouldReturnUpdatedCustomer() throws Exception {
        UUID id = UUID.randomUUID();
        CreditLimitRequest request = new CreditLimitRequest(new BigDecimal("75000.00"));

        CustomerResponse response = new CustomerResponse();
        response.setId(id);
        response.setFirstName("Kamal");
        response.setCreditLimit(new BigDecimal("75000.00"));

        when(customerService.updateCreditLimit(any(), any())).thenReturn(response);

        mockMvc.perform(patch("/api/customers/{id}/credit-limit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.creditLimit").value(75000.00));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCreditLimit_shouldRejectNegativeValue() throws Exception {
        String body = """
                {"creditLimit": -100}
                """;

        mockMvc.perform(patch("/api/customers/{id}/credit-limit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordCustomerPayment_shouldReturnPaymentResponse() throws Exception {
        UUID id = UUID.randomUUID();
        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("20000.00"), PaymentMethod.CASH);

        CustomerPaymentResponse response = new CustomerPaymentResponse(
                id, "Kamal Perera", new BigDecimal("20000.00"), 2, new BigDecimal("5000.00"));

        when(customerService.recordCustomerPayment(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/customers/{id}/payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amountPaid").value(20000.00))
                .andExpect(jsonPath("$.data.ordersSettled").value(2))
                .andExpect(jsonPath("$.data.customerRemainingBalance").value(5000.00));
    }
}
