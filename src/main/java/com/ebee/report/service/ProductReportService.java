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
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.NumberUtils;
import com.ebee.sales.projection.ProductSalesProjection;
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
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<ProductSalesProjection> results = orderItemRepository.findSalesByProduct(start, end);
        List<TopSellerReport> list = new ArrayList<>();
        int rank = 1;

        for (ProductSalesProjection p : results) {
            if (limit > 0 && list.size() >= limit) break;
            list.add(TopSellerReport.builder()
                    .productId(p.getProductId())
                    .productName(p.getProductName())
                    .itemCode(p.getItemCode())
                    .category(p.getCategory())
                    .quantitySold(p.getTotalQuantity())
                    .revenue(p.getTotalRevenue())
                    .rank(rank++)
                    .build());
        }
        return list;
    }

    public List<ProductByCategoryReport> getByCategory(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<Object[]> results = productRepository.findProductCategorySummary(start, end);
        List<ProductByCategoryReport> list = new ArrayList<>();
        for (Object[] row : results) {
            list.add(ProductByCategoryReport.builder()
                    .category((String) row[0])
                    .productCount(NumberUtils.toLong(row[1]))
                    .quantitySold(NumberUtils.toLong(row[2]))
                    .revenue(NumberUtils.toBigDecimal(row[3]))
                    .build());
        }
        return list;
    }

}
