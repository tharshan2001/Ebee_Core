package com.ebee.report.service;

import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.order.repository.OrderItemRepository;
import com.ebee.order.repository.OrderRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.report.dto.CommissionLiabilityReport;
import com.ebee.report.dto.FinancialReport;
import com.ebee.report.dto.PeriodRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommissionRepository commissionRepository;
    private final ProductRepository productRepository;

    public FinancialReport getProfitLoss(PeriodRange range) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        BigDecimal revenue = orderRepository.sumRevenueByDateRange(start, end);

        // Build product cost map
        List<Product> allProducts = productRepository.findAllWithInventory();
        Map<UUID, BigDecimal> costMap = new HashMap<>();
        for (Product p : allProducts) {
            costMap.put(p.getId(), p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO);
        }

        List<Object[]> itemResults = orderItemRepository.findSalesByProduct(start, end);
        BigDecimal costPrice = BigDecimal.ZERO;
        for (Object[] row : itemResults) {
            UUID productId = (UUID) row[0];
            BigDecimal qty = BigDecimal.valueOf(toLong(row[4]));
            BigDecimal unitCost = costMap.getOrDefault(productId, BigDecimal.ZERO);
            costPrice = costPrice.add(unitCost.multiply(qty));
        }

        BigDecimal grossProfit = revenue.subtract(costPrice);
        BigDecimal grossMargin = revenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal commissions = commissionRepository.sumCommissionLockedByDateRange(start, end);
        BigDecimal netProfit = grossProfit.subtract(commissions);

        return FinancialReport.builder()
                .totalRevenue(revenue)
                .totalCostPrice(costPrice)
                .grossProfit(grossProfit)
                .grossMargin(grossMargin.setScale(2, RoundingMode.HALF_UP))
                .totalCommissions(commissions)
                .netProfit(netProfit)
                .period(range)
                .build();
    }

    public CommissionLiabilityReport getCommissionLiability() {
        BigDecimal approved = commissionRepository.sumCommissionAmountByStatus(CommissionStatus.APPROVED);
        BigDecimal processing = commissionRepository.sumCommissionAmountByStatus(CommissionStatus.PROCESSING);
        long approvedCount = commissionRepository.findByStatus(CommissionStatus.APPROVED).size();
        long processingCount = commissionRepository.findByStatus(CommissionStatus.PROCESSING).size();

        return CommissionLiabilityReport.builder()
                .totalUnpaid(approved.add(processing))
                .approvedAmount(approved)
                .processingAmount(processing)
                .approvedCount(approvedCount)
                .processingCount(processingCount)
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
}
