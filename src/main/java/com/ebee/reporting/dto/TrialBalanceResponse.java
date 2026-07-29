package com.ebee.reporting.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TrialBalanceResponse {

    private LocalDateTime generatedAt;
    private PeriodSummary period;
    private List<LedgerEntry> customerLedgers;
    private List<LedgerEntry> supplierLedgers;
    private List<LedgerEntry> agentLedgers;
    private Totals totals;

    @Data
    @Builder
    public static class PeriodSummary {
        private Integer month;
        private Integer year;
        private String status;
    }

    @Data
    @Builder
    public static class LedgerEntry {
        private String entityId;
        private String entityCode;
        private String entityName;
        private BigDecimal debit;
        private BigDecimal credit;
        private BigDecimal balance;
        private String balanceType;
    }

    @Data
    @Builder
    public static class Totals {
        private BigDecimal totalDebit;
        private BigDecimal totalCredit;
        private BigDecimal totalCustomerBalance;
        private BigDecimal totalSupplierBalance;
        private BigDecimal totalAgentBalance;
    }
}
