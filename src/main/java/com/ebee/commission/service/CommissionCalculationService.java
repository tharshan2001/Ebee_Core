package com.ebee.commission.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.alert.enums.AlertType;
import com.ebee.common.security.SecurityService;
import com.ebee.commission.dto.CommissionCalculateRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.util.NumberUtils;
import com.ebee.notification.NotificationPort;
import com.ebee.order.entity.Order;
import com.ebee.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommissionCalculationService {

    private final CommissionRepository commissionRepository;
    private final CommissionConfigRepository configRepository;
    private final OrderRepository orderRepository;
    private final CommissionMapper mapper;
    private final NotificationPort notificationPort;
    private final LedgerService ledgerService;
    private final SecurityService securityService;

    @Transactional
    public CommissionResponse calculateCommission(CommissionCalculateRequest request) {
        if (commissionRepository.existsByOrderId(request.getOrderId())) {
            throw new IllegalStateException("Commission already exists for order: " + request.getOrderId());
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        Commission commission = calculateCommissionForOrder(order);

        Agent agent = commission.getAgent();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("agentName", agent.getUser().getFirstName());
        placeholders.put("orderNumber", order.getOrderNumber());
        placeholders.put("totalAmount", NumberUtils.formatAmount(order.getTotalAmount()));
        placeholders.put("commissionAmount", NumberUtils.formatAmount(commission.getCommissionAmount()));
        placeholders.put("commissionRate", commission.getCommissionRate().toPlainString());
        placeholders.put("savingsContext", "");
        placeholders.put("savingsContextTxt", "");
        Set<String> unescaped = Set.of("savingsContext");
        notificationPort.notifyAgent(agent, "Commission: " + order.getOrderNumber(),
                "order-commission-calculated", placeholders, unescaped);
        notificationPort.createInternalAlert(agent.getUser(),
                "Commission: " + order.getOrderNumber(),
                """
                        A commission of LKR %s has been calculated for order %s.
                        
                        Order Amount: LKR %s
                        Rate: %s%%
                        Commission: LKR %s
                        """.formatted(
                        NumberUtils.formatAmount(commission.getCommissionAmount()),
                        order.getOrderNumber(),
                        NumberUtils.formatAmount(order.getTotalAmount()),
                        commission.getCommissionRate().toPlainString(),
                        NumberUtils.formatAmount(commission.getCommissionAmount())
                ),
                AlertType.COMMISSION_CALCULATED, commission.getId(), "commission");

        return mapper.toResponse(commission);
    }

    @Transactional
    public Commission calculateCommissionForOrder(Order order) {
        if (order.getAgent() == null) {
            throw new IllegalStateException("Order has no agent, cannot calculate commission");
        }

        Agent agent = order.getAgent();
        if (!Boolean.TRUE.equals(agent.getIsActive())) {
            throw new IllegalArgumentException("Agent is not active");
        }
        CommissionConfig config = findApplicableConfig(agent);

        BigDecimal commissionRate = resolveCommissionRate(agent, config);

        // Commission is earned on sales before VAT (VAT isn't revenue). Same as total when VAT is off.
        BigDecimal netSales = order.getTotalAmount().subtract(order.getTaxTotal());
        BigDecimal commissionAmount = netSales
                .multiply(commissionRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Commission commission = Commission.builder()
                .agent(agent)
                .order(order)
                .config(config)
                .orderAmount(netSales)
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .status(CommissionStatus.CALCULATED)
                .calculatedAt(LocalDateTime.now())
                .build();

        Commission saved = commissionRepository.save(commission);

        ledgerService.recordAgentCommission(
                agent.getId(),
                saved.getId(),
                commissionAmount,
                "Commission calculated for Order " + order.getOrderNumber(),
                securityService.getCurrentUserId()
        );

        return saved;
    }

    private CommissionConfig findApplicableConfig(Agent agent) {
        if (agent.getCommissionType() == AgentCommissionType.PERFORMANCE) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        return configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT)
                .filter(config -> isConfigEffective(config, now))
                .orElseThrow(() -> new IllegalStateException(
                        "No active default commission config found. " +
                        "Please configure a default commission rate before creating orders for DEFAULT agents."));
    }

    private BigDecimal resolveCommissionRate(Agent agent, CommissionConfig config) {
        if (agent.getCommissionType() == AgentCommissionType.PERFORMANCE) {
            return agent.getCommissionRate();
        }
        return config.getCommissionRate();
    }

    private boolean isConfigEffective(CommissionConfig config, LocalDateTime now) {
        if (config.getEffectiveFrom() != null && config.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        if (config.getEffectiveTo() != null && config.getEffectiveTo().isBefore(now)) {
            return false;
        }
        return true;
    }
}
