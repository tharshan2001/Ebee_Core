package com.ebee.agent.service;

import com.ebee.agent.dto.AgentRequest;
import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final UserRepository userRepository;

    @Transactional
    public AgentResponse createAgent(AgentRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getRole().equals(RoleType.AGENT)) {
            throw new RuntimeException("User must have AGENT role");
        }

        if (agentRepository.existsByAgentCode(request.getAgentCode())) {
            throw new RuntimeException("Agent code already exists");
        }

        Agent agent = Agent.builder()
                .userId(request.getUserId())
                .agentCode(request.getAgentCode())
                .phoneNumber(request.getPhoneNumber())
                .commissionRate(request.getCommissionRate())
                .build();

        Agent savedAgent = agentRepository.save(agent);
        return mapToResponse(savedAgent);
    }

    public AgentResponse getAgentById(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        return mapToResponse(agent);
    }

    public AgentResponse getAgentByUserId(UUID userId) {
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        return mapToResponse(agent);
    }

    public List<AgentResponse> getAllAgents() {
        return agentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AgentResponse updateAgent(UUID id, AgentRequest request) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        if (!agent.getAgentCode().equals(request.getAgentCode()) &&
            agentRepository.existsByAgentCode(request.getAgentCode())) {
            throw new RuntimeException("Agent code already exists");
        }

        agent.setAgentCode(request.getAgentCode());
        agent.setPhoneNumber(request.getPhoneNumber());
        agent.setCommissionRate(request.getCommissionRate());

        return mapToResponse(agentRepository.save(agent));
    }

    public AgentResponse getAgentByAgentCode(String agentCode) {
        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        return mapToResponse(agent);
    }

    private AgentResponse mapToResponse(Agent agent) {
        AgentResponse response = new AgentResponse();
        response.setId(agent.getId());
        response.setUserId(agent.getUserId());
        response.setAgentCode(agent.getAgentCode());
        response.setPhoneNumber(agent.getPhoneNumber());
        response.setCommissionRate(agent.getCommissionRate());
        response.setTotalSales(agent.getTotalSales());
        response.setTotalCommission(agent.getTotalCommission());
        response.setIsActive(agent.getIsActive());
        response.setCreatedAt(agent.getCreatedAt());
        response.setUpdatedAt(agent.getUpdatedAt());
        return response;
    }
}
