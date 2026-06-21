package com.ebee.lead.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.lead.dto.CreateLeadRequest;
import com.ebee.lead.dto.LeadResponse;
import com.ebee.lead.entity.Lead;
import com.ebee.lead.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);
    private final LeadRepository leadRepository;

    @Transactional
    public LeadResponse createLead(CreateLeadRequest request) {
        Lead lead = new Lead();
        lead.setAgentId(request.getAgentId());
        lead.setAgentName(request.getAgentName().trim());
        lead.setCustomerName(request.getCustomerName().trim());
        lead.setPhone(request.getPhone().trim());
        lead.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        lead.setProductInterest(request.getProductInterest().trim());
        lead.setNote(request.getNote());
        lead.setSubmittedAt(LocalDateTime.now());

        Lead saved = leadRepository.save(lead);
        log.info("Lead created: {} for agent {}", saved.getId(), saved.getAgentId());
        return toResponse(saved);
    }

    public List<LeadResponse> getAllLeads() {
        return leadRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public LeadResponse getLeadById(UUID id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", "id", id));
        return toResponse(lead);
    }

    public List<LeadResponse> getLeadsByAgentId(UUID agentId) {
        return leadRepository.findByAgentIdOrderBySubmittedAtDesc(agentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LeadResponse convertLead(UUID leadId, UUID orderId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", "id", leadId));

        lead.setConvertedOrderId(orderId);
        Lead saved = leadRepository.save(lead);
        log.info("Lead converted: {} -> order {}", saved.getId(), orderId);
        return toResponse(saved);
    }

    private LeadResponse toResponse(Lead lead) {
        LeadResponse response = new LeadResponse();
        response.setId(lead.getId());
        response.setAgentId(lead.getAgentId());
        response.setAgentName(lead.getAgentName());
        response.setCustomerName(lead.getCustomerName());
        response.setPhone(lead.getPhone());
        response.setEmail(lead.getEmail());
        response.setProductInterest(lead.getProductInterest());
        response.setNote(lead.getNote());
        response.setSubmittedAt(lead.getSubmittedAt());
        response.setConvertedOrderId(lead.getConvertedOrderId());
        return response;
    }
}
