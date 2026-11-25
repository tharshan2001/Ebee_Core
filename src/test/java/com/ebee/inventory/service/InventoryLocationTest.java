package com.ebee.inventory.service;

import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.mapper.InventoryMapper;
import com.ebee.inventory.repository.InventoryLayerRepository;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.location.entity.Location;
import com.ebee.location.service.LocationService;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.mapper.StockMapper;
import com.ebee.stock.repository.StockRepository;
import com.ebee.stock.service.StockMovementRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryLocationTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryLayerRepository inventoryLayerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private StockMapper stockMapper;
    @Mock private StockRepository stockRepository;
    @Mock private StockMovementRecorder stockMovementRecorder;
    @Mock private InventoryAlertService inventoryAlertService;
    @Mock private LocationService locationService;
    @InjectMocks private InventoryService inventoryService;

    private Location location(String code) {
        Location l = new Location(code, code, Location.Type.SHOP);
        ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
        when(locationService.require(l.getId())).thenReturn(l);
        return l;
    }

    @Test
    void receivingAtANewLocation_createsAStockRowThere() {
        Location shop = location("SHOP1");
        Product widget = Product.builder().id(UUID.randomUUID()).name("Widget").build();
        when(productRepository.findById(widget.getId())).thenReturn(Optional.of(widget));
        when(inventoryRepository.findByProductIdAndLocationId(widget.getId(), shop.getId())).thenReturn(Optional.empty());
        List<Inventory> saved = new ArrayList<>();
        when(inventoryRepository.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(inventoryRepository.findByProductIdInWithLock(any(), eq(shop.getId()))).thenAnswer(inv -> saved);

        inventoryService.recordPurchaseStocks(shop.getId(), List.of(widget.getId()), List.of(12),
                List.of(new BigDecimal("50.00")), List.of("PO-1"));

        ArgumentCaptor<Inventory> row = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(row.capture());
        assertThat(row.getValue().getLocation()).isSameAs(shop);
        assertThat(row.getValue().getQuantity()).isEqualTo(12);
    }

    @Test
    void sellingFromALocationWithoutTheProduct_isRejected_withoutCreatingARow() {
        Location shop = location("SHOP2");
        UUID productId = UUID.randomUUID();
        when(inventoryRepository.findByProductIdInWithLock(any(), eq(shop.getId()))).thenReturn(List.of());

        assertThatThrownBy(() -> inventoryService.deductStock(shop.getId(), List.of(productId), List.of(1), List.of("order")))
                .hasMessageContaining("Inventory");
        verify(inventoryRepository, never()).save(any());
    }
}
