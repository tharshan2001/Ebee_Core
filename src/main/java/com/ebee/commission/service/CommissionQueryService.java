package com.ebee.commission.service;

import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.dto.PendingPaymentSummary;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.payout.repository.PayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ebee.payout.entity.Payout;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionQueryService {

    private final CommissionRepository commissionRepository;
    private final PayoutRepository payoutRepository;
    private final CommissionMapper mapper;

    public PendingPaymentSummary getPendingPaymentSummary(UUID agentId) {
        List<Commission> processing = commissionRepository.findByAgentIdAndStatusWithDetails(agentId, CommissionStatus.PROCESSING);
        BigDecimal total = commissionRepository.sumCommissionAmountByAgentIdAndStatus(agentId, CommissionStatus.PROCESSING);
        processing.sort(Comparator.comparing(Commission::getApprovedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<CommissionResponse> responses = processing.stream()
                .map(c -> enrichWithAgentTotals(mapper.toResponse(c), null))
                .toList();
        enrichWithPayoutIds(responses);
        return PendingPaymentSummary.builder()
                .totalAvailable(total)
                .processingCount(processing.size())
                .commissions(responses)
                .build();
    }

    public List<CommissionResponse> getCommissionsByAgent(UUID agentId) {
        BigDecimal totalSales = commissionRepository.sumSalesByAgentId(agentId);
        List<CommissionResponse> responses = commissionRepository.findByAgentIdWithDetails(agentId).stream()
                .map(c -> enrichWithAgentTotals(mapper.toResponse(c), totalSales))
                .toList();
        enrichWithPayoutIds(responses);
        return responses;
    }

    public List<CommissionResponse> getCommissionsByStatus(CommissionStatus status) {
        List<CommissionResponse> responses = commissionRepository.findByStatusWithDetails(status).stream()
                .map(c -> enrichWithAgentTotals(mapper.toResponse(c), null))
                .toList();
        enrichWithPayoutIds(responses);
        return responses;
    }

    public CommissionResponse getCommissionById(UUID id) {
        Commission commission = commissionRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commission not found: " + id));
        BigDecimal totalSales = commissionRepository.sumSalesByAgentId(commission.getAgent().getId());
        CommissionResponse response = enrichWithAgentTotals(mapper.toResponse(commission), totalSales);
        enrichWithPayoutIds(List.of(response));
        return response;
    }

    public Page<CommissionResponse> getAllCommissions(Pageable pageable) {
        Page<Commission> commissions = commissionRepository.findAllWithDetails(pageable);
        Map<UUID, BigDecimal> salesMap = buildSalesMap(commissions.getContent());
        Page<CommissionResponse> page = commissions.map(c -> enrichWithAgentTotals(mapper.toResponse(c), salesMap.get(c.getAgent().getId())));
        enrichWithPayoutIds(page.getContent());
        return page;
    }

    private Map<UUID, BigDecimal> buildSalesMap(List<Commission> commissions) {
        List<UUID> agentIds = commissions.stream()
                .map(c -> c.getAgent().getId())
                .distinct()
                .toList();
        return commissionRepository.sumSalesByAgentIds(agentIds)
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));
    }

    private CommissionResponse enrichWithAgentTotals(CommissionResponse response, BigDecimal totalSales) {
        response.setAgentTotalSales(totalSales);
        return response;
    }

    private void enrichWithPayoutIds(List<CommissionResponse> responses) {
        if (responses.isEmpty()) return;
        List<UUID> commissionIds = responses.stream()
                .map(CommissionResponse::getId)
                .toList();
        List<Payout> payouts = payoutRepository.findByCommissionIds(commissionIds);
        Map<UUID, UUID> payoutMap = payouts.stream()
                .flatMap(p -> p.getCommissionIds().stream().map(cid -> Map.entry(cid, p.getId())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        for (CommissionResponse r : responses) {
            r.setPayoutId(payoutMap.get(r.getId()));
        }
    }
}
