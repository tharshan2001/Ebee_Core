package com.ebee.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter(autoApply = true)
public class DistrictConverter implements AttributeConverter<District, String> {

    @Override
    public String convertToDatabaseColumn(District district) {
        return district == null ? null : district.getDisplayName();
    }

    @Override
    public District convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return District.fromDisplayName(dbData);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown district value '{}' in database, returning null", dbData);
            return null;
        }
    }
}
