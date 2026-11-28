package com.ebee.report.service;

import com.ebee.accounting.entity.AgentLedger;
import com.ebee.accounting.entity.CustomerLedger;
import com.ebee.accounting.entity.SupplierLedger;
import com.ebee.accounting.repository.AgentLedgerRepository;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.util.DateUtils;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.product.repository.ProductRepository;
import com.ebee.report.dto.AgingReportResponse;
import com.ebee.report.dto.ReportSummaryResponse;
import com.ebee.report.dto.TrialBalanceResponse;
import com.ebee.report.dto.TrialBalanceResponse.*;
import com.ebee.report.dto.AgingReportResponse.*;
import com.ebee.report.dto.ReportSummaryResponse.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final CustomerLedgerRepository customerLedgerRepository;
    private final SupplierLedgerRepository supplierLedgerRepository;
    private final AgentLedgerRepository agentLedgerRepository;

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

    public TrialBalanceResponse getTrialBalance() {
        LocalDateTime now = LocalDateTime.now();

        List<CustomerLedger> customerEntries = customerLedgerRepository.findAll();
        List<SupplierLedger> supplierEntries = supplierLedgerRepository.findAll();
        List<AgentLedger> agentEntries = agentLedgerRepository.findAll();

        BigDecimal totalCustomerDebit = BigDecimal.ZERO;
        BigDecimal totalCustomerCredit = BigDecimal.ZERO;
        List<LedgerEntry> customerLedgerEntries = new ArrayList<>();

        for (CustomerLedger l : customerEntries) {
            BigDecimal debit = l.getDebit() != null ? l.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = l.getCredit() != null ? l.getCredit() : BigDecimal.ZERO;
            BigDecimal balance = debit.subtract(credit);

            totalCustomerDebit = totalCustomerDebit.add(debit);
            totalCustomerCredit = totalCustomerCredit.add(credit);

            String entityCode = customerRepository.findById(l.getCustomerId())
                    .map(Customer::getCustomerCode).orElse("N/A");

            customerLedgerEntries.add(LedgerEntry.builder()
                    .entityId(l.getCustomerId().toString())
                    .entityCode(entityCode)
                    .entityName(l.getDescription())
                    .transactionType(l.getTransactionType())
                    .debit(debit)
                    .credit(credit)
                    .balance(balance)
                    .balanceType(balance.compareTo(BigDecimal.ZERO) >= 0 ? "DR" : "CR")
                    .description(l.getDescription())
                    .createdAt(l.getCreatedAt())
                    .build());
        }

        BigDecimal totalSupplierDebit = BigDecimal.ZERO;
        BigDecimal totalSupplierCredit = BigDecimal.ZERO;
        List<LedgerEntry> supplierLedgerEntries = new ArrayList<>();

        for (SupplierLedger l : supplierEntries) {
            BigDecimal debit = l.getDebit() != null ? l.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = l.getCredit() != null ? l.getCredit() : BigDecimal.ZERO;
            BigDecimal balance = credit.subtract(debit);

            totalSupplierDebit = totalSupplierDebit.add(debit);
            totalSupplierCredit = totalSupplierCredit.add(credit);

            supplierLedgerEntries.add(LedgerEntry.builder()
                    .entityId(l.getSupplierId().toString())
                    .entityCode("N/A")
                    .entityName(l.getDescription())
                    .transactionType(l.getTransactionType())
                    .debit(debit)
                    .credit(credit)
                    .balance(balance)
                    .balanceType(balance.compareTo(BigDecimal.ZERO) >= 0 ? "CR" : "DR")
                    .description(l.getDescription())
                    .createdAt(l.getCreatedAt())
                    .build());
        }

        BigDecimal totalAgentDebit = BigDecimal.ZERO;
        BigDecimal totalAgentCredit = BigDecimal.ZERO;
        List<LedgerEntry> agentLedgerEntries = new ArrayList<>();

        for (AgentLedger l : agentEntries) {
            BigDecimal debit = l.getDebit() != null ? l.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = l.getCredit() != null ? l.getCredit() : BigDecimal.ZERO;
            BigDecimal balance = credit.subtract(debit);

            totalAgentDebit = totalAgentDebit.add(debit);
            totalAgentCredit = totalAgentCredit.add(credit);

            String entityCode = agentRepository.findById(l.getAgentId())
                    .map(Agent::getAgentCode).orElse("N/A");

            agentLedgerEntries.add(LedgerEntry.builder()
                    .entityId(l.getAgentId().toString())
                    .entityCode(entityCode)
                    .entityName(l.getDescription())
                    .transactionType(l.getTransactionType())
                    .debit(debit)
                    .credit(credit)
                    .balance(balance)
                    .balanceType(balance.compareTo(BigDecimal.ZERO) >= 0 ? "CR" : "DR")
                    .description(l.getDescription())
                    .createdAt(l.getCreatedAt())
                    .build());
        }

        return TrialBalanceResponse.builder()
                .generatedAt(now)
                .period(PeriodSummary.builder()
                        .month(now.getMonthValue())
                        .year(now.getYear())
                        .status("CURRENT")
                        .build())
                .customerLedgers(customerLedgerEntries)
                .supplierLedgers(supplierLedgerEntries)
                .agentLedgers(agentLedgerEntries)
                .totals(Totals.builder()
                        .totalDebit(totalCustomerDebit.add(totalSupplierDebit).add(totalAgentDebit))
                        .totalCredit(totalCustomerCredit.add(totalSupplierCredit).add(totalAgentCredit))
                        .totalCustomerBalance(totalCustomerDebit.subtract(totalCustomerCredit))
                        .totalSupplierBalance(totalSupplierCredit.subtract(totalSupplierDebit))
                        .totalAgentBalance(totalAgentCredit.subtract(totalAgentDebit))
                        .build())
                .build();
    }

    public AgingReportResponse getAgingReport() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        List<Customer> customersWithDues = customerRepository.findAllWithOutstandingBalance();

        BigDecimal totalCurrent = BigDecimal.ZERO;
        BigDecimal total30 = BigDecimal.ZERO;
        BigDecimal total60 = BigDecimal.ZERO;
        BigDecimal total90 = BigDecimal.ZERO;
        BigDecimal total90Plus = BigDecimal.ZERO;

        List<CustomerAgingBucket> buckets = new ArrayList<>();

        for (Customer customer : customersWithDues) {
            List<Order> unpaidOrders = orderRepository.findByCustomerIdAndPaymentStatusInWithDetails(
                    customer.getId(),
                    List.of(PaymentStatus.UNPAID, PaymentStatus.PARTIALLY_PAID)
            );

            // Dues that aren't on any order: opening balances and manual adjustments.
            BigDecimal orderDues = unpaidOrders.stream().map(Order::getOutstandingBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<CustomerLedger> offOrder = customerLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                    .stream()
                    .filter(l -> l.getOrderId() == null && l.getPaymentId() == null && l.getReturnId() == null
                            && !"ADVANCE".equals(l.getTransactionType()))
                    .toList();
            BigDecimal offOrderDues = offOrder.stream().map(l -> l.getDebit().subtract(l.getCredit()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .min(customer.getOutstandingBalance().subtract(orderDues))
                    .max(BigDecimal.ZERO);

            if (unpaidOrders.isEmpty() && offOrderDues.signum() == 0) {
                continue;
            }

            BigDecimal current = BigDecimal.ZERO;
            BigDecimal days30 = BigDecimal.ZERO;
            BigDecimal days60 = BigDecimal.ZERO;
            BigDecimal days90 = BigDecimal.ZERO;
            BigDecimal days90Plus = BigDecimal.ZERO;
            LocalDate oldestDate = null;

            List<Map.Entry<LocalDate, BigDecimal>> dues = new ArrayList<>();
            unpaidOrders.forEach(o -> dues.add(Map.entry(o.getCreatedAt().toLocalDate(), o.getOutstandingBalance())));
            if (offOrderDues.signum() > 0) {
                LocalDate since = offOrder.stream()
                        .filter(l -> l.getDebit().signum() > 0)
                        .map(l -> l.getEffectiveDate() != null ? l.getEffectiveDate() : l.getCreatedAt().toLocalDate())
                        .min(LocalDate::compareTo)
                        .orElse(today);
                dues.add(Map.entry(since, offOrderDues));
            }

            for (Map.Entry<LocalDate, BigDecimal> due : dues) {
                LocalDate orderDate = due.getKey();
                long daysDiff = ChronoUnit.DAYS.between(orderDate, today);
                BigDecimal outstanding = due.getValue();

                if (oldestDate == null || orderDate.isBefore(oldestDate)) {
                    oldestDate = orderDate;
                }

                if (daysDiff <= 0) {
                    current = current.add(outstanding);
                } else if (daysDiff <= 30) {
                    days30 = days30.add(outstanding);
                } else if (daysDiff <= 60) {
                    days60 = days60.add(outstanding);
                } else if (daysDiff <= 90) {
                    days90 = days90.add(outstanding);
                } else {
                    days90Plus = days90Plus.add(outstanding);
                }
            }

            BigDecimal totalOutstanding = current.add(days30).add(days60).add(days90).add(days90Plus);
            int daysOutstanding = oldestDate != null ? (int) ChronoUnit.DAYS.between(oldestDate, today) : 0;

            totalCurrent = totalCurrent.add(current);
            total30 = total30.add(days30);
            total60 = total60.add(days60);
            total90 = total90.add(days90);
            total90Plus = total90Plus.add(days90Plus);

            buckets.add(CustomerAgingBucket.builder()
                    .customerId(customer.getId().toString())
                    .customerCode(customer.getCustomerCode())
                    .customerName(customer.getFirstName() + " " + customer.getLastName())
                    .phoneNumber(customer.getPhoneNumber())
                    .current(current)
                    .days30(days30)
                    .days60(days60)
                    .days90(days90)
                    .days90Plus(days90Plus)
                    .totalOutstanding(totalOutstanding)
                    .oldestInvoiceDate(oldestDate)
                    .daysOutstanding(daysOutstanding)
                    .build());
        }

        return AgingReportResponse.builder()
                .generatedAt(now)
                .summary(AgingSummary.builder()
                        .totalOutstanding(totalCurrent.add(total30).add(total60).add(total90).add(total90Plus))
                        .current(totalCurrent)
                        .days30(total30)
                        .days60(total60)
                        .days90(total90)
                        .days90Plus(total90Plus)
                        .totalCustomers((long) buckets.size())
                        .build())
                .customerBuckets(buckets)
                .build();
    }
}
