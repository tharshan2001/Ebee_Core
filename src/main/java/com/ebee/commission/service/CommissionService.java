package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.commission.dto.CommissionCalculateRequest;
import com.ebee.commission.dto.CommissionConfigResponse;
import com.ebee.commission.dto.CommissionRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommissionService {

    private final CommissionRepository commissionRepository;
    private final CommissionConfigRepository configRepository;
    private final CommissionMapper mapper;

    @Transactional
    public CommissionResponse calculateCommission(CommissionCalculateRequest request) {
        if (commissionRepository.existsByOrderId(request.getOrderId())) {
            throw new IllegalStateException("Commission already exists for order: " + request.getOrderId());
        }

        Order order = getOrderEntity(request.getOrderId());
        Agent agent = order.getAgent();

        CommissionConfig config = configRepository.findByIsActiveTrue().stream()
                .filter(c -> c.getAppliesTo().equalsIgnoreCase(agent.getAgentCode()) ||
                            c.getAppliesTo().equalsIgnoreCase("ALL"))
                .findFirst()
                .orElse(null);

        BigDecimal commissionRate = config != null ?
                BigDecimal.valueOf(config.getCommissionRate()) :
                BigDecimal.valueOf(agent.getCommissionRate());

        BigDecimal commissionAmount = order.getTotalAmount()
                .multiply(commissionRate)
                .divide(BigDecimal.valueOf(100));

        Commission commission = Commission.builder()
                .agent(agent)
                .order(order)
                .config(config)
                .orderAmount(order.getTotalAmount())
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .status(CommissionStatus.CALCULATED)
                .calculatedAt(LocalDateTime.now())
                .build();

        commission = commissionRepository.save(commission);
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse approveCommission(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.APPROVED);
        commission.setApprovedAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse markAsProcessing(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.PROCESSING);
        commission = commissionRepository.save(commission);
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse markAsPaid(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.PAID);
        commission.setPaidAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);

        Agent agent = commission.getAgent();
        agent.setTotalCommission(agent.getTotalCommission() + commission.getCommissionAmount().doubleValue());
        agent.setTotalSales(agent.getTotalSales() + commission.getOrderAmount().doubleValue());

        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse rejectCommission(UUID commissionId, String reason) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.REJECTED);
        commission.setErrorMessage(reason);
        commission = commissionRepository.save(commission);
        return mapper.toResponse(commission);
    }

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
        return mapper.toResponse(getCommissionEntity(id));
    }

    private Commission getCommissionEntity(UUID id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission not found: " + id));
    }

    private Order getOrderEntity(UUID orderId) {
        return null; // Would inject OrderRepository
    }
}
