package com.ebee.sales.service;

import com.ebee.commission.repository.CommissionRepository;
import com.ebee.order.repository.OrderRepository;
import com.ebee.sales.dto.AgentDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentDashboardService {

    private final OrderRepository orderRepository;
    private final CommissionRepository commissionRepository;

    public AgentDashboardResponse getStats(java.util.UUID agentId) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime weekStartDateTime = weekStart.atStartOfDay();
        LocalDateTime weekEndDateTime = today.atTime(LocalTime.MAX);

        long ordersToday = orderRepository.findByAgentId(agentId).stream()
                .filter(o -> {
                    LocalDateTime createdAt = o.getCreatedAt();
                    return createdAt != null && !createdAt.isBefore(todayStart) && !createdAt.isAfter(todayEnd);
                })
                .count();

        BigDecimal totalCommission = commissionRepository.sumCommissionAmountByAgentId(agentId);

        BigDecimal thisWeekCommission = commissionRepository.sumCommissionAmountByAgentIdAndDateRange(
                agentId, weekStartDateTime, weekEndDateTime);

        return new AgentDashboardResponse(ordersToday, totalCommission, thisWeekCommission);
    }
}
