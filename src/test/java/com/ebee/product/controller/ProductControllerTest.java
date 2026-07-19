package com.ebee.product.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.product.dto.ProductImageResponse;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private ProductResponse productResponse;
    private MockMultipartFile imageFile;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
        when(jwtService.extractEmail(anyString())).thenReturn("admin@ebee.com");
        when(userDetailsService.loadUserByUsername("admin@ebee.com")).thenReturn(null);
        when(ipWhitelistService.isAllowed(any(), anyString())).thenReturn(true);

        productId = UUID.randomUUID();
        imageId = UUID.randomUUID();

        imageFile = new MockMultipartFile(
                "images", "test.jpg", "image/jpeg", "fake-image-content".getBytes());

        ProductImageResponse imageResponse = new ProductImageResponse();
        imageResponse.setId(imageId);
        imageResponse.setImageUrl("https://minio.example.com/ebee/products/" + productId + "/test.jpg");
        imageResponse.setImageName("test.jpg");
        imageResponse.setImageType("image/jpeg");
        imageResponse.setSortOrder(0);

        productResponse = new ProductResponse();
        productResponse.setId(productId);
        productResponse.setName("Test Product");
        productResponse.setItemCode("TP-001");
        productResponse.setPrice(new BigDecimal("99.99"));
        productResponse.setCategory("Electronics");
        productResponse.setIsActive(true);
        productResponse.setImages(List.of(imageResponse));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_withImage_returns201() throws Exception {
        when(productService.createProduct(any())).thenReturn(productResponse);

        mockMvc.perform(multipart("/api/products")
                        .file(imageFile)
                        .param("name", "Test Product")
                        .param("itemCode", "TP-001")
                        .param("category", "Electronics")
                        .param("price", "99.99"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Product"))
                .andExpect(jsonPath("$.data.images[0].imageName").value("test.jpg"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_withoutImage_returns201() throws Exception {
        productResponse.setImages(List.of());
        when(productService.createProduct(any())).thenReturn(productResponse);

        mockMvc.perform(multipart("/api/products")
                        .param("name", "Test Product")
                        .param("itemCode", "TP-001")
                        .param("category", "Electronics")
                        .param("price", "99.99"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateProduct_withImageAndRemoveUrls_returns200() throws Exception {
        when(productService.updateProduct(eq(productId), any())).thenReturn(productResponse);

        String removeUrl = "https://minio.example.com/ebee/products/" + productId + "/old.jpg";

        mockMvc.perform(multipart("/api/products/{id}", productId)
                        .file(imageFile)
                        .param("name", "Test Product")
                        .param("itemCode", "TP-001")
                        .param("category", "Electronics")
                        .param("price", "99.99")
                        .param("removeImageUrls", removeUrl)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images[0].imageName").value("test.jpg"));

        verify(productService).updateProduct(eq(productId), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadProductImages_returns200() throws Exception {
        when(productService.addImagesToProduct(eq(productId), any())).thenReturn(productResponse);

        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images[0].imageName").value("test.jpg"));

        verify(productService).addImagesToProduct(eq(productId), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadProductImages_withMultipleImages_returns200() throws Exception {
        MockMultipartFile image2 = new MockMultipartFile(
                "images", "photo.png", "image/png", "fake-png-content".getBytes());

        ProductImageResponse imageResponse2 = new ProductImageResponse();
        imageResponse2.setId(UUID.randomUUID());
        imageResponse2.setImageUrl("https://minio.example.com/ebee/products/" + productId + "/photo.png");
        imageResponse2.setImageName("photo.png");
        imageResponse2.setImageType("image/png");
        imageResponse2.setSortOrder(1);

        productResponse.setImages(List.of(
                productResponse.getImages().getFirst(), imageResponse2));

        when(productService.addImagesToProduct(eq(productId), any())).thenReturn(productResponse);

        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(imageFile)
                        .file(image2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images.length()").value(2));
    }

    @Test
    void uploadProductImages_anonymous_returns401() throws Exception {
        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(imageFile))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void uploadProductImages_agentRole_returns403() throws Exception {
        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(imageFile))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProductImage_withValidIds_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId))
                .andExpect(status().isNoContent());

        verify(productService).deleteProductImage(productId, imageId);
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void deleteProductImage_agentRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProductImage_anonymous_returns401() throws Exception {
        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_anonymous_returns401() throws Exception {
        mockMvc.perform(multipart("/api/products")
                        .file(imageFile)
                        .param("name", "Test Product")
                        .param("itemCode", "TP-001")
                        .param("category", "Electronics")
                        .param("price", "99.99"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createProduct_agentRole_returns403() throws Exception {
        mockMvc.perform(multipart("/api/products")
                        .file(imageFile)
                        .param("name", "Test Product")
                        .param("itemCode", "TP-001")
                        .param("category", "Electronics")
                        .param("price", "99.99"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_anonymous_returns401() throws Exception {
        mockMvc.perform(multipart("/api/products/{id}", productId)
                        .file(imageFile)
                        .param("name", "Test Product")
                        .param("itemCode", "TP-001")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
    }
}
