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

import java.math.BigDecimal;
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
        BigDecimal totalSales = commissionRepository.sumSalesByAgentId(agentId);
        return commissionRepository.findByAgentId(agentId).stream()
                .map(c -> enrichWithAgentTotals(mapper.toResponse(c), totalSales))
                .collect(Collectors.toList());
    }

    public List<CommissionResponse> getCommissionsByStatus(CommissionStatus status) {
        return commissionRepository.findByStatus(status).stream()
                .map(c -> enrichWithAgentTotals(mapper.toResponse(c), null))
                .collect(Collectors.toList());
    }

    public CommissionResponse getCommissionById(UUID id) {
        Commission commission = commissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission not found: " + id));
        BigDecimal totalSales = commissionRepository.sumSalesByAgentId(commission.getAgent().getId());
        return enrichWithAgentTotals(mapper.toResponse(commission), totalSales);
    }

    public List<CommissionResponse> getAllCommissions() {
        return commissionRepository.findAll().stream()
                .map(c -> {
                    BigDecimal totalSales = commissionRepository.sumSalesByAgentId(c.getAgent().getId());
                    return enrichWithAgentTotals(mapper.toResponse(c), totalSales);
                })
                .collect(Collectors.toList());
    }

    private CommissionResponse enrichWithAgentTotals(CommissionResponse response, BigDecimal totalSales) {
        response.setAgentTotalSales(totalSales);
        return response;
    }
}
