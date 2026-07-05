package com.ebee.supplier.mapper;

import com.ebee.supplier.dto.SupplierRequest;
import com.ebee.supplier.dto.SupplierResponse;
import com.ebee.supplier.entity.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SupplierMapper {

    Supplier toEntity(SupplierRequest request);

    @Mapping(target = "isActive", source = "active")
    SupplierResponse toResponse(Supplier supplier);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(SupplierRequest request, @MappingTarget Supplier supplier);
}
