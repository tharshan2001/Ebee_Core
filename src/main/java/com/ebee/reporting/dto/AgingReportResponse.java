package com.ebee.reporting.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AgingReportResponse {

    private LocalDateTime generatedAt;
    private AgingSummary summary;
    private List<CustomerAgingBucket> customerBuckets;

    @Data
    @Builder
    public static class AgingSummary {
        private BigDecimal totalOutstanding;
        private BigDecimal current;
        private BigDecimal days30;
        private BigDecimal days60;
        private BigDecimal days90;
        private BigDecimal days90Plus;
        private Long totalCustomers;
    }

    @Data
    @Builder
    public static class CustomerAgingBucket {
        private String customerId;
        private String customerCode;
        private String customerName;
        private String phoneNumber;
        private BigDecimal current;
        private BigDecimal days30;
        private BigDecimal days60;
        private BigDecimal days90;
        private BigDecimal days90Plus;
        private BigDecimal totalOutstanding;
        private LocalDate oldestInvoiceDate;
        private Integer daysOutstanding;
    }
}
