package com.ebee.auth.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.dto.AuthResponse;
import com.ebee.auth.dto.LoginRequest;
import com.ebee.auth.dto.RegisterRequest;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.AuthService;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.user.enums.RoleType;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
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
    void login_withValidCredentials_returns200() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@ebee.com");
        request.setPassword("password");

        AuthResponse authResponse = AuthResponse.builder()
                .token("access-token")
                .refreshToken("refresh-token")
                .email("admin@ebee.com")
                .build();

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void register_asAdmin_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@ebee.com");
        request.setPassword("Pass123!");
        request.setFirstName("New");
        request.setLastName("User");
        request.setPhoneNumber("0771234567");
        request.setRole(RoleType.AGENT);

        when(authService.register(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void register_asNonAdmin_returns403() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@ebee.com");
        request.setPassword("Pass123!");
        request.setFirstName("New");
        request.setLastName("User");
        request.setPhoneNumber("0771234567");
        request.setRole(RoleType.AGENT);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void forgotPassword_publicEndpoint_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@ebee.com"}
                                """))
                .andExpect(status().isOk());
    }
}
