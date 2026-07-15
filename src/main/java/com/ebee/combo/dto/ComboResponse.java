package com.ebee.combo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ComboResponse {

    private UUID id;
    private String name;
    private String description;
    private BigDecimal comboPrice;
    private Boolean isActive;
    private List<ComboItemResponse> items;
    private ComboRuleResponse rule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
