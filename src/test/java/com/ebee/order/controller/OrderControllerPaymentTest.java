package com.ebee.order.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.common.response.ApiResponse;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.OrderSettlementRequest;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.enums.RefundChoice;
import com.ebee.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class OrderControllerPaymentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private RateLimitingService rateLimitingService;

    @MockBean
    private IpWhitelistService ipWhitelistService;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordPayment_shouldReturnUpdatedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("2000.00"), PaymentMethod.CASH, null, null);

        OrderResponse response = new OrderResponse();
        response.setId(orderId);
        response.setPaymentMethod(PaymentMethod.CASH);
        response.setPaymentStatus(PaymentStatus.PAID);
        response.setPaidAmount(new BigDecimal("2000.00"));
        response.setOutstandingBalance(BigDecimal.ZERO);
        response.setPaidAt(LocalDateTime.now());

        when(orderService.recordPayment(eq(orderId), any(RecordPaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders/{id}/payment", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Payment recorded successfully"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.paidAmount").value(2000.00));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordPayment_partialPayment_shouldReturnUpdatedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("800.00"), PaymentMethod.CASH, null, null);

        OrderResponse response = new OrderResponse();
        response.setId(orderId);
        response.setPaymentMethod(PaymentMethod.CASH);
        response.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
        response.setPaidAmount(new BigDecimal("800.00"));
        response.setOutstandingBalance(new BigDecimal("1200.00"));
        response.setPaidAt(LocalDateTime.now());

        when(orderService.recordPayment(eq(orderId), any(RecordPaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders/{id}/payment", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.data.paidAmount").value(800.00))
                .andExpect(jsonPath("$.data.outstandingBalance").value(1200.00));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void recordPayment_asAgent_shouldSucceed() throws Exception {
        UUID orderId = UUID.randomUUID();
        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("2000.00"), PaymentMethod.CASH, null, null);

        OrderResponse response = new OrderResponse();
        response.setId(orderId);
        response.setPaymentMethod(PaymentMethod.CASH);
        response.setPaymentStatus(PaymentStatus.PAID);

        when(orderService.recordPayment(eq(orderId), any(RecordPaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders/{id}/payment", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void recordPayment_withoutAuth_shouldReturnUnauthorized() throws Exception {
        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("2000.00"), PaymentMethod.CASH, null, null);

        mockMvc.perform(post("/api/orders/{id}/payment", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordPayment_withInvalidAmount_shouldReturnBadRequest() throws Exception {
        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("-100.00"), PaymentMethod.CASH, null, null);

        mockMvc.perform(post("/api/orders/{id}/payment", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordPayment_withNullMethod_shouldReturnBadRequest() throws Exception {
        String invalidJson = """
                {
                    "amount": 1000.00,
                    "paymentMethod": null
                }
                """;

        mockMvc.perform(post("/api/orders/{id}/payment", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnOrder_shouldReturnSuccess() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse();
        response.setId(orderId);
        response.setStatus(OrderStatus.RETURNED);
        response.setPaymentStatus(PaymentStatus.REFUNDED);

        when(orderService.returnOrder(eq(orderId), any(OrderSettlementRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders/{id}/return", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundChoice\":\"REFUND\",\"notes\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order returned successfully"))
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));
    }
}
