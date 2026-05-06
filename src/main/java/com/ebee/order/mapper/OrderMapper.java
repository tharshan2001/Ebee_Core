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
    @Mapping(target = "agentName", expression = "java(order.getAgent().getUser() != null ? order.getAgent().getUser().getFirstName() + \" \" + order.getAgent().getUser().getLastName() : null)")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", expression = "java(order.getCreatedBy() != null ? order.getCreatedBy().getFirstName() + \" \" + order.getCreatedBy().getLastName() : null)")
    OrderResponse toResponse(Order order);

    List<OrderResponse> toResponseList(List<Order> orders);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "productSku", source = "product.sku")
    OrderItemResponse toItemResponse(OrderItem item);

    List<OrderItemResponse> toItemResponseList(List<OrderItem> items);
}
