package com.ebee.product.service;

import com.ebee.category.service.CategoryService;
import com.ebee.common.port.StoragePort;
import com.ebee.common.service.PrintTemplateService;
import com.ebee.inventory.mapper.InventoryMapper;
import com.ebee.inventory.service.InventoryService;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.dto.ProductResponse;
import com.ebee.product.entity.Product;
import com.ebee.product.mapper.ProductMapper;
import com.ebee.product.repository.ProductImageRepository;
import com.ebee.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper productMapper;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private StoragePort storagePort;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private InventoryService inventoryService;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private EntityManager entityManager;
    @Mock private CategoryService categoryService;
    @Mock private PrintTemplateService printTemplateService;

    @InjectMocks private ProductServiceImpl productService;

    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        product = Product.builder()
                .id(productId)
                .name("Discounted Product")
                .itemCode("DISC-001")
                .category("Electronics")
                .price(new BigDecimal("1000.00"))
                .costPrice(new BigDecimal("500.00"))
                .discountType("FIXED")
                .discountValue(new BigDecimal("100.00"))
                .maxDiscountType("FIXED")
                .maxDiscountValue(new BigDecimal("200.00"))
                .build();

        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productMapper.toResponse(any(Product.class))).thenReturn(new ProductResponse());
    }

    @Test
    void updateProduct_partialMaxDiscountUpdate_keepsExistingCatalogDiscount() {
        ProductRequest request = new ProductRequest();
        request.setMaxDiscountType(com.ebee.common.enums.DiscountType.FIXED);
        request.setMaxDiscountValue(new BigDecimal("300.00"));
        request.setMaxDiscountFieldsSet(true);

        productService.updateProduct(productId, request);

        assertThat(product.getDiscountType()).isEqualTo("FIXED");
        assertThat(product.getDiscountValue()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(product.getMaxDiscountType()).isEqualTo("FIXED");
        assertThat(product.getMaxDiscountValue()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void updateProduct_partialCatalogDiscountUpdate_keepsExistingMaxDiscount() {
        ProductRequest request = new ProductRequest();
        request.setDiscountType(com.ebee.common.enums.DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("10.00"));
        request.setDiscountFieldsSet(true);

        productService.updateProduct(productId, request);

        assertThat(product.getDiscountType()).isEqualTo("PERCENTAGE");
        assertThat(product.getDiscountValue()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(product.getMaxDiscountType()).isEqualTo("FIXED");
        assertThat(product.getMaxDiscountValue()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void updateProduct_noDiscountFields_requestLeavesDiscountUntouched() {
        ProductRequest request = new ProductRequest();
        request.setName("Renamed Product");

        productService.updateProduct(productId, request);

        assertThat(product.getDiscountType()).isEqualTo("FIXED");
        assertThat(product.getDiscountValue()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(product.getMaxDiscountType()).isEqualTo("FIXED");
        assertThat(product.getMaxDiscountValue()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void updateProduct_bothDiscountFieldsProvided_replacesDiscount() {
        ProductRequest request = new ProductRequest();
        request.setDiscountType(com.ebee.common.enums.DiscountType.FIXED);
        request.setDiscountValue(new BigDecimal("150.00"));
        request.setDiscountFieldsSet(true);
        request.setMaxDiscountType(com.ebee.common.enums.DiscountType.FIXED);
        request.setMaxDiscountValue(new BigDecimal("250.00"));
        request.setMaxDiscountFieldsSet(true);

        productService.updateProduct(productId, request);

        assertThat(product.getDiscountType()).isEqualTo("FIXED");
        assertThat(product.getDiscountValue()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(product.getMaxDiscountType()).isEqualTo("FIXED");
        assertThat(product.getMaxDiscountValue()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void updateProduct_emptyDiscountFields_clearsCatalogDiscount() {
        ProductRequest request = new ProductRequest();
        request.setDiscountFieldsSet(true);

        productService.updateProduct(productId, request);

        assertThat(product.getDiscountType()).isNull();
        assertThat(product.getDiscountValue()).isNull();
        assertThat(product.getMaxDiscountType()).isEqualTo("FIXED");
        assertThat(product.getMaxDiscountValue()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
