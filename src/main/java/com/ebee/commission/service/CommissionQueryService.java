package com.ebee.commission.service;

import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionQueryService {

    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;

    public List<CommissionResponse> getCommissionsByAgent(UUID agentId) {
        return commissionRepository.findByAgentId(agentId).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<CommissionResponse> getCommissionsByStatus(CommissionStatus status) {
        return commissionRepository.findByStatus(status).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    public CommissionResponse getCommissionById(UUID id) {
        Commission commission = commissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission not found: " + id));
        return mapper.toResponse(commission);
    }

    public List<CommissionResponse> getAllCommissions() {
        return commissionRepository.findAll().stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }
}
