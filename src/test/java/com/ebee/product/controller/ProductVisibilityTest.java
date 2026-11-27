package com.ebee.product.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.inventory.dto.InventoryResponse;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.dto.ProductSearchResponse;
import com.ebee.product.service.ProductService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cost prices and stock levels used to be readable by anyone, without signing in. */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class ProductVisibilityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProductService productService;
    @MockBean private Validator validator;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private RateLimitingService rateLimitingService;
    @MockBean private IpWhitelistService ipWhitelistService;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
        when(ipWhitelistService.isAllowed(any(), anyString())).thenReturn(true);
        when(productService.getProductById(id)).thenAnswer(call -> product());
        when(productService.getAllProductsPaginated(any(), any(), any()))
                .thenAnswer(call -> new PageImpl<>(List.of(product())));
        when(productService.searchProductsAdvanced(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(call -> new ProductSearchResponse(List.of(product()), false, 1, 1));
    }

    private ProductResponse product() {
        ProductResponse p = new ProductResponse();
        p.setId(id);
        p.setName("Kettle");
        p.setPrice(new BigDecimal("4500"));
        p.setCostPrice(new BigDecimal("3000"));
        p.setTotalSold(12L);
        p.setTaxRateId(UUID.randomUUID());
        p.setMaxDiscountType("PERCENT");
        p.setMaxDiscountValue(new BigDecimal("10"));
        p.setInventory(InventoryResponse.builder()
                .quantity(9).reservedQuantity(2).availableQuantity(7).reorderLevel(3)
                .averageCost(new BigDecimal("2900")).build());
        return p;
    }

    private void assertPublicView(String url, String at) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath(at + ".price").value(4500))
                .andExpect(jsonPath(at + ".inStock").value(true))
                .andExpect(jsonPath(at + ".costPrice").doesNotExist())
                .andExpect(jsonPath(at + ".inventory").doesNotExist())
                .andExpect(jsonPath(at + ".maxDiscountValue").doesNotExist())
                .andExpect(jsonPath(at + ".totalSold").doesNotExist())
                .andExpect(jsonPath(at + ".taxRateId").doesNotExist());
    }

    @Test
    void visitorsSeeNoCostOrStockLevels() throws Exception {
        assertPublicView("/api/products/" + id, "$.data");
        assertPublicView("/api/products", "$.data.content[0]");
        assertPublicView("/api/products/search/advanced", "$.data.products[0]");
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentsSeeStockAndDiscountLimitsButNoCost() throws Exception {
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inventory.availableQuantity").value(7))
                .andExpect(jsonPath("$.data.maxDiscountValue").value(10))
                .andExpect(jsonPath("$.data.costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.inventory.averageCost").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminsSeeEverything() throws Exception {
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costPrice").value(3000))
                .andExpect(jsonPath("$.data.inventory.averageCost").value(2900))
                .andExpect(jsonPath("$.data.totalSold").value(12));
    }

    @Test
    void aStaleTokenIsRefusedSoTheClientRefreshesIt() throws Exception {
        when(jwtService.extractEmail("old")).thenThrow(new RuntimeException("expired"));

        mockMvc.perform(get("/api/products/{id}", id).header("Authorization", "Bearer old"))
                .andExpect(status().isUnauthorized());
    }
}
