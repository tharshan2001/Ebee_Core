package com.ebee.purchase.service;

import com.ebee.purchase.dto.PurchaseOrderStatusRequest;
import com.ebee.purchase.entity.PurchaseOrder;
import com.ebee.purchase.entity.PurchaseOrderItem;
import com.ebee.purchase.enums.PurchaseOrderStatus;
import com.ebee.purchase.mapper.PurchaseOrderMapper;
import com.ebee.purchase.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Phase D: received stock can't be cancelled away; close-short keeps only what arrived. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseOrderRulesTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private com.ebee.purchase.repository.PurchaseOrderAttachmentRepository attachmentRepository;
    @Mock private com.ebee.supplier.repository.SupplierRepository supplierRepository;
    @Mock private com.ebee.product.repository.ProductRepository productRepository;
    @Mock private com.ebee.user.repository.UserRepository userRepository;
    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    @Mock private com.ebee.inventory.service.InventoryService inventoryService;
    @Mock private com.ebee.inventory.repository.InventoryRepository inventoryRepository;
    @Mock private com.ebee.common.port.StoragePort storagePort;
    @Mock private PurchaseOrderPaymentService purchaseOrderPaymentService;
    @Mock private com.ebee.tax.service.TaxService taxService;
    @Mock private com.ebee.tax.service.VatService vatService;
    @Mock private com.ebee.location.service.LocationService locationService;
    @InjectMocks private PurchaseOrderServiceImpl service;

    private PurchaseOrder po(PurchaseOrderStatus status) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(UUID.randomUUID());
        po.setPoNumber("PO-TEST");
        po.setStatus(status);
        po.setItems(new ArrayList<>());
        when(purchaseOrderRepository.findByIdWithItemsLocked(po.getId())).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return po;
    }

    private static PurchaseOrderItem item(int ordered, int received, String unitPrice, String unitTax) {
        PurchaseOrderItem i = new PurchaseOrderItem();
        i.setQuantity(ordered);
        i.setReceivedQuantity(received);
        i.setUnitPrice(new BigDecimal(unitPrice));
        i.setUnitTax(new BigDecimal(unitTax));
        return i;
    }

    @ParameterizedTest
    @EnumSource(value = PurchaseOrderStatus.class, names = {"PARTIALLY_RECEIVED", "RECEIVED", "RETURNED"})
    void cancellingAfterStockArrived_isRejected(PurchaseOrderStatus status) {
        PurchaseOrder po = po(status);
        assertThatThrownBy(() -> service.updateStatus(po.getId(), new PurchaseOrderStatusRequest(PurchaseOrderStatus.CANCELLED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PurchaseOrderStatus.class, names = {"DRAFT", "PENDING", "APPROVED"})
    void cancellingBeforeAnyStockArrived_isAllowed(PurchaseOrderStatus status) {
        PurchaseOrder po = po(status);
        service.updateStatus(po.getId(), new PurchaseOrderStatusRequest(PurchaseOrderStatus.CANCELLED));
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    }

    @Test
    void receivedToReturned_viaStatusEndpoint_isRejected_useReturnItems() {
        PurchaseOrder po = po(PurchaseOrderStatus.RECEIVED);
        assertThatThrownBy(() -> service.updateStatus(po.getId(), new PurchaseOrderStatusRequest(PurchaseOrderStatus.RETURNED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closeShort_keepsOnlyWhatArrived() {
        PurchaseOrder po = po(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        // 10 ordered, 6 received at 100 + 18 VAT; 5 ordered, 0 received.
        po.getItems().addAll(List.of(item(10, 6, "100.00", "18.00"), item(5, 0, "40.00", "0")));
        po.setTotalAmount(new BigDecimal("1380.00"));
        po.setOutstandingBalance(new BigDecimal("708.00"));

        service.closeShort(po.getId(), "Supplier out of stock");

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(po.getTotalAmount()).isEqualByComparingTo("708.00");
        assertThat(po.getTaxTotal()).isEqualByComparingTo("108.00");
        assertThat(po.getNotes()).contains("Closed short: Supplier out of stock");
    }

    @Test
    void closeShort_onlyForPartiallyReceived_andNotWhenOverpaid() {
        PurchaseOrder approved = po(PurchaseOrderStatus.APPROVED);
        assertThatThrownBy(() -> service.closeShort(approved.getId(), null)).hasMessageContaining("PARTIALLY_RECEIVED");

        PurchaseOrder prepaid = po(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        prepaid.getItems().add(item(10, 2, "100.00", "0"));
        prepaid.setPaidAmount(new BigDecimal("1000.00"));
        assertThatThrownBy(() -> service.closeShort(prepaid.getId(), null)).hasMessageContaining("supplier refund");
    }
}
