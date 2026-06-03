package com.ebee.stock.mapper;

import com.ebee.stock.dto.StockResponse;
import com.ebee.stock.entity.Stock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StockMapper {

    @Mapping(target = "productId", source = "inventory.product.id")
    @Mapping(target = "productName", source = "inventory.product.name")
    @Mapping(target = "productItemCode", source = "inventory.product.itemCode")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", expression = "java(stock.getCreatedBy() != null ? stock.getCreatedBy().getFirstName() + \" \" + stock.getCreatedBy().getLastName() : null)")
    @Mapping(target = "createdByEmail", source = "createdBy.email")
    StockResponse toResponse(Stock stock);

    List<StockResponse> toResponseList(List<Stock> stocks);
}
