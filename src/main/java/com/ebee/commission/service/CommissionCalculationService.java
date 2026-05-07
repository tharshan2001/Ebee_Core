package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.commission.dto.CommissionCalculateRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.order.entity.Order;
import com.ebee.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommissionCalculationService {

    private final CommissionRepository commissionRepository;
    private final CommissionConfigRepository configRepository;
    private final OrderRepository orderRepository;
    private final CommissionMapper mapper;

    @Transactional
    public CommissionResponse calculateCommission(CommissionCalculateRequest request) {
        if (commissionRepository.existsByOrderId(request.getOrderId())) {
            throw new IllegalStateException("Commission already exists for order: " + request.getOrderId());
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Order not found: " + request.getOrderId()));

        Agent agent = order.getAgent();

        CommissionConfig config = findApplicableConfig(agent);

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

    private CommissionConfig findApplicableConfig(Agent agent) {
        List<CommissionConfig> activeConfigs = configRepository.findByIsActiveTrue();

        CommissionConfig performanceConfig = activeConfigs.stream()
                .filter(c -> c.getType() == CommissionType.PERFORMANCE)
                .filter(c -> c.getAppliesTo().equalsIgnoreCase(agent.getAgentCode()))
                .findFirst()
                .orElse(null);

        if (performanceConfig != null) {
            return performanceConfig;
        }

        return activeConfigs.stream()
                .filter(c -> c.getType() == CommissionType.DEFAULT)
                .filter(c -> c.getAppliesTo().equalsIgnoreCase("ALL") ||
                            c.getAppliesTo().equalsIgnoreCase(agent.getAgentCode()))
                .findFirst()
                .orElse(null);
    }
}
