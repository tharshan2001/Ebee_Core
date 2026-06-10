package com.ebee.sales.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.NumberUtils;
import com.ebee.order.repository.OrderRepository;
import com.ebee.sales.dto.AgentLeaderboardResponse;
import com.ebee.sales.projection.AgentSalesProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderboardService {

    private final OrderRepository orderRepository;
    private final CommissionRepository commissionRepository;
    private final AgentRepository agentRepository;
    private final SecurityService securityService;

    public List<AgentLeaderboardResponse> getLeaderboard(
            String metric, int limit, District district,
            LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = DateUtils.toStartDateTime(startDate);
        LocalDateTime end = DateUtils.toEndDateTime(endDate);

        List<AgentSalesProjection> orderResults = orderRepository.findSalesByAgent(start, end);
        Map<UUID, AgentSalesProjection> orderMap = new HashMap<>();
        for (AgentSalesProjection p : orderResults) {
            orderMap.put(p.getAgentId(), p);
        }

        List<Agent> allAgents = agentRepository.findAllWithUser().stream()
                .filter(Agent::getIsActive)
                .toList();

        List<AgentLeaderboardResponse> entries = new ArrayList<>();
        List<UUID> allAgentIds = new ArrayList<>();
        for (Agent agent : allAgents) {
            allAgentIds.add(agent.getId());
            AgentSalesProjection proj = orderMap.get(agent.getId());
            Long orders = proj != null ? proj.getTotalOrders() : 0L;
            BigDecimal revenue = proj != null ? proj.getTotalRevenue() : BigDecimal.ZERO;
            entries.add(AgentLeaderboardResponse.builder()
                    .agentId(agent.getId())
                    .agentCode(agent.getAgentCode())
                    .agentName((agent.getUser().getFirstName() != null ? agent.getUser().getFirstName() : "")
                            + " " + (agent.getUser().getLastName() != null ? agent.getUser().getLastName() : ""))
                    .district(agent.getDistrict() != null ? agent.getDistrict().name() : "")
                    .totalOrders(orders)
                    .totalRevenue(revenue)
                    .avgOrderValue(orders > 0
                            ? revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO)
                    .build());
        }

        List<Object[]> commissionResults = commissionRepository.sumCommissionByAgentIds(
                allAgentIds, start, end);
        Map<UUID, BigDecimal> commissionMap = new HashMap<>();
        for (Object[] cr : commissionResults) {
            commissionMap.put((UUID) cr[0], NumberUtils.toBigDecimal(cr[1]));
        }
        for (AgentLeaderboardResponse entry : entries) {
            entry.setTotalCommission(commissionMap.getOrDefault(entry.getAgentId(), BigDecimal.ZERO));
        }

        if (district != null) {
            entries = new ArrayList<>(entries.stream()
                    .filter(e -> district.name().equals(e.getDistrict()))
                    .toList());
        }

        Comparator<AgentLeaderboardResponse> comparator = switch (metric != null ? metric : "revenue") {
            case "orders" -> Comparator.comparingLong(AgentLeaderboardResponse::getTotalOrders);
            case "commission" -> Comparator.comparing(AgentLeaderboardResponse::getTotalCommission);
            case "avgOrderValue" -> Comparator.comparing(AgentLeaderboardResponse::getAvgOrderValue);
            default -> Comparator.comparing(AgentLeaderboardResponse::getTotalRevenue);
        };

        entries.sort(comparator.reversed());

        if (limit > 0 && limit < entries.size()) {
            entries = entries.subList(0, limit);
        }

        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }

        return entries;
    }

    public List<AgentLeaderboardResponse> getMyRank(
            String metric, District district,
            LocalDate startDate, LocalDate endDate) {
        UUID currentUserId = securityService.getCurrentUserId();
        Agent agent = agentRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", currentUserId));
        UUID currentAgentId = agent.getId();

        List<AgentLeaderboardResponse> all = getLeaderboard(metric, 0, district, startDate, endDate);

        for (AgentLeaderboardResponse entry : all) {
            if (entry.getAgentId().equals(currentAgentId)) {
                entry.setCurrentUser(true);
            }
        }

        return all;
    }
}
