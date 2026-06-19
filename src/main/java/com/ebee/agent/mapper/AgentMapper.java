package com.ebee.agent.mapper;

import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class AgentMapper {

    protected CommissionRepository commissionRepository;
    protected CommissionConfigRepository commissionConfigRepository;

    @Autowired
    public void setCommissionRepository(CommissionRepository commissionRepository) {
        this.commissionRepository = commissionRepository;
    }

    @Autowired
    public void setCommissionConfigRepository(CommissionConfigRepository commissionConfigRepository) {
        this.commissionConfigRepository = commissionConfigRepository;
    }

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

    @AfterMapping
    protected void setEffectiveCommissionRate(Agent agent, @MappingTarget AgentResponse response) {
        if (agent.getCommissionType() == AgentCommissionType.PERFORMANCE) {
            response.setEffectiveCommissionRate(agent.getCommissionRate());
        } else {
            CommissionConfig config = commissionConfigRepository
                    .findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT)
                    .orElse(null);
            response.setEffectiveCommissionRate(config != null ? config.getCommissionRate() : agent.getCommissionRate());
        }
    }
}
