package com.ebee.location.service;

import com.ebee.common.security.SecurityService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.location.dto.StockTransferRequest;
import com.ebee.location.entity.Location;
import com.ebee.location.entity.StockTransfer;
import com.ebee.location.repository.StockTransferRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.enums.StockMovementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockTransferServiceTest {

    private final StockTransferRepository repo = mock(StockTransferRepository.class);
    private final LocationService locations = mock(LocationService.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final StockTransferService service =
            new StockTransferService(repo, locations, inventory, products, mock(SecurityService.class));

    private Location main;
    private Location shop;
    private Product widget;
    private StockTransfer stored;

    private static Location location(String code) {
        Location l = new Location(code, code, Location.Type.WAREHOUSE);
        ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
        return l;
    }

    @BeforeEach
    void setUp() {
        main = location("MAIN");
        shop = location("SHOP1");
        widget = Product.builder().id(UUID.randomUUID()).name("Widget").itemCode("W-1").build();
        when(locations.requireActive(main.getId())).thenReturn(main);
        when(locations.requireActive(shop.getId())).thenReturn(shop);
        when(products.findById(widget.getId())).thenReturn(Optional.of(widget));
        when(repo.save(any())).thenAnswer(inv -> {
            stored = inv.getArgument(0);
            return stored;
        });
        when(repo.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(stored));
    }

    private StockTransfer draft(int qty) {
        return service.create(new StockTransferRequest(main.getId(), shop.getId(),
                List.of(new StockTransferRequest.Item(widget.getId(), qty)), "Restock shop"));
    }

    @Test
    void sameSourceAndDestination_isRejected() {
        assertThatThrownBy(() -> service.create(new StockTransferRequest(main.getId(), main.getId(),
                List.of(new StockTransferRequest.Item(widget.getId(), 1)), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dispatch_takesFifoStockFromSource_andRecordsItsCost() {
        draft(10);
        // FIFO: 6 @ 100 + 4 @ 150 = 1,200
        when(inventory.deductStock(eq(main.getId()), eq(StockMovementType.TRANSFER_OUT), anyList(), anyList(), anyList()))
                .thenReturn(List.of(new BigDecimal("1200")));

        StockTransfer t = service.dispatch(UUID.randomUUID());

        assertThat(t.getStatus()).isEqualTo(StockTransfer.Status.IN_TRANSIT);
        assertThat(t.getItems().get(0).getTotalCost()).isEqualByComparingTo("1200");
        assertThat(t.getItems().get(0).getUnitCost()).isEqualByComparingTo("120");
        assertThatThrownBy(() -> service.dispatch(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void receive_addsStockAtDestination_atTheSameCost() {
        draft(10);
        when(inventory.deductStock(any(), any(StockMovementType.class), anyList(), anyList(), anyList()))
                .thenReturn(List.of(new BigDecimal("1200")));
        service.dispatch(UUID.randomUUID());

        StockTransfer t = service.receive(UUID.randomUUID());

        assertThat(t.getStatus()).isEqualTo(StockTransfer.Status.RECEIVED);
        verify(inventory).addStock(eq(shop.getId()), eq(StockMovementType.TRANSFER_IN), eq(List.of(widget.getId())),
                eq(List.of(10)), eq(List.of(new BigDecimal("120.0000"))), anyList());
    }

    @Test
    void cancelInTransit_returnsStockToSource_atItsCost() {
        draft(10);
        when(inventory.deductStock(any(), any(StockMovementType.class), anyList(), anyList(), anyList()))
                .thenReturn(List.of(new BigDecimal("1200")));
        service.dispatch(UUID.randomUUID());

        StockTransfer t = service.cancel(UUID.randomUUID());

        assertThat(t.getStatus()).isEqualTo(StockTransfer.Status.CANCELLED);
        verify(inventory).addStock(eq(main.getId()), eq(StockMovementType.TRANSFER_IN), anyList(), anyList(),
                eq(List.of(new BigDecimal("120.0000"))), anyList());
    }

    @Test
    void cancelDraft_movesNoStock_andReceivedCantBeCancelled() {
        draft(3);
        service.cancel(UUID.randomUUID());
        verify(inventory, never()).addStock(any(), any(), anyList(), anyList(), anyList(), anyList());

        stored.setStatus(StockTransfer.Status.RECEIVED);
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }
}
