package com.ebee.tax.dto;

import com.ebee.tax.entity.BusinessSettings;

public record BusinessSettingsResponse(boolean vatRegistered, String vatRegistrationNumber, boolean pricesIncludeTax) {
    public static BusinessSettingsResponse of(BusinessSettings s) {
        return new BusinessSettingsResponse(s.isVatRegistered(), s.getVatRegistrationNumber(), s.isPricesIncludeTax());
    }
}
