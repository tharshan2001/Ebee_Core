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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final UserRepository userRepository;

    @Transactional
    public AgentResponse createAgent(AgentRequest request) throws IOException {
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
                .nicNumber(request.getNicNumber())
                .bankAccountNumber(request.getBankAccountNumber())
                .bankName(request.getBankName())
                .bankBranch(request.getBankBranch())
                .build();

        // Handle NIC document upload
        if (request.getNicDocument() != null && !request.getNicDocument().isEmpty()) {
            agent.setNicDocument(request.getNicDocument().getBytes());
            agent.setNicDocumentName(request.getNicDocument().getOriginalFilename());
            agent.setNicDocumentType(request.getNicDocument().getContentType());
        }

        // Handle Bank Passbook document upload
        if (request.getBankPassbookDocument() != null && !request.getBankPassbookDocument().isEmpty()) {
            agent.setBankPassbookDocument(request.getBankPassbookDocument().getBytes());
            agent.setBankPassbookDocumentName(request.getBankPassbookDocument().getOriginalFilename());
            agent.setBankPassbookDocumentType(request.getBankPassbookDocument().getContentType());
        }

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
    public AgentResponse updateAgent(UUID id, AgentUpdateRequest request) throws IOException {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        if (!agent.getAgentCode().equals(request.getAgentCode()) &&
            agentRepository.existsByAgentCode(request.getAgentCode())) {
            throw new IllegalArgumentException("Agent code already exists");
        }

        agent.setAgentCode(request.getAgentCode());
        agent.setPhoneNumber(request.getPhoneNumber());
        agent.setCommissionRate(request.getCommissionRate());
        agent.setNicNumber(request.getNicNumber());
        agent.setBankAccountNumber(request.getBankAccountNumber());
        agent.setBankName(request.getBankName());
        agent.setBankBranch(request.getBankBranch());

        // Handle NIC document upload
        if (request.getNicDocument() != null && !request.getNicDocument().isEmpty()) {
            agent.setNicDocument(request.getNicDocument().getBytes());
            agent.setNicDocumentName(request.getNicDocument().getOriginalFilename());
            agent.setNicDocumentType(request.getNicDocument().getContentType());
        }

        // Handle Bank Passbook document upload
        if (request.getBankPassbookDocument() != null && !request.getBankPassbookDocument().isEmpty()) {
            agent.setBankPassbookDocument(request.getBankPassbookDocument().getBytes());
            agent.setBankPassbookDocumentName(request.getBankPassbookDocument().getOriginalFilename());
            agent.setBankPassbookDocumentType(request.getBankPassbookDocument().getContentType());
        }

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

        response.setNicNumber(agent.getNicNumber());
        response.setNicDocumentName(agent.getNicDocumentName());
        response.setNicDocumentType(agent.getNicDocumentType());
        response.setBankPassbookDocumentName(agent.getBankPassbookDocumentName());
        response.setBankPassbookDocumentType(agent.getBankPassbookDocumentType());
        response.setBankAccountNumber(agent.getBankAccountNumber());
        response.setBankName(agent.getBankName());
        response.setBankBranch(agent.getBankBranch());

        response.setCreatedAt(agent.getCreatedAt());
        response.setUpdatedAt(agent.getUpdatedAt());
        return response;
    }
}
