package com.ebee.sales.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.NumberUtils;
import com.ebee.order.repository.OrderItemRepository;
import com.ebee.order.repository.OrderRepository;
import com.ebee.sales.dto.*;
import com.ebee.sales.projection.CategorySalesProjection;
import com.ebee.sales.projection.ProductSalesProjection;
import com.ebee.sales.projection.SalesSummaryProjection;
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
public class SalesService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommissionRepository commissionRepository;
    private final AgentRepository agentRepository;

    public SalesSummaryResponse getSalesSummary(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = DateUtils.toStartDateTime(startDate);
        LocalDateTime end = DateUtils.toEndDateTime(endDate);

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

    public List<SalesByAgentResponse> getSalesByAgent(LocalDate startDate, LocalDate endDate) {
        return orderRepository.findSalesByAgent(
                        DateUtils.toStartDateTime(startDate), DateUtils.toEndDateTime(endDate))
                .stream()
                .filter(p -> p.getAgentId() != null)
                .map(p -> SalesByAgentResponse.builder()
                        .agentId(p.getAgentId())
                        .agentCode(p.getAgentCode())
                        .agentName((p.getFirstName() != null ? p.getFirstName() : "")
                                + " " + (p.getLastName() != null ? p.getLastName() : ""))
                        .totalOrders(p.getTotalOrders())
                        .totalRevenue(p.getTotalRevenue())
                        .build())
                .toList();
    }

    public SalesByAgentDetailResponse getSalesByAgentDetail(UUID agentId, LocalDate startDate, LocalDate endDate) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));

        List<SalesSummaryProjection> result = orderRepository.findSalesSummaryByAgent(agentId,
                DateUtils.toStartDateTime(startDate), DateUtils.toEndDateTime(endDate));
        BigDecimal revenue = BigDecimal.ZERO;
        Long count = 0L;

        if (!result.isEmpty()) {
            SalesSummaryProjection p = result.get(0);
            revenue = p.getTotalRevenue();
            count = p.getTotalOrders();
        }

        BigDecimal commission = commissionRepository.sumCommissionAmountByAgentIdAndDateRange(agentId,
                DateUtils.toStartDateTime(startDate), DateUtils.toEndDateTime(endDate));

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
        return orderItemRepository.findSalesByProduct(
                        DateUtils.toStartDateTime(startDate), DateUtils.toEndDateTime(endDate))
                .stream()
                .map(p -> SalesByProductResponse.builder()
                        .productId(p.getProductId())
                        .productName(p.getProductName())
                        .itemCode(p.getItemCode())
                        .category(p.getCategory())
                        .totalQuantity(p.getTotalQuantity())
                        .totalRevenue(p.getTotalRevenue())
                        .build())
                .toList();
    }

    public List<SalesByCategoryResponse> getSalesByCategory(LocalDate startDate, LocalDate endDate) {
        List<CategorySalesProjection> results = orderItemRepository.findSalesByCategory(
                DateUtils.toStartDateTime(startDate), DateUtils.toEndDateTime(endDate));

        BigDecimal totalRevenue = results.stream()
                .map(CategorySalesProjection::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return results.stream()
                .map(p -> {
                    BigDecimal rev = p.getTotalRevenue();
                    BigDecimal percentage = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                            ? rev.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;
                    return SalesByCategoryResponse.builder()
                            .category(p.getCategory())
                            .totalQuantity(p.getTotalQuantity())
                            .totalRevenue(rev)
                            .percentageOfTotal(percentage)
                            .build();
                })
                .toList();
    }

    public List<SalesTrendResponse> getDailyTrend(LocalDate startDate, LocalDate endDate) {
        return mapTrendResults(
                orderRepository.findDailyTrend(DateUtils.toStartDateTime(startDate), DateUtils.toEndDateTime(endDate)));
    }

    public List<SalesTrendResponse> getMonthlyTrend(LocalDate startDate, LocalDate endDate) {
        return mapTrendResults(
                orderRepository.findMonthlyTrend(DateUtils.toStartDateTime(startDate), DateUtils.toEndDateTime(endDate)));
    }

    public List<SalesTrendResponse> getYearlyTrend(LocalDate startDate, LocalDate endDate) {
        return mapTrendResults(
                orderRepository.findYearlyTrend(DateUtils.toStartDateTime(startDate), DateUtils.toEndDateTime(endDate)));
    }

    private List<SalesTrendResponse> mapTrendResults(List<Object[]> results) {
        return results.stream()
                .map(row -> SalesTrendResponse.builder()
                        .period(NumberUtils.toLocalDateTime(row[0]))
                        .totalOrders(NumberUtils.toLong(row[1]))
                        .totalRevenue(NumberUtils.toBigDecimal(row[2]))
                        .build())
                .toList();
    }
}
