package com.ebee.inventory.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.inventory.dto.InventoryResponse;
import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.entity.InventoryLayer;
import com.ebee.inventory.mapper.InventoryMapper;
import com.ebee.inventory.repository.InventoryLayerRepository;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.dto.StockRequest;
import com.ebee.stock.dto.StockResponse;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.stock.mapper.StockMapper;
import com.ebee.stock.repository.StockRepository;
import com.ebee.stock.service.StockMovementRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private InventoryResponse dummyResponse() {
        return InventoryResponse.builder()
                .id(UUID.randomUUID())
                .quantity(0)
                .reservedQuantity(0)
                .build();
    }

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryLayerRepository inventoryLayerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private StockMapper stockMapper;
    @Mock private StockRepository stockRepository;
    @Mock private StockMovementRecorder stockMovementRecorder;
    @Mock private InventoryAlertService inventoryAlertService;

    @InjectMocks private InventoryService inventoryService;

    private Product product;
    private Inventory inventory;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        product = Product.builder().id(productId).name("Test Product").isActive(true).build();
        inventory = Inventory.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(100)
                .reservedQuantity(0)
                .version(0)
                .build();
    }

    @Test
    void createInventory_success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.empty());
        when(inventoryRepository.save(any())).thenReturn(inventory);
        when(inventoryMapper.toResponse(any())).thenReturn(dummyResponse());

        var result = inventoryService.createInventory(productId, 50, 10);

        assertThat(result).isNotNull();
        verify(inventoryAlertService).checkAndNotify(any());
    }

    @Test
    void createInventory_alreadyExists_throws() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.createInventory(productId, 50, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void adjustStock_sufficientStock_succeeds() {
        StockRequest request = new StockRequest();
        request.setProductId(productId);
        request.setQuantityChange(-10);
        request.setMovementType(StockMovementType.OUT);

        InventoryLayer layer = new InventoryLayer(inventory, 100, 100, BigDecimal.TEN, java.time.LocalDateTime.now());

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        when(inventoryLayerRepository.findByInventoryIdAndRemainingQuantityGreaterThanOrderByReceivedAtAsc(
                any(), eq(0))).thenReturn(List.of(layer));
        when(inventoryLayerRepository.saveAll(anyList())).thenReturn(List.of(layer));
        when(inventoryRepository.save(any())).thenReturn(inventory);
        when(inventoryMapper.toResponse(any())).thenReturn(dummyResponse());

        inventoryService.adjustStock(request);

        assertThat(inventory.getQuantity()).isEqualTo(90);
        assertThat(layer.getRemainingQuantity()).isEqualTo(90);
        assertThat(inventory.getAverageCost()).isEqualByComparingTo(BigDecimal.TEN);
        verify(stockMovementRecorder).record(any(), eq(-10), eq(100), eq(90), eq(StockMovementType.OUT), any());
    }

    @Test
    void adjustStock_insufficientStock_throws() {
        StockRequest request = new StockRequest();
        request.setProductId(productId);
        request.setQuantityChange(-200);
        request.setMovementType(StockMovementType.OUT);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void adjustStock_exactlyOutOfStock_throws() {
        inventory.setQuantity(10);
        StockRequest request = new StockRequest();
        request.setProductId(productId);
        request.setQuantityChange(-20);
        request.setMovementType(StockMovementType.OUT);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void adjustStock_productNotFound_throws() {
        StockRequest request = new StockRequest();
        request.setProductId(productId);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inventory not found");
    }

    @Test
    void reserveStock_sufficientAvailable_reserves() {
        inventory.setQuantity(50);
        inventory.setReservedQuantity(10);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenReturn(inventory);
        when(inventoryMapper.toResponse(any())).thenReturn(dummyResponse());

        inventoryService.reserveStock(productId, 20);

        assertThat(inventory.getReservedQuantity()).isEqualTo(30);
        verify(stockMovementRecorder).record(inventory, 0, 50, 50,
                StockMovementType.RESERVE, "Reserved 20 units", 10, 30);
    }

    @Test
    void reserveStock_insufficientAvailable_throws() {
        inventory.setQuantity(50);
        inventory.setReservedQuantity(45);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.reserveStock(productId, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient available stock");
    }

    @Test
    void releaseReservedStock_success_releases() {
        inventory.setQuantity(50);
        inventory.setReservedQuantity(30);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenReturn(inventory);
        when(inventoryMapper.toResponse(any())).thenReturn(dummyResponse());

        inventoryService.releaseReservedStock(productId, 10);

        assertThat(inventory.getReservedQuantity()).isEqualTo(20);
        verify(stockMovementRecorder).record(inventory, 0, 50, 50,
                StockMovementType.RELEASE, "Released 10 units", 30, 20);
    }

    @Test
    void releaseReservedStock_moreThanReserved_throws() {
        inventory.setReservedQuantity(5);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.releaseReservedStock(productId, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot release more than reserved quantity");
    }

    @Test
    void hasSufficientStock_returnsTrue() {
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        assertThat(inventoryService.hasSufficientStock(productId, 50)).isTrue();
    }

    @Test
    void hasSufficientStock_returnsFalse() {
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        assertThat(inventoryService.hasSufficientStock(productId, 150)).isFalse();
    }

    @Test
    void getAvailableStock_returnsDifference() {
        inventory.setQuantity(100);
        inventory.setReservedQuantity(30);
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        assertThat(inventoryService.getAvailableStock(productId)).isEqualTo(70);
    }
}
