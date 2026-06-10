package com.ebee.agent.mapper;

import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.entity.Agent;
import com.ebee.commission.repository.CommissionRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class AgentMapper {

    @Autowired
    protected CommissionRepository commissionRepository;

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "fullName", expression = "java(agent.getUser().getFirstName() + \" \" + agent.getUser().getLastName())")
    @Mapping(target = "district", expression = "java(agent.getDistrict() != null ? agent.getDistrict().getDisplayName() : null)")
    @Mapping(target = "userStatus", source = "user.status")
    @Mapping(target = "totalCommission", expression = "java(commissionRepository.sumCommissionAmountByAgentId(agent.getId()).doubleValue())")
    public abstract AgentResponse toResponse(Agent agent);

    public abstract List<AgentResponse> toResponseList(List<Agent> agents);
}
