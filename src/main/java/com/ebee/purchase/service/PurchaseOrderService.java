package com.ebee.purchase.service;

import com.ebee.purchase.dto.PurchaseOrderRequest;
import com.ebee.purchase.dto.PurchaseOrderResponse;
import com.ebee.purchase.dto.PurchaseOrderStatusRequest;
import com.ebee.purchase.dto.PurchaseOrderUpdateRequest;
import com.ebee.purchase.dto.ReceiveOrderRequest;
import com.ebee.purchase.dto.RecordPaymentRequest;
import com.ebee.purchase.dto.ReturnItemsRequest;
import com.ebee.purchase.enums.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderService {

    PurchaseOrderResponse createOrder(PurchaseOrderRequest request, UUID createdByUserId);

    PurchaseOrderResponse updateOrder(UUID id, PurchaseOrderUpdateRequest request);

    PurchaseOrderResponse getOrder(UUID id);

    Page<PurchaseOrderResponse> listOrders(String search, Pageable pageable);

    PurchaseOrderResponse updateStatus(UUID id, PurchaseOrderStatusRequest request);

    PurchaseOrderResponse receiveOrder(UUID id, ReceiveOrderRequest request);

    PurchaseOrderResponse returnItems(UUID id, ReturnItemsRequest request);

    /** Keep what arrived on a PARTIALLY_RECEIVED PO and stop expecting the rest. */
    PurchaseOrderResponse closeShort(UUID id, String reason);

    PurchaseOrderResponse addAttachment(UUID orderId, MultipartFile file);

    void deleteAttachment(UUID orderId, UUID attachmentId);

    List<PurchaseOrderResponse> findBySupplier(UUID supplierId);

    PurchaseOrderResponse recordPayment(UUID id, RecordPaymentRequest request);
}
