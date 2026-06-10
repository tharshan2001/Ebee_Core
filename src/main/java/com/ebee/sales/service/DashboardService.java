package com.ebee.sales.service;

import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.util.DateUtils;
import com.ebee.order.repository.OrderRepository;
import com.ebee.sales.dto.DashboardPeriod;
import com.ebee.sales.dto.DashboardPreviousPeriod;
import com.ebee.sales.dto.DashboardResponse;
import com.ebee.sales.dto.DashboardSummary;
import com.ebee.sales.dto.DashboardToday;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final OrderRepository orderRepository;
    private final CommissionRepository commissionRepository;

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

        LocalDateTime curStart = DateUtils.toStartDateTime(startDate);
        LocalDateTime curEnd = DateUtils.toEndDateTime(endDate);
        LocalDateTime prevStartDt = DateUtils.toStartDateTime(prevStart);
        LocalDateTime prevEndDt = DateUtils.toEndDateTime(prevEnd);

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
}
