package com.ebee.combo.dto;

import com.ebee.common.enums.DiscountType;
import com.ebee.combo.enums.ComboRuleType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ComboRuleRequest {

    @NotNull(message = "Rule type is required")
    private ComboRuleType ruleType;

    private UUID freeProductId;

    private DiscountType discountType;

    private BigDecimal discountValue;
}
