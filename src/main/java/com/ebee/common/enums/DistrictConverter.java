package com.ebee.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DistrictConverter implements AttributeConverter<District, String> {

    @Override
    public String convertToDatabaseColumn(District district) {
        return district == null ? null : district.getDisplayName();
    }

    @Override
    public District convertToEntityAttribute(String dbData) {
        return dbData == null ? null : District.fromDisplayName(dbData);
    }
}
