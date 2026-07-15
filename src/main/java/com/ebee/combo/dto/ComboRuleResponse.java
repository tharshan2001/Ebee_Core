package com.ebee.combo.dto;

import com.ebee.common.enums.DiscountType;
import com.ebee.combo.enums.ComboRuleType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ComboRuleResponse {

    private UUID id;
    private ComboRuleType ruleType;
    private UUID freeProductId;
    private String freeProductName;
    private String freeProductItemCode;
    private BigDecimal freeProductPrice;
    private DiscountType discountType;
    private BigDecimal discountValue;
}
