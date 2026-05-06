package com.ebee.agent.service;

import com.ebee.agent.dto.AgentRequest;
import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.dto.AgentUpdateRequest;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.common.exception.ResourceNotFoundException;
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
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        if (!user.getRole().equals(RoleType.AGENT)) {
            throw new IllegalArgumentException("User must have AGENT role");
        }

        if (agentRepository.existsByAgentCode(request.getAgentCode())) {
            throw new IllegalArgumentException("Agent code already exists");
        }

        Agent agent = Agent.builder()
                .user(user)
                .agentCode(request.getAgentCode())
                .phoneNumber(request.getPhoneNumber())
                .commissionRate(request.getCommissionRate())
                .build();

        Agent savedAgent = agentRepository.save(agent);
        return mapToResponse(savedAgent);
    }

    public AgentResponse getAgentById(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        return mapToResponse(agent);
    }

    public AgentResponse getAgentByUserId(UUID userId) {
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
        return mapToResponse(agent);
    }

    public List<AgentResponse> getAllAgents() {
        return agentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AgentResponse updateAgent(UUID id, AgentUpdateRequest request) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        if (!agent.getAgentCode().equals(request.getAgentCode()) &&
            agentRepository.existsByAgentCode(request.getAgentCode())) {
            throw new IllegalArgumentException("Agent code already exists");
        }

        agent.setAgentCode(request.getAgentCode());
        agent.setPhoneNumber(request.getPhoneNumber());
        agent.setCommissionRate(request.getCommissionRate());

        return mapToResponse(agentRepository.save(agent));
    }

    public AgentResponse getAgentByAgentCode(String agentCode) {
        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "agentCode", agentCode));
        return mapToResponse(agent);
    }

    @Transactional
    public void deleteAgent(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        agent.setIsActive(false);
        agentRepository.save(agent);
    }

    @Transactional
    public AgentResponse updateCommissionRate(UUID id, Double commissionRate) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        agent.setCommissionRate(commissionRate);
        return mapToResponse(agentRepository.save(agent));
    }

    private AgentResponse mapToResponse(Agent agent) {
        AgentResponse response = new AgentResponse();
        response.setId(agent.getId());
        response.setUserId(agent.getUser().getId());
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
