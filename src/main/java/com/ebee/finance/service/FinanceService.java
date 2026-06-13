package com.ebee.finance.service;

import com.ebee.common.util.DateUtils;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.finance.dto.CategoryBreakdown;
import com.ebee.finance.dto.FinanceSummaryResponse;
import com.ebee.finance.dto.PeriodInfo;
import com.ebee.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceService {

    private final OrderRepository orderRepository;
    private final CommissionRepository commissionRepository;
    private final ExpenseRepository expenseRepository;

    public FinanceSummaryResponse getSummary(LocalDate start, LocalDate end) {
        var startDt = DateUtils.toStartDateTime(start);
        var endDt = DateUtils.toEndDateTime(end);

        BigDecimal revenue = orderRepository.sumRevenueByDateRange(startDt, endDt);
        BigDecimal commissions = commissionRepository.sumCommissionLockedByDateRange(startDt, endDt);
        BigDecimal expenses = expenseRepository.sumApprovedByDateRange(start, end);
        BigDecimal netProfit = revenue.subtract(commissions).subtract(expenses);

        List<Object[]> categoryRows = expenseRepository.sumApprovedGroupByCategory(start, end);
        List<CategoryBreakdown> expenseByCategory = categoryRows.stream()
                .map(row -> new CategoryBreakdown(
                        row[0] instanceof Enum ? ((Enum<?>) row[0]).name() : String.valueOf(row[0]),
                        (BigDecimal) row[1]))
                .toList();

        return FinanceSummaryResponse.builder()
                .totalRevenue(revenue)
                .totalCommissions(commissions)
                .totalExpenses(expenses)
                .netProfit(netProfit)
                .period(PeriodInfo.builder().start(start).end(end).build())
                .expenseByCategory(expenseByCategory)
                .build();
    }
}
