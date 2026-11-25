package com.ebee.tax.dto;

/** All fields optional; only the ones sent are changed. */
public record BusinessSettingsRequest(Boolean vatRegistered, String vatRegistrationNumber, Boolean pricesIncludeTax) {
}
