package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.commission.dto.CommissionCalculateRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.notification.NotificationService;
import com.ebee.order.entity.Order;
import com.ebee.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CommissionCalculationService {

    private final CommissionRepository commissionRepository;
    private final CommissionConfigRepository configRepository;
    private final OrderRepository orderRepository;
    private final CommissionMapper mapper;
    private final NotificationService notificationService;

    @Transactional
    public CommissionResponse calculateCommission(CommissionCalculateRequest request) {
        if (commissionRepository.existsByOrderId(request.getOrderId())) {
            throw new IllegalStateException("Commission already exists for order: " + request.getOrderId());
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Order not found: " + request.getOrderId()));

        Commission commission = calculateCommissionForOrder(order);

        Agent agent = commission.getAgent();
        String subject = "Commission Calculated – Order " + order.getOrderNumber();
        String message = """
                Dear %s,
                
                A commission of LKR %.2f has been calculated for order %s.
                
                Order Amount: LKR %.2f
                Rate: %.2f%%
                Commission: LKR %.2f
                
                The commission will be processed once approved.
                """.formatted(
                agent.getUser().getFirstName(),
                commission.getCommissionAmount(),
                order.getOrderNumber(),
                order.getTotalAmount(),
                commission.getCommissionRate(),
                commission.getCommissionAmount()
        );
        notificationService.notifyAgent(agent, subject, message);

        return mapper.toResponse(commission);
    }

    @Transactional
    public Commission calculateCommissionForOrder(Order order) {
        if (order.getAgent() == null) {
            throw new IllegalStateException("Order has no agent, cannot calculate commission");
        }

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

        return commissionRepository.save(commission);
    }

    private CommissionConfig findApplicableConfig(Agent agent) {
        if (agent.getCommissionType() == AgentCommissionType.PERFORMANCE) {
            return null;
        }

        return configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT)
                .orElse(null);
    }
}
