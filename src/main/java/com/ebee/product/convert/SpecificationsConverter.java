package com.ebee.product.convert;

import com.ebee.product.dto.ProductSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Converter(autoApply = false)
public class SpecificationsConverter implements AttributeConverter<List<ProductSpecification>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<ProductSpecification> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize specifications", e);
        }
    }

    @Override
    public List<ProductSpecification> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<ProductSpecification>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize specifications: {}", dbData, e);
            return new ArrayList<>();
        }
    }
}
