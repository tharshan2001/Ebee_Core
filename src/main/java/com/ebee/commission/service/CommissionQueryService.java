package com.ebee.commission.service;

import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.dto.PendingPaymentSummary;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionQueryService {

    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;

    public PendingPaymentSummary getPendingPaymentSummary(UUID agentId) {
        List<Commission> processing = commissionRepository.findByAgentIdAndStatus(agentId, CommissionStatus.PROCESSING);
        BigDecimal total = commissionRepository.sumCommissionAmountByAgentIdAndStatus(agentId, CommissionStatus.PROCESSING);
        processing.sort(Comparator.comparing(Commission::getApprovedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<CommissionResponse> responses = processing.stream()
                .map(c -> enrichWithAgentTotals(mapper.toResponse(c), null))
                .toList();
        return PendingPaymentSummary.builder()
                .totalAvailable(total)
                .processingCount(processing.size())
                .commissions(responses)
                .build();
    }

    public List<CommissionResponse> getCommissionsByAgent(UUID agentId) {
        BigDecimal totalSales = commissionRepository.sumSalesByAgentId(agentId);
        return commissionRepository.findByAgentId(agentId).stream()
                .map(c -> enrichWithAgentTotals(mapper.toResponse(c), totalSales))
                .toList();
    }

    public List<CommissionResponse> getCommissionsByStatus(CommissionStatus status) {
        return commissionRepository.findByStatus(status).stream()
                .map(c -> enrichWithAgentTotals(mapper.toResponse(c), null))
                .toList();
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
                .toList();
    }

    private CommissionResponse enrichWithAgentTotals(CommissionResponse response, BigDecimal totalSales) {
        response.setAgentTotalSales(totalSales);
        return response;
    }
}
