package com.ebee.purchase.mapper;

import com.ebee.purchase.dto.AttachmentResponse;
import com.ebee.purchase.dto.PurchaseOrderItemResponse;
import com.ebee.purchase.dto.PurchaseOrderResponse;
import com.ebee.purchase.entity.PurchaseOrder;
import com.ebee.purchase.entity.PurchaseOrderAttachment;
import com.ebee.purchase.entity.PurchaseOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {

    @Mapping(target = "supplierId", source = "purchaseOrder.supplier.id")
    @Mapping(target = "supplierName", source = "purchaseOrder.supplier.name")
    @Mapping(target = "createdById", source = "purchaseOrder.createdBy.id")
    @Mapping(target = "createdByName", expression = "java(purchaseOrder.getCreatedBy() != null ? purchaseOrder.getCreatedBy().getFirstName() + \" \" + purchaseOrder.getCreatedBy().getLastName() : null)")
    @Mapping(target = "locationId", source = "purchaseOrder.location.id")
    @Mapping(target = "locationName", source = "purchaseOrder.location.name")
    @Mapping(target = "items", source = "purchaseOrderItems")
    @Mapping(target = "attachments", source = "attachmentList")
    PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder, List<PurchaseOrderItemResponse> purchaseOrderItems, List<AttachmentResponse> attachmentList);

    default PurchaseOrderResponse toFullResponse(PurchaseOrder purchaseOrder) {
        List<PurchaseOrderItemResponse> itemResponses = purchaseOrder.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        List<AttachmentResponse> attachmentResponses = purchaseOrder.getAttachments().stream()
                .map(this::toAttachmentResponse)
                .toList();
        return toResponse(purchaseOrder, itemResponses, attachmentResponses);
    }

    @Mapping(target = "productId", source = "item.product.id")
    @Mapping(target = "productName", source = "item.product.name")
    @Mapping(target = "productItemCode", source = "item.product.itemCode")
    @Mapping(target = "receivedQuantity", source = "item.receivedQuantity")
    @Mapping(target = "returnedQuantity", source = "item.returnedQuantity")
    PurchaseOrderItemResponse toItemResponse(PurchaseOrderItem item);

    AttachmentResponse toAttachmentResponse(PurchaseOrderAttachment attachment);
}
