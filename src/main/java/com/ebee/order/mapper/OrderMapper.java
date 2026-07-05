package com.ebee.order.mapper;

import com.ebee.order.dto.OrderItemResponse;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "agentId", source = "agent.id")
    @Mapping(target = "agentCode", source = "agent.agentCode")
    @Mapping(target = "agentName", expression = "java(order.getAgent() != null && order.getAgent().getUser() != null ? order.getAgent().getUser().getFirstName() + \" \" + order.getAgent().getUser().getLastName() : \"Direct Order\")")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", expression = "java(order.getCustomer() != null ? order.getCustomer().getFirstName() + \" \" + order.getCustomer().getLastName() : null)")
    @Mapping(target = "customerPhone", source = "customer.phoneNumber")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", expression = "java(order.getCreatedBy() != null ? order.getCreatedBy().getFirstName() + \" \" + order.getCreatedBy().getLastName() : null)")
    @Mapping(target = "district", expression = "java(order.getDistrict())")
    @Mapping(target = "deliveryStatus", source = "deliveryStatus")
    @Mapping(target = "deliveryAssignedToId", source = "deliveryAssignedTo.id")
    @Mapping(target = "deliveryAssignedToName", expression = "java(order.getDeliveryAssignedTo() != null ? order.getDeliveryAssignedTo().getFirstName() + \" \" + order.getDeliveryAssignedTo().getLastName() : null)")
    @Mapping(target = "deliveryScheduledDate", source = "deliveryScheduledDate")
    @Mapping(target = "deliveryNotes", source = "deliveryNotes")
    @Mapping(target = "deliveryFailureReason", source = "deliveryFailureReason")
    @Mapping(target = "orderSource", source = "orderSource")
    @Mapping(target = "paymentMethod", source = "paymentMethod")
    @Mapping(target = "paymentStatus", source = "paymentStatus")
    @Mapping(target = "paidAmount", source = "paidAmount")
    @Mapping(target = "paidAt", source = "paidAt")
    @Mapping(target = "outstandingBalance", source = "outstandingBalance")
    OrderResponse toResponse(Order order);

    List<OrderResponse> toResponseList(List<Order> orders);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "productItemCode", source = "product.itemCode")
    @Mapping(target = "costPrice", source = "costPrice")
    OrderItemResponse toItemResponse(OrderItem item);

    List<OrderItemResponse> toItemResponseList(List<OrderItem> items);
}
