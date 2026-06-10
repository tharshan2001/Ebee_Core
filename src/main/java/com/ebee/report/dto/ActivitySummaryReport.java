package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ActivitySummaryReport {
    private Long totalActions;
    private List<ActionBreakdown> actionBreakdown;
    private PeriodRange period;

    @Data
    @Builder
    public static class ActionBreakdown {
        private String action;
        private Long count;
        private Long uniqueUsers;
    }
}
