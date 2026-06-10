package com.ebee.report.service;

import com.ebee.order.repository.OrderItemRepository;
import com.ebee.product.repository.ProductRepository;
import com.ebee.report.dto.PeriodRange;
import com.ebee.report.dto.ProductByCategoryReport;
import com.ebee.report.dto.ProductSummaryReport;
import com.ebee.report.dto.TopSellerReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductReportService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductSummaryReport getSummary() {
        long total = productRepository.count();
        long active = productRepository.findAll().stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).count();
        long inactive = total - active;
        long categories = productRepository.countDistinctCategories();

        return ProductSummaryReport.builder()
                .totalProducts(total)
                .activeCount(active)
                .inactiveCount(inactive)
                .categoryCount(categories)
                .build();
    }

    public List<TopSellerReport> getTopSellers(PeriodRange range, int limit) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        List<Object[]> results = orderItemRepository.findSalesByProduct(start, end);
        List<TopSellerReport> list = new ArrayList<>();
        int rank = 1;

        for (Object[] row : results) {
            if (limit > 0 && list.size() >= limit) break;
            list.add(TopSellerReport.builder()
                    .productId((UUID) row[0])
                    .productName((String) row[1])
                    .itemCode((String) row[2])
                    .category((String) row[3])
                    .quantitySold(toLong(row[4]))
                    .revenue(toBigDecimal(row[5]))
                    .rank(rank++)
                    .build());
        }
        return list;
    }

    public List<ProductByCategoryReport> getByCategory(PeriodRange range) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        List<Object[]> results = productRepository.findProductCategorySummary(start, end);
        List<ProductByCategoryReport> list = new ArrayList<>();
        for (Object[] row : results) {
            list.add(ProductByCategoryReport.builder()
                    .category((String) row[0])
                    .productCount(toLong(row[1]))
                    .quantitySold(toLong(row[2]))
                    .revenue(toBigDecimal(row[3]))
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
