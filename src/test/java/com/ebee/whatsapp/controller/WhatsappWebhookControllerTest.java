package com.ebee.whatsapp.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.whatsapp.dto.WhatsappWebhookRequest;
import com.ebee.whatsapp.service.WhatsappService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WhatsappWebhookController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class WhatsappWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WhatsappService whatsappService;

    @MockBean
    private RateLimitingService rateLimitingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private IpWhitelistService ipWhitelistService;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
        when(jwtService.extractEmail(anyString())).thenReturn("admin@ebee.com");
        when(userDetailsService.loadUserByUsername("admin@ebee.com")).thenReturn(null);
        when(ipWhitelistService.isAllowed(any(), anyString())).thenReturn(true);
    }

    @Test
    void verify_shouldReturnChallenge_whenValid() throws Exception {
        when(whatsappService.verifySubscription("subscribe", "ebee_wh_verify_2024", "12345"))
                .thenReturn("12345");

        mockMvc.perform(get("/api/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "ebee_wh_verify_2024")
                        .param("hub.challenge", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("12345"));

        verify(whatsappService).verifySubscription("subscribe", "ebee_wh_verify_2024", "12345");
    }

    @Test
    void verify_shouldReturn403_whenInvalid() throws Exception {
        doThrow(new WhatsappService.WhatsappVerificationException("Invalid verify token"))
                .when(whatsappService).verifySubscription("subscribe", "wrong", "12345");

        mockMvc.perform(get("/api/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong")
                        .param("hub.challenge", "12345"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Verification failed"));
    }

    @Test
    void receive_shouldReturn200_andProcessPayload_whenSignatureValid() throws Exception {
        WhatsappWebhookRequest request = new WhatsappWebhookRequest("whatsapp_business_account", java.util.List.of());
        when(whatsappService.isValidSignature(any(), eq("sha256=good"))).thenReturn(true);

        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=good")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(whatsappService).processWebhook(any(WhatsappWebhookRequest.class));
    }

    @Test
    void receive_shouldReturn401_andNotProcess_whenSignatureInvalid() throws Exception {
        when(whatsappService.isValidSignature(any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"object\":\"whatsapp_business_account\",\"entry\":[]}"))
                .andExpect(status().isUnauthorized());

        verify(whatsappService, never()).processWebhook(any());
    }

    @Test
    void receive_shouldReturn400_whenSignedButMalformedJson() throws Exception {
        when(whatsappService.isValidSignature(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=good")
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        verify(whatsappService, never()).processWebhook(any());
    }

    @Test
    void receive_shouldNotProcess_whenInvalidContentType() throws Exception {
        mockMvc.perform(post("/api/whatsapp/webhook")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());

        verify(whatsappService, never()).processWebhook(any());
    }
}
