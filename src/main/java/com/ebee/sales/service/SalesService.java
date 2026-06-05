package com.ebee.sales.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.order.repository.OrderItemRepository;
import com.ebee.order.repository.OrderRepository;
import com.ebee.sales.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommissionRepository commissionRepository;
    private final AgentRepository agentRepository;

    public SalesSummaryResponse getSalesSummary(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = toStartDateTime(startDate);
        LocalDateTime end = toEndDateTime(endDate);

        BigDecimal revenue = orderRepository.sumRevenueByDateRange(start, end);
        Long count = orderRepository.countOrdersByDateRange(start, end);
        BigDecimal avg = count > 0
                ? revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal commission = commissionRepository.sumCommissionLockedByDateRange(start, end);

        return SalesSummaryResponse.builder()
                .totalRevenue(revenue)
                .totalOrders(count)
                .averageOrderValue(avg)
                .totalCommission(commission)
                .periodStart(startDate != null ? startDate.atStartOfDay() : null)
                .periodEnd(endDate != null ? endDate.atStartOfDay() : null)
                .build();
    }

    public DashboardResponse getDashboard(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            endDate = LocalDate.now();
            startDate = endDate.minusDays(30);
        } else if (startDate != null && endDate == null) {
            endDate = startDate.plusDays(30);
        } else if (startDate == null) {
            startDate = endDate.minusDays(30);
        }

        long periodDays = ChronoUnit.DAYS.between(startDate, endDate);
        LocalDate prevStart = startDate.minusDays(periodDays);
        LocalDate prevEnd = startDate;

        LocalDateTime curStart = toStartDateTime(startDate);
        LocalDateTime curEnd = toEndDateTime(endDate);
        LocalDateTime prevStartDt = toStartDateTime(prevStart);
        LocalDateTime prevEndDt = toEndDateTime(prevEnd);

        BigDecimal curRevenue = orderRepository.sumRevenueByDateRange(curStart, curEnd);
        Long curCount = orderRepository.countOrdersByDateRange(curStart, curEnd);
        BigDecimal curAvg = curCount > 0
                ? curRevenue.divide(BigDecimal.valueOf(curCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal curCommission = commissionRepository.sumCommissionLockedByDateRange(curStart, curEnd);

        BigDecimal prevRevenue = orderRepository.sumRevenueByDateRange(prevStartDt, prevEndDt);
        Long prevCount = orderRepository.countOrdersByDateRange(prevStartDt, prevEndDt);
        BigDecimal prevCommission = commissionRepository.sumCommissionLockedByDateRange(prevStartDt, prevEndDt);

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = todayStart;

        Long ordersToday = orderRepository.countOrdersByDateRangeAllStatuses(todayStart, todayEnd);
        Long ordersYesterday = orderRepository.countOrdersByDateRangeAllStatuses(yesterdayStart, yesterdayEnd);
        Long deliveriesToday = orderRepository.countDeliveredByDateRange(todayStart, todayEnd);
        Long deliveriesYesterday = orderRepository.countDeliveredByDateRange(yesterdayStart, yesterdayEnd);

        return DashboardResponse.builder()
                .period(DashboardPeriod.builder().start(startDate).end(endDate).build())
                .summary(DashboardSummary.builder()
                        .totalRevenue(curRevenue)
                        .totalOrders(curCount)
                        .averageOrderValue(curAvg)
                        .totalCommission(curCommission)
                        .build())
                .today(DashboardToday.builder()
                        .ordersToday(ordersToday)
                        .ordersYesterday(ordersYesterday)
                        .deliveriesToday(deliveriesToday)
                        .deliveriesYesterday(deliveriesYesterday)
                        .build())
                .previousPeriod(DashboardPreviousPeriod.builder()
                        .totalRevenue(prevRevenue)
                        .totalOrders(prevCount)
                        .totalCommission(prevCommission)
                        .build())
                .build();
    }

    public List<SalesByAgentResponse> getSalesByAgent(LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = orderRepository.findSalesByAgent(toStartDateTime(startDate), toEndDateTime(endDate));
        List<SalesByAgentResponse> list = new ArrayList<>();

        for (Object[] row : results) {
            UUID agentId = (UUID) row[0];
            if (agentId == null) continue;

            String firstName = (String) row[2];
            String lastName = (String) row[3];

            list.add(SalesByAgentResponse.builder()
                    .agentId(agentId)
                    .agentCode((String) row[1])
                    .agentName((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : ""))
                    .totalOrders(toLong(row[4]))
                    .totalRevenue(toBigDecimal(row[5]))
                    .build());
        }

        return list;
    }

    public SalesByAgentDetailResponse getSalesByAgentDetail(UUID agentId, LocalDate startDate, LocalDate endDate) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));

        List<Object[]> result = orderRepository.findSalesSummaryByAgent(agentId,
                toStartDateTime(startDate), toEndDateTime(endDate));
        BigDecimal revenue = BigDecimal.ZERO;
        Long count = 0L;

        if (!result.isEmpty()) {
            Object[] row = result.get(0);
            revenue = toBigDecimal(row[0]);
            count = toLong(row[1]);
        }

        BigDecimal commission = commissionRepository.sumCommissionAmountByAgentIdAndDateRange(agentId,
                toStartDateTime(startDate), toEndDateTime(endDate));

        return SalesByAgentDetailResponse.builder()
                .agentId(agent.getId())
                .agentCode(agent.getAgentCode())
                .agentName((agent.getUser().getFirstName() != null ? agent.getUser().getFirstName() : "") + " " + (agent.getUser().getLastName() != null ? agent.getUser().getLastName() : ""))
                .totalOrders(count)
                .totalRevenue(revenue)
                .totalCommission(commission)
                .build();
    }

    public List<SalesByProductResponse> getSalesByProduct(LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = orderItemRepository.findSalesByProduct(
                toStartDateTime(startDate), toEndDateTime(endDate));
        List<SalesByProductResponse> list = new ArrayList<>();

        for (Object[] row : results) {
            list.add(SalesByProductResponse.builder()
                    .productId((UUID) row[0])
                    .productName((String) row[1])
                    .itemCode((String) row[2])
                    .category((String) row[3])
                    .totalQuantity(toLong(row[4]))
                    .totalRevenue(toBigDecimal(row[5]))
                    .build());
        }

        return list;
    }

    public List<SalesByCategoryResponse> getSalesByCategory(LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = orderItemRepository.findSalesByCategory(
                toStartDateTime(startDate), toEndDateTime(endDate));
        List<SalesByCategoryResponse> list = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Object[] row : results) {
            totalRevenue = totalRevenue.add(toBigDecimal(row[2]));
        }

        for (Object[] row : results) {
            BigDecimal rev = toBigDecimal(row[2]);
            BigDecimal percentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? rev.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            list.add(SalesByCategoryResponse.builder()
                    .category((String) row[0])
                    .totalQuantity(toLong(row[1]))
                    .totalRevenue(rev)
                    .percentageOfTotal(percentage)
                    .build());
        }

        return list;
    }

    public List<SalesTrendResponse> getDailyTrend(LocalDate startDate, LocalDate endDate) {
        return mapTrendResults(
                orderRepository.findDailyTrend(toStartDateTime(startDate), toEndDateTime(endDate)));
    }

    public List<SalesTrendResponse> getMonthlyTrend(LocalDate startDate, LocalDate endDate) {
        return mapTrendResults(
                orderRepository.findMonthlyTrend(toStartDateTime(startDate), toEndDateTime(endDate)));
    }

    public List<SalesTrendResponse> getYearlyTrend(LocalDate startDate, LocalDate endDate) {
        return mapTrendResults(
                orderRepository.findYearlyTrend(toStartDateTime(startDate), toEndDateTime(endDate)));
    }

    private List<SalesTrendResponse> mapTrendResults(List<Object[]> results) {
        List<SalesTrendResponse> list = new ArrayList<>();

        for (Object[] row : results) {
            list.add(SalesTrendResponse.builder()
                    .period(toLocalDateTime(row[0]))
                    .totalOrders(toLong(row[1]))
                    .totalRevenue(toBigDecimal(row[2]))
                    .build());
        }

        return list;
    }

    private LocalDateTime toStartDateTime(LocalDate date) {
        return date != null ? date.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
    }

    private LocalDateTime toEndDateTime(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay() : LocalDateTime.of(9999, 12, 31, 23, 59);
    }

    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj instanceof Timestamp ts) return ts.toLocalDateTime();
        if (obj instanceof LocalDateTime ldt) return ldt;
        return null;
    }

    private Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
