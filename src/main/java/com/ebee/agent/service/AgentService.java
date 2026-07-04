package com.ebee.agent.service;

import com.ebee.agent.dto.AgentRequest;
import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.dto.AgentUpdateRequest;
import com.ebee.agent.dto.AgentWithCredentialsResponse;
import com.ebee.agent.enums.AgentCommissionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AgentService {

    AgentWithCredentialsResponse createAgent(AgentRequest request, boolean createdByAdmin);

    AgentResponse approveAgent(UUID id);

    AgentResponse rejectAgent(UUID id);

    AgentResponse activateAgent(UUID id);

    AgentResponse deactivateAgent(UUID id);

    AgentResponse getAgentById(UUID id);

    AgentResponse getAgentByUserId(UUID userId);

    List<AgentResponse> getAllAgents();

    Page<AgentResponse> getAllAgentsPaginated(Pageable pageable);

    Page<AgentResponse> searchAgentsPaginated(String search, Pageable pageable);

    List<AgentResponse> searchAgents(String search);

    AgentResponse updateAgent(UUID id, AgentUpdateRequest request);

    AgentResponse getAgentByAgentCode(String agentCode);

    void deleteAgent(UUID id);

    AgentResponse updateCommissionRate(UUID id, Double commissionRate, AgentCommissionType commissionType);

    void resetPassword(UUID id);
}
