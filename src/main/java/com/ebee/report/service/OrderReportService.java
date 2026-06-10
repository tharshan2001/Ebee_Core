package com.ebee.report.service;

import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.report.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderReportService {

    private final OrderRepository orderRepository;

    public OrderSummaryReport getSummary(PeriodRange range) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        List<Object[]> distribution = orderRepository.findOrderStatusDistribution(start, end);
        Long pending = 0L, shipped = 0L, delivered = 0L, cancelled = 0L, returned = 0L;
        for (Object[] row : distribution) {
            String status = row[0] != null ? row[0].toString() : "";
            Long count = toLong(row[1]);
            switch (status) {
                case "PENDING" -> pending = count;
                case "SHIPPED" -> shipped = count;
                case "DELIVERED" -> delivered = count;
                case "CANCELLED" -> cancelled = count;
                case "RETURNED" -> returned = count;
            }
        }

        Long total = pending + shipped + delivered + cancelled + returned;
        BigDecimal cancellationRate = total > 0
                ? BigDecimal.valueOf(cancelled).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return OrderSummaryReport.builder()
                .totalOrders(total)
                .pendingCount(pending)
                .shippedCount(shipped)
                .deliveredCount(delivered)
                .cancelledCount(cancelled)
                .returnedCount(returned)
                .cancellationRate(cancellationRate)
                .period(range)
                .build();
    }

    public List<TopCustomerReport> getTopCustomers(PeriodRange range, int limit) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        List<Object[]> results = orderRepository.findTopCustomersByRevenue(start, end);
        List<TopCustomerReport> list = new ArrayList<>();
        int rank = 1;

        for (Object[] row : results) {
            if (limit > 0 && list.size() >= limit) break;
            UUID customerId = (UUID) row[0];
            String name = (String) row[1];
            String phone = (String) row[2];
            Long count = toLong(row[3]);
            BigDecimal spent = toBigDecimal(row[4]);
            BigDecimal avg = count > 0
                    ? spent.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            list.add(TopCustomerReport.builder()
                    .customerId(customerId)
                    .customerName(name != null ? name : "")
                    .phoneNumber(phone != null ? phone : "")
                    .orderCount(count)
                    .totalSpent(spent)
                    .avgOrderValue(avg)
                    .rank(rank++)
                    .build());
        }
        return list;
    }

    public AvgDeliveryTimeReport getAvgDeliveryTime(PeriodRange range) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        List<Object[]> results = orderRepository.findAvgDeliveryTime(start, end);
        if (results.isEmpty()) {
            return AvgDeliveryTimeReport.builder()
                    .avgHours(0.0)
                    .minHours(0.0)
                    .maxHours(0.0)
                    .deliveredCount(0L)
                    .build();
        }

        Object[] row = results.get(0);
        return AvgDeliveryTimeReport.builder()
                .avgHours(row[0] != null ? ((Number) row[0]).doubleValue() : 0.0)
                .minHours(row[1] != null ? ((Number) row[1]).doubleValue() : 0.0)
                .maxHours(row[2] != null ? ((Number) row[2]).doubleValue() : 0.0)
                .deliveredCount(toLong(row[3]))
                .build();
    }

    private LocalDateTime toStartDateTime(LocalDate date) {
        return date != null ? date.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
    }

    private LocalDateTime toEndDateTime(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay() : LocalDateTime.of(9999, 12, 31, 23, 59);
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
