package com.ebee.delivery.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.common.result.Result;
import com.ebee.delivery.dto.DeliveryResponse;
import com.ebee.delivery.dto.DeliveryStatusUpdateRequest;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.service.DeliveryService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeliveryController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class DeliveryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DeliveryService deliveryService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private RateLimitingService rateLimitingService;
    @MockBean private IpWhitelistService ipWhitelistService;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
        when(ipWhitelistService.isAllowed(any(), anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDeliveryStatus_asAdmin_returns200() throws Exception {
        when(deliveryService.updateDeliveryStatus(any(UUID.class), any(DeliveryStatusUpdateRequest.class)))
                .thenReturn(new DeliveryResponse());

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest();
        request.setStatus(DeliveryStatus.IN_TRANSIT);

        mockMvc.perform(patch("/api/deliveries/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery status updated successfully"));

        verify(deliveryService).updateDeliveryStatus(any(UUID.class), any(DeliveryStatusUpdateRequest.class));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void updateDeliveryStatus_asDriver_returns200() throws Exception {
        when(deliveryService.updateDeliveryStatus(any(UUID.class), any(DeliveryStatusUpdateRequest.class)))
                .thenReturn(new DeliveryResponse());

        DeliveryStatusUpdateRequest request = new DeliveryStatusUpdateRequest();
        request.setStatus(DeliveryStatus.PICKED_UP);

        mockMvc.perform(patch("/api/deliveries/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(deliveryService).updateDeliveryStatus(any(UUID.class), any(DeliveryStatusUpdateRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDeliveries_returns200() throws Exception {
        mockMvc.perform(get("/api/deliveries")
                        .param("status", "PENDING"))
                .andExpect(status().isOk());

        verify(deliveryService).getDeliveries(DeliveryStatus.PENDING, null, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMyDeliveries_returns200() throws Exception {
        mockMvc.perform(get("/api/deliveries/mine"))
                .andExpect(status().isOk());

        verify(deliveryService).getMyDeliveries(false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDeliveryDetail_returns200() throws Exception {
        when(deliveryService.getDeliveryDetail(orderId))
                .thenReturn(Result.ok(new DeliveryResponse()));

        mockMvc.perform(get("/api/deliveries/{orderId}", orderId))
                .andExpect(status().isOk());

        verify(deliveryService).getDeliveryDetail(orderId);
    }
}
