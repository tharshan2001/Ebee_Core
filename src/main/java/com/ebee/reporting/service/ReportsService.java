package com.ebee.reporting.service;

import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.util.DateUtils;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.order.repository.OrderRepository;
import com.ebee.product.repository.ProductRepository;
import com.ebee.reporting.dto.ReportSummaryResponse;
import com.ebee.reporting.dto.ReportSummaryResponse.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportsService {

    private final OrderRepository orderRepository;
    private final CommissionRepository commissionRepository;
    private final ExpenseRepository expenseRepository;
    private final AgentRepository agentRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public ReportSummaryResponse getSummary(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            endDate = LocalDate.now();
            startDate = endDate.minusDays(30);
        } else if (startDate != null && endDate == null) {
            endDate = startDate.plusDays(30);
        } else if (startDate == null) {
            startDate = endDate.minusDays(30);
        }

        LocalDateTime curStart = DateUtils.toStartDateTime(startDate);
        LocalDateTime curEnd = DateUtils.toEndDateTime(endDate);

        SalesInfo salesInfo = buildSalesInfo(startDate, endDate, curStart, curEnd);
        FinanceInfo financeInfo = buildFinanceInfo(startDate, endDate);
        DashboardInfo dashboardInfo = buildDashboardInfo();

        return ReportSummaryResponse.builder()
                .period(PeriodInfo.builder().start(startDate).end(endDate).build())
                .sales(salesInfo)
                .finance(financeInfo)
                .dashboard(dashboardInfo)
                .build();
    }

    private SalesInfo buildSalesInfo(LocalDate startDate, LocalDate endDate, LocalDateTime curStart, LocalDateTime curEnd) {
        long periodDays = ChronoUnit.DAYS.between(startDate, endDate);
        LocalDate prevStart = startDate.minusDays(periodDays);
        LocalDate prevEnd = startDate;
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

        Long ordersToday = orderRepository.countOrdersByDateRangeAllStatuses(todayStart, todayEnd);
        Long ordersYesterday = orderRepository.countOrdersByDateRangeAllStatuses(yesterdayStart, todayStart);
        Long deliveriesToday = orderRepository.countDeliveredByDateRange(todayStart, todayEnd);

        PeriodComparison comparison = buildPeriodComparison(curRevenue, prevRevenue, curCount, prevCount);

        return SalesInfo.builder()
                .totalRevenue(curRevenue != null ? curRevenue : BigDecimal.ZERO)
                .totalOrders(curCount != null ? curCount : 0L)
                .averageOrderValue(curAvg)
                .totalCommission(curCommission != null ? curCommission : BigDecimal.ZERO)
                .ordersToday(ordersToday != null ? ordersToday : 0L)
                .ordersYesterday(ordersYesterday != null ? ordersYesterday : 0L)
                .deliveriesToday(deliveriesToday != null ? deliveriesToday : 0L)
                .previousPeriod(comparison)
                .build();
    }

    private PeriodComparison buildPeriodComparison(BigDecimal curRevenue, BigDecimal prevRevenue,
                                                   Long curCount, Long prevCount) {
        BigDecimal revChange = curRevenue.subtract(prevRevenue);
        BigDecimal revChangePct = prevRevenue.compareTo(BigDecimal.ZERO) > 0
                ? revChange.divide(prevRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Long ordersChange = curCount - prevCount;
        BigDecimal ordersChangePct = prevCount > 0
                ? BigDecimal.valueOf(ordersChange * 100.0 / prevCount)
                : BigDecimal.ZERO;

        return PeriodComparison.builder()
                .revenueChange(revChange)
                .revenueChangePercent(revChangePct)
                .ordersChange(ordersChange)
                .ordersChangePercent(ordersChangePct.longValue())
                .build();
    }

    private FinanceInfo buildFinanceInfo(LocalDate startDate, LocalDate endDate) {
        LocalDateTime curStart = DateUtils.toStartDateTime(startDate);
        LocalDateTime curEnd = DateUtils.toEndDateTime(endDate);

        BigDecimal collectedRevenue = orderRepository.sumCollectedRevenueByDateRange(curStart, curEnd);
        BigDecimal outstandingRevenue = orderRepository.sumOutstandingByDateRange(curStart, curEnd);
        BigDecimal expenses = expenseRepository.sumApprovedByDateRange(startDate, endDate);

        BigDecimal commissions = commissionRepository.sumCommissionLockedByDateRange(curStart, curEnd);
        BigDecimal netProfit = (collectedRevenue != null ? collectedRevenue : BigDecimal.ZERO)
                .subtract(commissions != null ? commissions : BigDecimal.ZERO)
                .subtract(expenses != null ? expenses : BigDecimal.ZERO);

        List<Object[]> categoryRows = expenseRepository.sumApprovedGroupByCategory(startDate, endDate);
        List<CategoryBreakdown> expenseByCategory = categoryRows.stream()
                .map(row -> CategoryBreakdown.builder()
                        .category(row[0] instanceof Enum ? ((Enum<?>) row[0]).name() : String.valueOf(row[0]))
                        .amount((BigDecimal) row[1])
                        .build())
                .toList();

        return FinanceInfo.builder()
                .collectedRevenue(collectedRevenue != null ? collectedRevenue : BigDecimal.ZERO)
                .outstandingRevenue(outstandingRevenue != null ? outstandingRevenue : BigDecimal.ZERO)
                .totalExpenses(expenses != null ? expenses : BigDecimal.ZERO)
                .netProfit(netProfit)
                .expenseByCategory(expenseByCategory)
                .build();
    }

    private DashboardInfo buildDashboardInfo() {
        Long activeAgents = agentRepository.countActive();
        Long activeCustomers = customerRepository.countActive();
        Long lowStockProducts = productRepository.countLowStockProducts();

        return DashboardInfo.builder()
                .activeAgents(activeAgents != null ? activeAgents : 0L)
                .activeCustomers(activeCustomers != null ? activeCustomers : 0L)
                .lowStockProducts(lowStockProducts != null ? lowStockProducts : 0L)
                .build();
    }
}
