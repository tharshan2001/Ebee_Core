package com.ebee.supplier.service;

import com.ebee.supplier.dto.SupplierDuesPage;
import com.ebee.supplier.dto.*;
import com.ebee.purchase.enums.PurchasePaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SupplierService {
    SupplierDuesPage getDuesPage(String search, int page, int size);


    SupplierResponse createSupplier(SupplierRequest request);

    SupplierResponse getSupplierById(UUID id);

    Page<SupplierResponse> getAllSuppliers(String search, Pageable pageable);

    List<SupplierResponse> searchSuppliers(String search);

    SupplierResponse updateSupplier(UUID id, SupplierRequest request);

    void deactivateSupplier(UUID id);

    List<SupplierResponse> getSuppliersWithDues();

    SupplierOutstandingOrdersResponse getSupplierOutstandingOrders(UUID supplierId);

    SupplierPaymentResponse recordSupplierPayment(UUID supplierId, BigDecimal amount, PurchasePaymentMethod paymentMethod, String description);

    SupplierAdjustmentResponse manualAdjustOutstanding(UUID supplierId, AdjustOutstandingRequest request);
}
