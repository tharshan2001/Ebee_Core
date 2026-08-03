package com.ebee.combo.service;

import com.ebee.combo.dto.ComboItemRequest;
import com.ebee.combo.dto.ComboRequest;
import com.ebee.combo.dto.ComboRuleRequest;
import com.ebee.combo.dto.ComboResponse;
import com.ebee.combo.entity.Combo;
import com.ebee.combo.entity.ComboItem;
import com.ebee.combo.entity.ComboRule;
import com.ebee.combo.enums.ComboRuleType;
import com.ebee.combo.exception.ComboImmutabilityException;
import com.ebee.combo.mapper.ComboMapper;
import com.ebee.combo.repository.ComboRepository;
import com.ebee.common.enums.DiscountType;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.order.repository.OrderItemRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComboServiceTest {

    @Mock private ComboRepository comboRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ComboMapper comboMapper;

    @InjectMocks private ComboServiceImpl comboService;

    @Captor private ArgumentCaptor<Combo> comboCaptor;

    private UUID productId1;
    private UUID productId2;
    private UUID freeProductId;
    private Product product1;
    private Product product2;
    private Product freeProduct;
    private Combo combo;
    private ComboItem comboItem1;
    private ComboItem comboItem2;

    @BeforeEach
    void setUp() {
        productId1 = UUID.randomUUID();
        productId2 = UUID.randomUUID();
        freeProductId = UUID.randomUUID();

        product1 = Product.builder()
                .id(productId1)
                .itemCode("PROD-001")
                .name("Shampoo")
                .price(new BigDecimal("500.00"))
                .isActive(true)
                .build();

        product2 = Product.builder()
                .id(productId2)
                .itemCode("PROD-002")
                .name("Conditioner")
                .price(new BigDecimal("750.00"))
                .isActive(true)
                .build();

        freeProduct = Product.builder()
                .id(freeProductId)
                .itemCode("FREE-001")
                .name("Free Brush")
                .price(new BigDecimal("300.00"))
                .isActive(true)
                .build();

        comboItem1 = ComboItem.builder()
                .id(UUID.randomUUID())
                .product(product1)
                .quantity(2)
                .build();

        comboItem2 = ComboItem.builder()
                .id(UUID.randomUUID())
                .product(product2)
                .quantity(1)
                .build();

        combo = Combo.builder()
                .id(UUID.randomUUID())
                .name("Hair Care Bundle")
                .description("Shampoo + Conditioner")
                .isActive(true)
                .items(new ArrayList<>(List.of(comboItem1, comboItem2)))
                .build();

        lenient().when(comboRepository.save(any(Combo.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createCombo_withValidRequest_savesAndReturnsResponse() {
        ComboRequest request = buildComboRequest(false, false);
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1, product2));
        when(comboMapper.toResponse(any())).thenReturn(new ComboResponse());

        ComboResponse result = comboService.createCombo(request);

        verify(comboRepository).save(comboCaptor.capture());
        Combo saved = comboCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("Hair Care Bundle");
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getRule()).isNull();
        assertThat(result).isNotNull();
    }

    @Test
    void createCombo_withFreeProductRule_savesRuleWithFreeProduct() {
        ComboRequest request = buildComboRequest(true, false);
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1, product2, freeProduct));
        when(comboMapper.toResponse(any())).thenReturn(new ComboResponse());

        comboService.createCombo(request);

        verify(comboRepository).save(comboCaptor.capture());
        Combo saved = comboCaptor.getValue();
        assertThat(saved.getRule()).isNotNull();
        assertThat(saved.getRule().getRuleType()).isEqualTo(ComboRuleType.FREE_PRODUCT);
        assertThat(saved.getRule().getFreeProduct()).isEqualTo(freeProduct);
    }

    @Test
    void createCombo_withDiscountRule_savesRuleWithDiscount() {
        ComboRequest request = buildComboRequest(false, true);
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1, product2));
        when(comboMapper.toResponse(any())).thenReturn(new ComboResponse());

        comboService.createCombo(request);

        verify(comboRepository).save(comboCaptor.capture());
        Combo saved = comboCaptor.getValue();
        assertThat(saved.getRule()).isNotNull();
        assertThat(saved.getRule().getRuleType()).isEqualTo(ComboRuleType.DISCOUNT);
        assertThat(saved.getRule().getDiscountType()).isEqualTo("FIXED");
        assertThat(saved.getRule().getDiscountValue()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void createCombo_withInactiveProduct_throws() {
        Product inactive = Product.builder()
                .id(UUID.randomUUID()).name("Dead").isActive(false).build();
        ComboRequest request = new ComboRequest();
        request.setName("Bad Combo");
        ComboItemRequest item = new ComboItemRequest();
        item.setProductId(inactive.getId());
        item.setQuantity(1);
        request.setItems(List.of(item));

        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(inactive));

        assertThatThrownBy(() -> comboService.createCombo(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void createCombo_withNonexistentProduct_throws() {
        ComboRequest request = new ComboRequest();
        request.setName("Bad Combo");
        ComboItemRequest item = new ComboItemRequest();
        item.setProductId(UUID.randomUUID());
        item.setQuantity(1);
        request.setItems(List.of(item));

        when(productRepository.findAllById(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> comboService.createCombo(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCombo_freeProductRuleWithoutProductId_throws() {
        ComboRequest request = new ComboRequest();
        request.setName("Bad Rule");
        ComboItemRequest item = new ComboItemRequest();
        item.setProductId(productId1);
        item.setQuantity(1);
        request.setItems(List.of(item));

        ComboRuleRequest rule = new ComboRuleRequest();
        rule.setRuleType(ComboRuleType.FREE_PRODUCT);
        rule.setFreeProductId(null);
        request.setRule(rule);

        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1));

        assertThatThrownBy(() -> comboService.createCombo(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("freeProductId");
    }

    @Test
    void createCombo_discountRuleWithoutType_throws() {
        ComboRequest request = new ComboRequest();
        request.setName("Bad Discount");
        ComboItemRequest item = new ComboItemRequest();
        item.setProductId(productId1);
        item.setQuantity(1);
        request.setItems(List.of(item));

        ComboRuleRequest rule = new ComboRuleRequest();
        rule.setRuleType(ComboRuleType.DISCOUNT);
        rule.setDiscountType(null);
        rule.setDiscountValue(null);
        request.setRule(rule);

        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1));

        assertThatThrownBy(() -> comboService.createCombo(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountType and discountValue");
    }

    @Test
    void createCombo_percentageDiscountOver100_throws() {
        ComboRequest request = new ComboRequest();
        request.setName("Over 100");
        ComboItemRequest item = new ComboItemRequest();
        item.setProductId(productId1);
        item.setQuantity(1);
        request.setItems(List.of(item));

        ComboRuleRequest rule = new ComboRuleRequest();
        rule.setRuleType(ComboRuleType.DISCOUNT);
        rule.setDiscountType(DiscountType.PERCENTAGE);
        rule.setDiscountValue(new BigDecimal("150"));
        request.setRule(rule);

        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1));

        assertThatThrownBy(() -> comboService.createCombo(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void updateCombo_noExistingOrders_updatesInPlace() {
        UUID comboId = combo.getId();
        when(comboRepository.findById(comboId)).thenReturn(Optional.of(combo));
        when(orderItemRepository.existsByComboId(comboId)).thenReturn(false);
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1, product2));
        when(comboMapper.toResponse(any())).thenReturn(new ComboResponse());

        ComboRequest request = buildComboRequest(false, false);
        request.setName("Updated Bundle");

        comboService.updateCombo(comboId, request);

        verify(comboRepository, times(1)).save(any());
        assertThat(combo.getName()).isEqualTo("Updated Bundle");
    }

    @Test
    void updateCombo_hasOrders_clonesAndDeactivates() {
        UUID comboId = combo.getId();
        when(comboRepository.findById(comboId)).thenReturn(Optional.of(combo));
        when(orderItemRepository.existsByComboId(comboId)).thenReturn(true);
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1, product2));
        when(comboMapper.toResponse(any())).thenReturn(new ComboResponse());

        ComboRequest request = buildComboRequest(false, false);
        request.setName("Cloned Bundle");

        ComboResponse result = comboService.updateCombo(comboId, request);

        verify(comboRepository, times(2)).save(comboCaptor.capture());
        List<Combo> savedCombos = comboCaptor.getAllValues();
        assertThat(savedCombos.get(0).getIsActive()).isFalse();
        assertThat(savedCombos.get(1).getName()).isEqualTo("Cloned Bundle");
        assertThat(savedCombos.get(1).getIsActive()).isTrue();
        assertThat(result).isNotNull();
    }

    @Test
    void deleteCombo_noOrders_deactivates() {
        UUID comboId = combo.getId();
        when(comboRepository.findById(comboId)).thenReturn(Optional.of(combo));
        when(orderItemRepository.existsByComboId(comboId)).thenReturn(false);

        comboService.deleteCombo(comboId);

        verify(comboRepository).save(comboCaptor.capture());
        assertThat(comboCaptor.getValue().getIsActive()).isFalse();
    }

    @Test
    void deleteCombo_hasOrders_throwsImmutabilityException() {
        UUID comboId = combo.getId();
        when(comboRepository.findById(comboId)).thenReturn(Optional.of(combo));
        when(orderItemRepository.existsByComboId(comboId)).thenReturn(true);

        assertThatThrownBy(() -> comboService.deleteCombo(comboId))
                .isInstanceOf(ComboImmutabilityException.class)
                .hasMessageContaining("has been used in orders");
    }

    @Test
    void deleteCombo_notFound_throws() {
        when(comboRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comboService.deleteCombo(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getComboById_found_returnsResponse() {
        UUID comboId = combo.getId();
        when(comboRepository.findByIdWithDetails(comboId)).thenReturn(Optional.of(combo));
        when(comboMapper.toResponse(combo)).thenReturn(new ComboResponse());

        var result = comboService.getComboById(comboId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
        verify(comboRepository).findByIdWithDetails(comboId);
    }

    @Test
    void getComboById_notFound_returnsFail() {
        when(comboRepository.findByIdWithDetails(any())).thenReturn(Optional.empty());

        var result = comboService.getComboById(UUID.randomUUID());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Combo");
    }

    @Test
    void listActiveCombos_returnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Combo> page = new PageImpl<>(List.of(combo), pageable, 1);
        when(comboRepository.findAllByIsActiveTrue(pageable)).thenReturn(page);
        when(comboMapper.toResponse(any())).thenReturn(new ComboResponse());

        Page<ComboResponse> result = comboService.listActiveCombos(pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(comboRepository).findAllByIsActiveTrue(pageable);
    }

    @Test
    void listAllCombos_returnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Combo> page = new PageImpl<>(List.of(combo), pageable, 1);
        when(comboRepository.findAll(pageable)).thenReturn(page);
        when(comboMapper.toResponse(any())).thenReturn(new ComboResponse());

        Page<ComboResponse> result = comboService.listAllCombos(pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(comboRepository).findAll(pageable);
    }

    @Test
    void createCombo_negativeDiscountValue_throws() {
        ComboRequest request = new ComboRequest();
        request.setName("Negative");
        ComboItemRequest item = new ComboItemRequest();
        item.setProductId(productId1);
        item.setQuantity(1);
        request.setItems(List.of(item));

        ComboRuleRequest rule = new ComboRuleRequest();
        rule.setRuleType(ComboRuleType.DISCOUNT);
        rule.setDiscountType(DiscountType.FIXED);
        rule.setDiscountValue(new BigDecimal("-50"));
        request.setRule(rule);

        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1));

        assertThatThrownBy(() -> comboService.createCombo(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void createCombo_freeProductNotInCatalog_throws() {
        ComboRequest request = new ComboRequest();
        request.setName("Ghost Free");
        ComboItemRequest item = new ComboItemRequest();
        item.setProductId(productId1);
        item.setQuantity(1);
        request.setItems(List.of(item));

        ComboRuleRequest rule = new ComboRuleRequest();
        rule.setRuleType(ComboRuleType.FREE_PRODUCT);
        rule.setFreeProductId(UUID.randomUUID());
        request.setRule(rule);

        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(product1));

        assertThatThrownBy(() -> comboService.createCombo(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private ComboRequest buildComboRequest(boolean withFreeProductRule, boolean withDiscountRule) {
        ComboRequest request = new ComboRequest();
        request.setName("Hair Care Bundle");
        request.setDescription("Shampoo + Conditioner");

        ComboItemRequest item1 = new ComboItemRequest();
        item1.setProductId(productId1);
        item1.setQuantity(2);

        ComboItemRequest item2 = new ComboItemRequest();
        item2.setProductId(productId2);
        item2.setQuantity(1);

        request.setItems(List.of(item1, item2));

        if (withFreeProductRule) {
            ComboRuleRequest rule = new ComboRuleRequest();
            rule.setRuleType(ComboRuleType.FREE_PRODUCT);
            rule.setFreeProductId(freeProductId);
            request.setRule(rule);
        } else if (withDiscountRule) {
            ComboRuleRequest rule = new ComboRuleRequest();
            rule.setRuleType(ComboRuleType.DISCOUNT);
            rule.setDiscountType(DiscountType.FIXED);
            rule.setDiscountValue(new BigDecimal("200.00"));
            request.setRule(rule);
        }

        return request;
    }
}
