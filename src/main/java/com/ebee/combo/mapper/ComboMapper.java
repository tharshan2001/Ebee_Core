package com.ebee.combo.mapper;

import com.ebee.combo.dto.ComboItemResponse;
import com.ebee.combo.dto.ComboResponse;
import com.ebee.combo.dto.ComboRuleResponse;
import com.ebee.combo.entity.Combo;
import com.ebee.combo.entity.ComboItem;
import com.ebee.combo.entity.ComboRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ComboMapper {

    @Mapping(target = "comboPrice", expression = "java(combo.calculateComboPrice())")
    @Mapping(target = "items", source = "combo.items")
    @Mapping(target = "rule", source = "combo.rule")
    ComboResponse toResponse(Combo combo);

    List<ComboResponse> toResponseList(List<Combo> combos);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "productItemCode", source = "product.itemCode")
    @Mapping(target = "productPrice", source = "product.price")
    @Mapping(target = "lineTotal", expression = "java(item.getProduct().getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())).setScale(2, java.math.RoundingMode.HALF_UP))")
    ComboItemResponse toItemResponse(ComboItem item);

    @Mapping(target = "freeProductId", source = "freeProduct.id")
    @Mapping(target = "freeProductName", source = "freeProduct.name")
    @Mapping(target = "freeProductItemCode", source = "freeProduct.itemCode")
    @Mapping(target = "freeProductPrice", source = "freeProduct.price")
    ComboRuleResponse toRuleResponse(ComboRule rule);
}
