package com.ebee.inventory.service;

import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.repository.InventoryLayerRepository;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.location.service.LocationService;
import com.ebee.product.entity.Product;
import com.ebee.support.RealDatabaseTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stock reservations and sales racing for the same units, against the real database. Not
 * {@code @Transactional}: each call commits for real (that's what's being tested), so the test
 * deletes its rows afterwards.
 */
class InventoryConcurrencyIntegrationTest extends RealDatabaseTest {

    @Autowired private InventoryService inventory;
    @Autowired private InventoryRepository inventories;
    @Autowired private InventoryLayerRepository layers;
    @Autowired private LocationService locations;
    @Autowired private JdbcTemplate jdbc;

    private final List<UUID> productIds = new ArrayList<>();

    private Product stocked(int units, String unitCost) {
        Product p = product("150.00");
        productIds.add(p.getId());
        inventory.recordPurchaseStocks(null, List.of(p.getId()), List.of(units), List.of(new BigDecimal(unitCost)),
                List.of("Test stock"));
        return p;
    }

    private Inventory stockOf(Product p) {
        return inventories.findByProductIdAndLocationId(p.getId(), locations.defaultLocation().getId()).orElseThrow();
    }

    /** Runs the tasks at the same instant on separate threads; returns how many succeeded. */
    private int race(List<Callable<?>> tasks) throws Exception {
        SecurityContext auth = SecurityContextHolder.getContext();
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Callable<?> task : tasks) {
                results.add(pool.submit(() -> {
                    SecurityContextHolder.setContext(auth);
                    start.await();
                    try {
                        task.call();
                        return true;
                    } catch (IllegalArgumentException | IllegalStateException expected) {
                        return false; // "Out of stock" / "Insufficient stock" for the loser
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<Boolean> r : results) {
                if (r.get(30, TimeUnit.SECONDS)) {
                    ok++;
                }
            }
            return ok;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoReservationsForTheLastUnit_exactlyOneWins() throws Exception {
        Product p = stocked(1, "80.00");

        int won = race(List.of(() -> inventory.reserveStock(p.getId(), 1), () -> inventory.reserveStock(p.getId(), 1)));

        assertThat(won).isEqualTo(1);
        Inventory inv = stockOf(p);
        assertThat(inv.getQuantity()).isEqualTo(1);
        assertThat(inv.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void twoSalesOfTheLastUnit_exactlyOneWins_andStockNeverGoesNegative() throws Exception {
        Product p = stocked(1, "80.00");

        int won = race(List.of(
                () -> inventory.deductStock(List.of(p.getId()), List.of(1), List.of("Order A")),
                () -> inventory.deductStock(List.of(p.getId()), List.of(1), List.of("Order B"))));

        assertThat(won).isEqualTo(1);
        Inventory inv = stockOf(p);
        assertThat(inv.getQuantity()).isZero();
        assertThat(layers.findByInventoryIdOrderByReceivedAtAsc(inv.getId()))
                .allSatisfy(l -> assertThat(l.getRemainingQuantity()).isZero());
    }

    @Test
    void manySmallSalesAtOnce_sellExactlyWhatExists() throws Exception {
        Product p = stocked(5, "80.00");
        List<Callable<?>> sales = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int n = i;
            sales.add(() -> inventory.deductStock(List.of(p.getId()), List.of(1), List.of("Order " + n)));
        }

        int won = race(sales);

        assertThat(won).isEqualTo(5);
        assertThat(stockOf(p).getQuantity()).isZero();
    }

    @Test
    void reserveConfirmAndRelease_moveTheRightCounters_atFifoCost() {
        Product p = stocked(3, "80.00");
        inventory.recordPurchaseStocks(null, List.of(p.getId()), List.of(2), List.of(new BigDecimal("110.00")),
                List.of("Second batch"));

        inventory.reserveStock(p.getId(), 4);
        inventory.confirmOrderStock(p.getId(), 4);   // takes 3 @80, then 1 @110
        Inventory inv = stockOf(p);
        assertThat(inv.getQuantity()).isEqualTo(1);
        assertThat(inv.getReservedQuantity()).isZero();
        assertThat(inv.getAverageCost()).isEqualByComparingTo("110.00");

        inventory.reserveStock(p.getId(), 1);
        assertThatThrownBy(() -> inventory.releaseReservedStock(p.getId(), 2))
                .hasMessageContaining("Cannot release more than reserved");
        inventory.releaseReservedStock(p.getId(), 1);
        assertThat(stockOf(p).getReservedQuantity()).isZero();
        assertThatThrownBy(() -> inventory.reserveStock(p.getId(), 2)).hasMessageContaining("Available: 1");
    }

    @AfterEach
    void deleteCommittedRows() {
        for (UUID pid : productIds) {
            jdbc.update("DELETE FROM stock WHERE inventory_id IN (SELECT id FROM inventory WHERE product_id = ?)", pid);
            jdbc.update("DELETE FROM inventory_layers WHERE inventory_id IN (SELECT id FROM inventory WHERE product_id = ?)", pid);
            jdbc.update("DELETE FROM inventory WHERE product_id = ?", pid);
            jdbc.update("DELETE FROM products WHERE id = ?", pid);
        }
        jdbc.update("DELETE FROM alerts WHERE recipient_id = ?", admin.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", admin.getId());
    }
}
