package com.ebee.combo.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.combo.dto.ComboItemRequest;
import com.ebee.combo.dto.ComboRequest;
import com.ebee.combo.dto.ComboResponse;
import com.ebee.combo.exception.ComboImmutabilityException;
import com.ebee.combo.service.ComboService;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.common.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ComboController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class ComboControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ComboService comboService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private RateLimitingService rateLimitingService;
    @MockBean private IpWhitelistService ipWhitelistService;

    private ComboResponse comboResponse;
    private ComboRequest comboRequest;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(any(String.class))).thenReturn(true);

        comboResponse = new ComboResponse();
        comboResponse.setId(UUID.randomUUID());
        comboResponse.setName("Hair Care Bundle");
        comboResponse.setComboPrice(new BigDecimal("1750.00"));
        comboResponse.setIsActive(true);

        comboRequest = new ComboRequest();
        comboRequest.setName("Hair Care Bundle");
        comboRequest.setDescription("Shampoo + Conditioner");
        ComboItemRequest item = new ComboItemRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCombo_validRequest_returns201() throws Exception {
        when(comboService.createCombo(any())).thenReturn(comboResponse);

        mockMvc.perform(post("/api/admin/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValidRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Hair Care Bundle"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCombo_invalidRequest_returns400() throws Exception {
        ComboRequest bad = new ComboRequest();
        bad.setName("");

        mockMvc.perform(post("/api/admin/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCombo_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValidRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createCombo_agentRole_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValidRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCombo_validRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(comboService.updateCombo(eq(id), any())).thenReturn(comboResponse);

        mockMvc.perform(put("/api/admin/combos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValidRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCombo_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(comboService.updateCombo(eq(id), any()))
                .thenThrow(new ResourceNotFoundException("Combo", "id", id));

        mockMvc.perform(put("/api/admin/combos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildValidRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCombo_noOrders_returns200() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/combos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCombo_hasOrders_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ComboImmutabilityException("Cannot delete combo — used in orders"))
                .when(comboService).deleteCombo(id);

        mockMvc.perform(delete("/api/admin/combos/{id}", id))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getComboById_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(comboService.getComboById(id)).thenReturn(comboResponse);

        mockMvc.perform(get("/api/combos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Hair Care Bundle"));
    }

    @Test
    void listActiveCombos_returns200() throws Exception {
        when(comboService.listActiveCombos(any(), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(comboResponse)));

        mockMvc.perform(get("/api/combos")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listAllCombos_admin_returns200() throws Exception {
        when(comboService.listAllCombos(any(), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(comboResponse)));

        mockMvc.perform(get("/api/admin/combos")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private ComboRequest buildValidRequest() {
        ComboRequest request = new ComboRequest();
        request.setName("Hair Care Bundle");
        request.setDescription("Shampoo + Conditioner");

        ComboItemRequest item = new ComboItemRequest();
        item.setProductId(UUID.randomUUID());
        item.setQuantity(2);
        request.setItems(List.of(item));

        return request;
    }
}
