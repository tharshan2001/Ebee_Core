package com.ebee.purchase.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.entity.InventoryLayer;
import com.ebee.inventory.repository.InventoryLayerRepository;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.location.service.LocationService;
import com.ebee.product.entity.Product;
import com.ebee.purchase.dto.PurchaseOrderItemRequest;
import com.ebee.purchase.dto.PurchaseOrderRequest;
import com.ebee.purchase.dto.PurchaseOrderResponse;
import com.ebee.purchase.dto.PurchaseOrderStatusRequest;
import com.ebee.purchase.dto.ReceiveOrderRequest;
import com.ebee.purchase.enums.PurchaseOrderStatus;
import com.ebee.purchase.enums.PurchasePaymentMethod;
import com.ebee.supplier.entity.Supplier;
import com.ebee.supplier.repository.SupplierRepository;
import com.ebee.support.RealDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Receiving purchase orders against the real schema: cost batches, stock, supplier ledger. */
@Transactional
class PurchaseOrderReceivingIntegrationTest extends RealDatabaseTest {

    @Autowired private PurchaseOrderService purchaseOrders;
    @Autowired private SupplierRepository suppliers;
    @Autowired private LedgerService ledger;
    @Autowired private InventoryRepository inventories;
    @Autowired private InventoryLayerRepository layers;
    @Autowired private LocationService locations;

    private Supplier supplier;
    private Product oil;

    @BeforeEach
    void setUpData() {
        Supplier s = new Supplier();
        s.setName("Supplier " + suffix);
        s.setPhone("0112345678");
        s.setCountry("Sri Lanka");
        supplier = suppliers.save(s);
        oil = product("150.00");
    }

    private PurchaseOrderResponse approvedPo(int qty, String unitCost, PurchasePaymentMethod method, String paid) {
        PurchaseOrderResponse po = purchaseOrders.createOrder(new PurchaseOrderRequest(supplier.getId(), "test",
                List.of(new PurchaseOrderItemRequest(oil.getId(), qty, new BigDecimal(unitCost), null)),
                method, paid == null ? null : new BigDecimal(paid)), admin.getId());
        if (po.status() == PurchaseOrderStatus.DRAFT) {
            po = purchaseOrders.updateStatus(po.id(), new PurchaseOrderStatusRequest(PurchaseOrderStatus.PENDING));
        }
        if (po.status() == PurchaseOrderStatus.PENDING) {
            po = purchaseOrders.updateStatus(po.id(), new PurchaseOrderStatusRequest(PurchaseOrderStatus.APPROVED));
        }
        return po;
    }

    private Inventory stock() {
        return inventories.findByProductIdAndLocationId(oil.getId(), locations.defaultLocation().getId()).orElseThrow();
    }

