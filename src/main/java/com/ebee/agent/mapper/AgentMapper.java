package com.ebee.agent.mapper;

import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.entity.Agent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AgentMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "fullName", expression = "java(agent.getUser().getFirstName() + \" \" + agent.getUser().getLastName())")
    @Mapping(target = "district", expression = "java(agent.getDistrict() != null ? agent.getDistrict().getDisplayName() : null)")
    @Mapping(target = "userStatus", source = "user.status")
    @Mapping(target = "addressLine1", source = "addressLine1")
    @Mapping(target = "addressLine2", source = "addressLine2")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "postalCode", source = "postalCode")
    @Mapping(target = "country", source = "country")
    AgentResponse toResponse(Agent agent);

    List<AgentResponse> toResponseList(List<Agent> agents);
}
