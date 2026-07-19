package com.ebee.supplier.mapper;

import com.ebee.supplier.dto.SupplierRequest;
import com.ebee.supplier.dto.SupplierResponse;
import com.ebee.supplier.entity.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", imports = {com.ebee.common.enums.District.class})
public interface SupplierMapper {

    @Mapping(target = "district", expression = "java(request.district() != null ? District.fromDisplayName(request.district()) : null)")
    @Mapping(target = "country", expression = "java(request.country() != null ? request.country() : \"Sri Lanka\")")
    Supplier toEntity(SupplierRequest request);

    @Mapping(target = "district", expression = "java(supplier.getDistrict() != null ? supplier.getDistrict().getDisplayName() : null)")
    @Mapping(target = "isActive", source = "active")
    SupplierResponse toResponse(Supplier supplier);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "district", expression = "java(request.district() != null ? District.fromDisplayName(request.district()) : null)")
    @Mapping(target = "country", expression = "java(request.country() != null ? request.country() : \"Sri Lanka\")")
    void updateEntity(SupplierRequest request, @MappingTarget Supplier supplier);
}
