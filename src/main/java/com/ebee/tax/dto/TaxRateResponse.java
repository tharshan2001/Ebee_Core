package com.ebee.tax.dto;

import com.ebee.tax.entity.TaxRate;

import java.math.BigDecimal;
import java.util.UUID;

public record TaxRateResponse(UUID id, String name, BigDecimal rate, boolean active) {
    public static TaxRateResponse of(TaxRate r) {
        return new TaxRateResponse(r.getId(), r.getName(), r.getRate(), r.isActive());
    }
}
