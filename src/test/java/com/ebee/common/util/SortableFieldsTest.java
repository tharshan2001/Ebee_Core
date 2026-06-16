package com.ebee.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortableFieldsTest {

    @Test
    void validate_null_returnsDefault() {
        assertThat(SortableFields.validate(null, SortableFields.PRODUCT))
                .isEqualTo("createdAt,desc");
    }

    @Test
    void validate_blank_returnsDefault() {
        assertThat(SortableFields.validate("   ", SortableFields.PRODUCT))
                .isEqualTo("createdAt,desc");
    }

    @Test
    void validate_allowedFieldAsc_returnsCanonical() {
        assertThat(SortableFields.validate("name,asc", SortableFields.PRODUCT))
                .isEqualTo("name,asc");
    }

    @Test
    void validate_allowedFieldDesc_returnsCanonical() {
        assertThat(SortableFields.validate("price,desc", SortableFields.PRODUCT))
                .isEqualTo("price,desc");
    }

    @Test
    void validate_disallowedField_throws() {
        assertThatThrownBy(() -> SortableFields.validate("password,asc", SortableFields.PRODUCT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort field 'password'");
    }

    @Test
    void validate_fieldForDifferentEntity_throws() {
        // 'phoneNumber' is a customer field, not a product field
        assertThatThrownBy(() -> SortableFields.validate("phoneNumber,asc", SortableFields.PRODUCT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_orderEntity_allowsOrderFields() {
        assertThat(SortableFields.validate("orderNumber,asc", SortableFields.ORDER))
                .isEqualTo("orderNumber,asc");
        assertThat(SortableFields.validate("totalAmount,desc", SortableFields.ORDER))
                .isEqualTo("totalAmount,desc");
    }
}