    @Test
    void partialThenFullReceipt_createsCostBatches_stock_andSupplierDebt() {
        PurchaseOrderResponse po = approvedPo(10, "100.00", PurchasePaymentMethod.CREDIT, null);
        var itemId = po.items().get(0).id();

        PurchaseOrderResponse afterFirst = purchaseOrders.receiveOrder(po.id(), new ReceiveOrderRequest(List.of(
                new ReceiveOrderRequest.ItemReceipt(itemId, 4, "LOT-A", LocalDate.now().plusMonths(6), null))));

        assertThat(afterFirst.status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(stock().getQuantity()).isEqualTo(4);
        assertThat(ledger.getSupplierBalance(supplier.getId())).isEqualByComparingTo("400.00");

        PurchaseOrderResponse afterSecond = purchaseOrders.receiveOrder(po.id(), new ReceiveOrderRequest(List.of(
                new ReceiveOrderRequest.ItemReceipt(itemId, 6))));

        assertThat(afterSecond.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(afterSecond.outstandingBalance()).isEqualByComparingTo("1000.00");
        assertThat(ledger.getSupplierBalance(supplier.getId())).isEqualByComparingTo("1000.00");

        Inventory inv = stock();
        assertThat(inv.getQuantity()).isEqualTo(10);
        assertThat(inv.getAverageCost()).isEqualByComparingTo("100.00");
        List<InventoryLayer> batches = layers.findByInventoryIdOrderByReceivedAtAsc(inv.getId());
        assertThat(batches).hasSize(2);
        assertThat(batches).extracting(InventoryLayer::getRemainingQuantity).containsExactly(4, 6);
        assertThat(batches.get(0).getLotNumber()).isEqualTo("LOT-A");
        assertThat(batches).allSatisfy(b -> assertThat(b.getUnitCost()).isEqualByComparingTo("100.00"));
        assertThat(productRepository.findById(oil.getId()).orElseThrow().getCostPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void receivingMoreThanOrdered_isRejected_andNothingMoves() {
        PurchaseOrderResponse po = approvedPo(5, "100.00", PurchasePaymentMethod.CREDIT, null);
        assertThatThrownBy(() -> purchaseOrders.receiveOrder(po.id(), new ReceiveOrderRequest(List.of(
                new ReceiveOrderRequest.ItemReceipt(po.items().get(0).id(), 6)))))
                .hasMessageContaining("only 5 remaining");
        assertThat(inventories.findByProductIdAndLocationId(oil.getId(), locations.defaultLocation().getId())
                .map(Inventory::getQuantity).orElse(0)).isZero();
        assertThat(ledger.getSupplierBalance(supplier.getId())).isEqualByComparingTo("0");
    }

    @Test
    void paidInFullAtOrder_thenReceived_supplierIsOwedNothing() {
        PurchaseOrderResponse po = approvedPo(10, "100.00", PurchasePaymentMethod.CASH, null);

        PurchaseOrderResponse received = purchaseOrders.receiveOrder(po.id(), new ReceiveOrderRequest(List.of(
                new ReceiveOrderRequest.ItemReceipt(po.items().get(0).id(), 10))));

        assertThat(received.outstandingBalance()).isEqualByComparingTo("0");
        assertThat(ledger.getSupplierBalance(supplier.getId())).isEqualByComparingTo("0");
        assertThat(suppliers.findById(supplier.getId()).orElseThrow().getOutstandingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void partlyPaidOnCredit_thenReceived_owesOnlyTheRest() {
        PurchaseOrderResponse po = approvedPo(10, "100.00", PurchasePaymentMethod.CREDIT, "300.00");

        PurchaseOrderResponse received = purchaseOrders.receiveOrder(po.id(), new ReceiveOrderRequest(List.of(
                new ReceiveOrderRequest.ItemReceipt(po.items().get(0).id(), 10))));

        assertThat(received.outstandingBalance()).isEqualByComparingTo("700.00");
        assertThat(ledger.getSupplierBalance(supplier.getId())).isEqualByComparingTo("700.00");
    }

    @Test
    void payingAfterReceipt_settlesThePo_andTheSupplierLedger() {
        PurchaseOrderResponse po = approvedPo(10, "100.00", PurchasePaymentMethod.CREDIT, null);
        purchaseOrders.receiveOrder(po.id(), new ReceiveOrderRequest(List.of(
                new ReceiveOrderRequest.ItemReceipt(po.items().get(0).id(), 10))));

        PurchaseOrderResponse paid = purchaseOrders.recordPayment(po.id(),
                new com.ebee.purchase.dto.RecordPaymentRequest(new BigDecimal("1000.00"), PurchasePaymentMethod.BANK_TRANSFER));

        assertThat(paid.paymentStatus()).isEqualTo(com.ebee.purchase.enums.PurchasePaymentStatus.PAID);
        assertThat(paid.outstandingBalance()).isEqualByComparingTo("0");
        assertThat(ledger.getSupplierBalance(supplier.getId())).isEqualByComparingTo("0");
        assertThat(suppliers.findById(supplier.getId()).orElseThrow().getOutstandingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void closingShort_owesOnlyForWhatArrived() {
        PurchaseOrderResponse po = approvedPo(10, "100.00", PurchasePaymentMethod.CREDIT, "200.00");
        purchaseOrders.receiveOrder(po.id(), new ReceiveOrderRequest(List.of(
                new ReceiveOrderRequest.ItemReceipt(po.items().get(0).id(), 6))));

        PurchaseOrderResponse closed = purchaseOrders.closeShort(po.id(), "Supplier out of stock");

        assertThat(closed.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(closed.totalAmount()).isEqualByComparingTo("600.00");
        assertThat(closed.outstandingBalance()).isEqualByComparingTo("400.00");
        assertThat(ledger.getSupplierBalance(supplier.getId())).isEqualByComparingTo("400.00");
    }

    @Test
    void prepaidSupplier_showsNothingOwed_andANegativeLedgerUntilStockArrives() {
        approvedPo(10, "100.00", PurchasePaymentMethod.CASH, null);

        assertThat(ledger.getSupplierBalance(supplier.getId())).isEqualByComparingTo("-1000.00");
        assertThat(suppliers.findById(supplier.getId()).orElseThrow().getOutstandingBalance()).isEqualByComparingTo("0");
    }
}
