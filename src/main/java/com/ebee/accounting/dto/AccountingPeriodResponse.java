package com.ebee.accounting.dto;

import com.ebee.accounting.enums.PeriodStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountingPeriodResponse(
    UUID id,
    Integer month,
    Integer year,
    PeriodStatus status,
    LocalDateTime openedAt,
    LocalDateTime closedAt,
    String reason
) {}
