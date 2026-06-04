package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DashboardPeriod {
    private LocalDate start;
    private LocalDate end;
}
