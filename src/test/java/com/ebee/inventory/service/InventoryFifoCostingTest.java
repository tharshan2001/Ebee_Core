package com.ebee.inventory.service;

import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.entity.InventoryLayer;
import com.ebee.inventory.mapper.InventoryMapper;
import com.ebee.inventory.repository.InventoryLayerRepository;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.mapper.StockMapper;
import com.ebee.stock.repository.StockRepository;
import com.ebee.stock.service.StockMovementRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FIFO cost, weighted average cost, and return costing on an in-memory set of layers. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryFifoCostingTest {

    private static final java.util.UUID MAIN_LOCATION_ID = java.util.UUID.randomUUID();

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryLayerRepository inventoryLayerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private StockMapper stockMapper;
    @Mock private StockRepository stockRepository;
    @Mock private StockMovementRecorder stockMovementRecorder;
    @Mock private InventoryAlertService inventoryAlertService;
    @org.mockito.Mock private com.ebee.location.service.LocationService locationService;
    @InjectMocks private InventoryService inventoryService;

    private final List<InventoryLayer> layers = new ArrayList<>();
    private Inventory inventory;
    private UUID productId;

    @BeforeEach
    void setUp() {
        // Single default location, like a fresh V127 database.
        com.ebee.location.entity.Location mainLocation = new com.ebee.location.entity.Location("MAIN", "Main",
                com.ebee.location.entity.Location.Type.WAREHOUSE);
        org.springframework.test.util.ReflectionTestUtils.setField(mainLocation, "id", MAIN_LOCATION_ID);
        lenient().when(locationService.defaultLocation()).thenReturn(mainLocation);
        lenient().when(locationService.resolve(any(), any())).thenReturn(mainLocation);
        lenient().when(locationService.require(any())).thenReturn(mainLocation);
        productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).name("Widget").isActive(true).build();
        inventory = Inventory.builder().id(UUID.randomUUID()).product(product)
                .quantity(0).reservedQuantity(0).build();

        when(inventoryRepository.findByProductIdAndLocationId(any(), any())).thenAnswer(inv ->
                java.util.Optional.ofNullable(productId.equals(inv.getArgument(0)) ? inventory : null));
        when(inventoryRepository.findByProductIdInWithLock(any(), any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return ids.contains(productId) ? List.of(inventory) : List.of();
        });
        when(inventoryLayerRepository.findByInventoryIdAndRemainingQuantityGreaterThanOrderByReceivedAtAsc(any(), anyInt()))
                .thenAnswer(inv -> layers.stream().filter(l -> l.getRemainingQuantity() > 0).toList());
        when(inventoryLayerRepository.save(any())).thenAnswer(inv -> {
            layers.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    private void receive(int qty, String unitCost, int minutesAgo) {
        layers.add(new InventoryLayer(inventory, qty, qty, new BigDecimal(unitCost), LocalDateTime.now().minusMinutes(minutesAgo)));
        inventory.setQuantity(inventory.getQuantity() + qty);
    }

    @Test
    void deductStock_returnsActualFifoCostAcrossLayers_andRecalculatesAverage() {
        receive(10, "100.00", 20);
        receive(10, "200.00", 10);

        List<BigDecimal> costs = inventoryService.deductStock(List.of(productId), List.of(15), List.of("order"));

        // 10 × 100 from the oldest layer + 5 × 200 from the next one.
        assertThat(costs).singleElement().satisfies(c -> assertThat(c).isEqualByComparingTo("2000.00"));
        assertThat(inventory.getQuantity()).isEqualTo(5);
        // Only 5 × 200 left on hand → average 200, not the last receipt by accident.
        assertThat(inventory.getAverageCost()).isEqualByComparingTo("200");
    }

    @Test
    void averageCost_isWeightedAcrossRemainingLayers() {
        receive(10, "100.00", 20);
        receive(10, "200.00", 10);

        inventoryService.deductStock(List.of(productId), List.of(5), List.of("order"));

        // Left: 5 × 100 + 10 × 200 = 2,500 over 15 units.
        assertThat(inventory.getAverageCost()).isEqualByComparingTo("166.6667");
    }

    @Test
    void sameProductOnTwoLines_isCheckedAgainstCombinedQuantity() {
        receive(10, "100.00", 10);

        assertThatThrownBy(() -> inventoryService.deductStock(
                List.of(productId, productId), List.of(6, 6), List.of("regular", "inside combo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
        // Rejected before anything was consumed.
        assertThat(layers.get(0).getRemainingQuantity()).isEqualTo(10);
        verify(stockMovementRecorder, never()).record(any(), anyInt(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void returnStock_goesBackAtOriginalCost() {
        receive(10, "100.00", 20);
        inventoryService.deductStock(List.of(productId), List.of(10), List.of("order"));
        receive(10, "300.00", 5); // prices went up since the sale

        inventoryService.returnStock(List.of(productId), List.of(2), List.of(new BigDecimal("100.00")), List.of("return"));

        InventoryLayer returned = layers.get(layers.size() - 1);
        assertThat(returned.getUnitCost()).isEqualByComparingTo("100.00");
        assertThat(returned.getRemainingQuantity()).isEqualTo(2);
        // 10 × 300 + 2 × 100 = 3,200 over 12.
        assertThat(inventory.getAverageCost()).isEqualByComparingTo("266.6667");
    }

    @Test
    void returnStock_unknownCost_usesCurrentAverage() {
        receive(10, "100.00", 20);
        receive(10, "200.00", 10);
        inventoryService.deductStock(List.of(productId), List.of(5), List.of("order")); // avg now 166.6667

        inventoryService.returnStock(List.of(productId), List.of(1), Arrays.asList((BigDecimal) null), List.of("combo return"));

        assertThat(layers.get(layers.size() - 1).getUnitCost()).isEqualByComparingTo("166.6667");
    }

    @Test
    void stockRowsAreLockedForUpdate() {
        receive(5, "100.00", 10);

        inventoryService.deductStock(List.of(productId), List.of(1), List.of("order"));

        verify(inventoryRepository).findByProductIdInWithLock(any(), eq(MAIN_LOCATION_ID));
        verify(inventoryRepository, never()).findByProductIdInAndLocationId(any(), any());
    }
}
