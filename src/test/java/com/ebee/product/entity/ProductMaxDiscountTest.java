package com.ebee.product.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductMaxDiscountTest {

    @Test
    void getPriceWithDiscount_noDiscountRequested_returnsOriginalPrice() {
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        // No per-order discount requested -> use the original price
        assertThat(product.getPriceWithDiscount(null, null)).isEqualByComparingTo("1000.00");
    }

    @Test
    void getPriceWithDiscount_withinCap_appliesDiscount() {
        // Cap is 20% PERCENTAGE, admin applies 10% -> 1000 * 0.9 = 900
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThat(product.getPriceWithDiscount("PERCENTAGE", new BigDecimal("10")))
                .isEqualByComparingTo("900.00");
    }

    @Test
    void getPriceWithDiscount_atCap_appliesDiscount() {
        // Cap is 20% PERCENTAGE, admin applies exactly 20% -> 1000 * 0.8 = 800
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThat(product.getPriceWithDiscount("PERCENTAGE", new BigDecimal("20")))
                .isEqualByComparingTo("800.00");
    }

    @Test
    void getPriceWithDiscount_exceedsCap_throws() {
        // Cap is 20% PERCENTAGE, admin tries 30% -> rejected
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThatThrownBy(() -> product.getPriceWithDiscount("PERCENTAGE", new BigDecimal("30")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum allowed");
    }

    @Test
    void getPriceWithDiscount_noCapSet_throws() {
        // Product has no maxDiscount set -> per-order discount not allowed
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .build();

        assertThatThrownBy(() -> product.getPriceWithDiscount("PERCENTAGE", new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not allow per-order discount");
    }

    @Test
    void getPriceWithDiscount_typeMismatch_throws() {
        // Product cap is PERCENTAGE but admin tries FIXED -> rejected
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThatThrownBy(() -> product.getPriceWithDiscount("FIXED", new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match product maxDiscountType");
    }

    @Test
    void getPriceWithDiscount_fixedDiscountWithinCap_appliesDiscount() {
        // Cap is 250 FIXED, admin applies 150 -> 1000 - 150 = 850
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("FIXED")
                .maxDiscountValue(new BigDecimal("250"))
                .build();

        assertThat(product.getPriceWithDiscount("FIXED", new BigDecimal("150")))
                .isEqualByComparingTo("850.00");
    }

    @Test
    void getPriceWithDiscount_fixedDiscountExceedsCap_throws() {
        // Cap is 100 FIXED, admin tries 200 -> rejected
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("FIXED")
                .maxDiscountValue(new BigDecimal("100"))
                .build();

        assertThatThrownBy(() -> product.getPriceWithDiscount("FIXED", new BigDecimal("200")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum allowed");
    }

    @Test
    void getPriceWithDiscount_zeroDiscount_returnsOriginalPrice() {
        // Admin explicitly applies 0% discount -> original price
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThat(product.getPriceWithDiscount("PERCENTAGE", BigDecimal.ZERO))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void getPriceWithDiscount_negativeDiscount_throws() {
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThatThrownBy(() -> product.getPriceWithDiscount("PERCENTAGE", new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void getPriceWithDiscount_invalidType_throws() {
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThatThrownBy(() -> product.getPriceWithDiscount("BOGUS", new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be PERCENTAGE or FIXED");
    }

    @Test
    void getPriceWithDiscount_fixedDiscountLargerThanPrice_floorsAtZero() {
        // Cap is 500 FIXED, admin applies 500 on a 400 product -> 0 (floored)
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("400.00"))
                .maxDiscountType("FIXED")
                .maxDiscountValue(new BigDecimal("500"))
                .build();

        assertThat(product.getPriceWithDiscount("FIXED", new BigDecimal("500")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void getPriceWithDiscount_productHasOwnPercentageDiscount_perOrderLayersOnTop() {
        // Product: price=1000, own 10% PERCENTAGE (effective 900)
        // Cap: 20% PERCENTAGE, admin applies 10%
        // Expected: 900 * 0.9 = 810
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("10"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThat(product.getPriceWithDiscount("PERCENTAGE", new BigDecimal("10")))
                .isEqualByComparingTo("810.00");
    }

    @Test
    void getPriceWithDiscount_productHasOwnFixedDiscount_perOrderLayersOnTop() {
        // Product: price=1000, own 100 FIXED (effective 900)
        // Cap: 200 FIXED, admin applies 150
        // Expected: 900 - 150 = 750
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .discountType("FIXED")
                .discountValue(new BigDecimal("100"))
                .maxDiscountType("FIXED")
                .maxDiscountValue(new BigDecimal("200"))
                .build();

        assertThat(product.getPriceWithDiscount("FIXED", new BigDecimal("150")))
                .isEqualByComparingTo("750.00");
    }

    @Test
    void getPriceWithDiscount_noPerOrderDiscount_returnsEffectivePrice() {
        // Product has its own 10% PERCENTAGE discount (effective 900)
        // No per-order discount requested -> return effective price
        Product product = Product.builder()
                .name("Test Product")
                .itemCode("TEST-001")
                .price(new BigDecimal("1000.00"))
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("10"))
                .maxDiscountType("PERCENTAGE")
                .maxDiscountValue(new BigDecimal("20"))
                .build();

        assertThat(product.getPriceWithDiscount(null, null))
                .isEqualByComparingTo("900.00");
    }
}
