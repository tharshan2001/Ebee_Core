package com.ebee.commission.mapper;

import com.ebee.commission.dto.CommissionConfigRequest;
import com.ebee.commission.dto.CommissionConfigResponse;
import com.ebee.commission.dto.CommissionRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.entity.CommissionConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CommissionMapper {

    CommissionMapper INSTANCE = Mappers.getMapper(CommissionMapper.class);

    @Mapping(target = "agent", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "config", ignore = true)
    Commission toEntity(CommissionRequest request);

    @Mapping(target = "agentId", source = "agent.id")
    @Mapping(target = "agentName", expression = "java(commission.getAgent().getUser().getFirstName() + \" \" + commission.getAgent().getUser().getLastName())")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderNumber", source = "order.orderNumber")
    CommissionResponse toResponse(Commission commission);

    CommissionConfig toConfigEntity(CommissionConfigRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateConfigFromRequest(CommissionConfigRequest request, @MappingTarget CommissionConfig config);

    CommissionConfigResponse toConfigResponse(CommissionConfig config);
}
