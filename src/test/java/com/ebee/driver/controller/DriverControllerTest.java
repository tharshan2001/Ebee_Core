package com.ebee.driver.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.driver.dto.AvailabilityRequest;
import com.ebee.driver.dto.DriverRequest;
import com.ebee.driver.dto.DriverResponse;
import com.ebee.driver.dto.DriverWithCredentialsResponse;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.dto.DriverResponse;
import com.ebee.driver.service.DriverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DriverController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class DriverControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DriverService driverService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private RateLimitingService rateLimitingService;
    @MockBean private IpWhitelistService ipWhitelistService;

    private final UUID driverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
        when(ipWhitelistService.isAllowed(any(), anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDriver_asAdmin_returns201() throws Exception {
        when(driverService.createDriver(any(DriverRequest.class)))
                .thenReturn(DriverWithCredentialsResponse.builder().build());

        DriverRequest request = new DriverRequest();
        request.setFirstName("New");
        request.setLastName("Driver");
        request.setEmail("new.driver@ebee.com");
        request.setPhoneNumber("0771234567");

        mockMvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Driver created successfully"));

        verify(driverService).createDriver(any(DriverRequest.class));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createDriver_asNonAdmin_returns403() throws Exception {
        DriverRequest request = new DriverRequest();
        request.setFirstName("New");
        request.setLastName("Driver");
        request.setEmail("new.driver@ebee.com");
        request.setPhoneNumber("0771234567");

        mockMvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDrivers_returns200() throws Exception {
        Page<DriverResponse> page = new PageImpl<>(Collections.emptyList());
        when(driverService.getDrivers(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/drivers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(driverService).getDrivers(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDriver_returns200() throws Exception {
        when(driverService.getDriver(driverId)).thenReturn(new DriverResponse());

        mockMvc.perform(get("/api/drivers/{id}", driverId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(driverService).getDriver(driverId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDriver_asAdmin_returns200() throws Exception {
        when(driverService.updateDriver(any(UUID.class), any(DriverRequest.class)))
                .thenReturn(new DriverResponse());

        DriverRequest request = new DriverRequest();
        request.setFirstName("Updated");
        request.setLastName("Driver");
        request.setEmail("updated.driver@ebee.com");
        request.setPhoneNumber("0771234567");

        mockMvc.perform(patch("/api/drivers/{id}", driverId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(driverService).updateDriver(any(UUID.class), any(DriverRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateAvailability_asAdmin_returns200() throws Exception {
        when(driverService.updateAvailability(any(UUID.class), any(AvailabilityRequest.class)))
                .thenReturn(new DriverResponse());

        AvailabilityRequest request = new AvailabilityRequest();
        request.setAvailability(DriverAvailability.SUSPENDED);

        mockMvc.perform(patch("/api/drivers/{id}/availability", driverId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Availability updated successfully"));

        verify(driverService).updateAvailability(driverId, request);
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void updateAvailability_asDriver_returns200() throws Exception {
        when(driverService.updateAvailability(any(UUID.class), any(AvailabilityRequest.class)))
                .thenReturn(new DriverResponse());

        AvailabilityRequest request = new AvailabilityRequest();
        request.setAvailability(DriverAvailability.OFFLINE);

        mockMvc.perform(patch("/api/drivers/{id}/availability", driverId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(driverService).updateAvailability(driverId, request);
    }
}
