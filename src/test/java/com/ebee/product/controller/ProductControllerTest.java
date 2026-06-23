package com.ebee.product.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private RateLimitingService rateLimitingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private IpWhitelistService ipWhitelistService;

    private UUID productId;
    private UUID imageId;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
        when(jwtService.extractEmail(anyString())).thenReturn("admin@ebee.com");
        when(userDetailsService.loadUserByUsername("admin@ebee.com")).thenReturn(null);
        when(ipWhitelistService.isAllowed(any(), anyString())).thenReturn(true);
        productId = UUID.randomUUID();
        imageId = UUID.randomUUID();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProductImage_withValidIds_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId))
                .andExpect(status().isNoContent());

        verify(productService).deleteProductImage(productId, imageId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProductImage_withInvalidImageId_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId))
                .andExpect(status().isNoContent());

        verify(productService).deleteProductImage(productId, imageId);
    }
}
