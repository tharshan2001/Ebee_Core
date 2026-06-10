package com.ebee.report.service;

import com.ebee.commission.repository.CommissionRepository;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.repository.OrderItemRepository;
import com.ebee.order.repository.OrderRepository;
import com.ebee.report.dto.*;
import com.ebee.report.util.PeriodUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.NumberUtils;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommissionRepository commissionRepository;

    public SalesSummaryReport getSummary(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        BigDecimal revenue = orderRepository.sumRevenueByDateRange(start, end);
        Long count = orderRepository.countOrdersByDateRange(start, end);
        BigDecimal avg = count > 0
                ? revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal commission = commissionRepository.sumCommissionLockedByDateRange(start, end);

        PeriodRange prevRange = PeriodUtils.previousPeriod(range);
        LocalDateTime prevStart = DateUtils.toStartDateTime(prevRange.startDate());
        LocalDateTime prevEnd = DateUtils.toEndDateTime(prevRange.endDate());
        BigDecimal prevRevenue = orderRepository.sumRevenueByDateRange(prevStart, prevEnd);
        Long prevCount = orderRepository.countOrdersByDateRange(prevStart, prevEnd);
        BigDecimal prevCommission = commissionRepository.sumCommissionLockedByDateRange(prevStart, prevEnd);

        SalesSummaryReport.PreviousPeriodComparison comparison = SalesSummaryReport.PreviousPeriodComparison.builder()
                .totalRevenue(prevRevenue)
                .totalOrders(prevCount)
                .totalCommission(prevCommission)
                .revenueChangePercent(calcChangePercent(revenue, prevRevenue))
                .ordersChangePercent(calcChangePercent(BigDecimal.valueOf(count), BigDecimal.valueOf(prevCount)))
                .build();

        return SalesSummaryReport.builder()
                .totalRevenue(revenue)
                .totalOrders(count)
                .averageOrderValue(avg)
                .totalCommission(commission)
                .period(range)
                .previousPeriod(comparison)
                .build();
    }

    public List<SalesTrendReport> getTrend(PeriodRange range, String granularity) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<Object[]> results = switch (granularity != null ? granularity : "monthly") {
            case "daily" -> orderRepository.findDailyTrend(start, end);
            case "weekly" -> orderRepository.findDailyTrend(start, end);
            case "yearly" -> orderRepository.findYearlyTrend(start, end);
            default -> orderRepository.findMonthlyTrend(start, end);
        };

        List<SalesTrendReport> list = new ArrayList<>();
        for (Object[] row : results) {
            list.add(SalesTrendReport.builder()
                    .period(toLocalDate(row[0]))
                    .totalOrders(NumberUtils.toLong(row[1]))
                    .totalRevenue(NumberUtils.toBigDecimal(row[2]))
                    .build());
        }
        return list;
    }

    public SalesBySourceReport getBySource(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<Object[]> results = orderRepository.findSalesBySource(start, end);
        BigDecimal agentRevenue = BigDecimal.ZERO;
        Long agentOrders = 0L;
        BigDecimal directRevenue = BigDecimal.ZERO;
        Long directOrders = 0L;

        for (Object[] row : results) {
            String source = row[0] != null ? row[0].toString() : "";
            Long orders = NumberUtils.toLong(row[1]);
            BigDecimal rev = NumberUtils.toBigDecimal(row[2]);
            if ("AGENT".equals(source) || OrderSource.AGENT.name().equals(source)) {
                agentRevenue = rev;
                agentOrders = orders;
            } else {
                directRevenue = rev;
                directOrders = orders;
            }
        }

        BigDecimal total = agentRevenue.add(directRevenue);

        SalesBySourceReport.SourceData agentData = SalesBySourceReport.SourceData.builder()
                .totalRevenue(agentRevenue)
                .totalOrders(agentOrders)
                .percentageOfRevenue(total.compareTo(BigDecimal.ZERO) > 0
                        ? agentRevenue.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO)
                .build();

        SalesBySourceReport.SourceData directData = SalesBySourceReport.SourceData.builder()
                .totalRevenue(directRevenue)
                .totalOrders(directOrders)
                .percentageOfRevenue(total.compareTo(BigDecimal.ZERO) > 0
                        ? directRevenue.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO)
                .build();

        return SalesBySourceReport.builder()
                .agent(agentData)
                .direct(directData)
                .build();
    }

    public List<SalesByStatusReport> getByStatus(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<Object[]> results = orderRepository.findOrderStatusDistribution(start, end);
        Long totalCount = orderRepository.countTotalOrdersByDateRange(start, end);
        List<SalesByStatusReport> list = new ArrayList<>();

        for (Object[] row : results) {
            String status = row[0] != null ? row[0].toString() : "UNKNOWN";
            Long count = NumberUtils.toLong(row[1]);
            BigDecimal rev = NumberUtils.toBigDecimal(row[2]);
            BigDecimal pct = totalCount > 0
                    ? BigDecimal.valueOf(count).divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            list.add(SalesByStatusReport.builder()
                    .status(status)
                    .count(count)
                    .totalRevenue(rev)
                    .percentage(pct)
                    .build());
        }
        return list;
    }

    private BigDecimal calcChangePercent(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (obj instanceof java.sql.Date d) return d.toLocalDate();
        if (obj instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (obj instanceof LocalDate d) return d;
        return null;
    }
}
