package com.ebee.payout.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.payout.service.PayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Payouts used to have no role checks: any signed-in user could list, create and process them. */
@WebMvcTest(PayoutController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class PayoutControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PayoutService payoutService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private RateLimitingService rateLimitingService;
    @MockBean private IpWhitelistService ipWhitelistService;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
        when(ipWhitelistService.isAllowed(any(), anyString())).thenReturn(true);
        when(payoutService.getAllPayouts()).thenReturn(List.of());
        when(payoutService.getPayoutPdf(any())).thenReturn(new byte[] {1});
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canListPayouts() throws Exception {
        mockMvc.perform(get("/api/payouts")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agent_cannotListPayouts() throws Exception {
        mockMvc.perform(get("/api/payouts")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payouts/" + id)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payouts/agent/" + id)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agent_cannotCreateOrProcessPayouts() throws Exception {
        mockMvc.perform(post("/api/payouts/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentIds\":[\"" + id + "\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/payouts/" + id + "/process")).andExpect(status().isForbidden());
        verify(payoutService, never()).createBatchPayouts(any());
        verify(payoutService, never()).processPayout(any());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_cannotDownloadPdf() throws Exception {
        mockMvc.perform(get("/api/payouts/" + id + "/pdf")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agent_reachesPdf_serviceChecksOwnership() throws Exception {
        mockMvc.perform(get("/api/payouts/" + id + "/pdf")).andExpect(status().isOk());
    }
}
