package com.ebee.product.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDiscountTest {

    @Test
    void getEffectivePrice_noDiscount_returnsOriginalPrice() {
        Product product = Product.builder()
                .price(new BigDecimal("100.00"))
                .build();

        assertThat(product.getEffectivePrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void getEffectivePrice_percentageDiscount_appliesCorrectly() {
        Product product = Product.builder()
                .price(new BigDecimal("100.00"))
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("10"))
                .build();

        // 100 * (1 - 10/100) = 100 * 0.9 = 90.00
        assertThat(product.getEffectivePrice()).isEqualByComparingTo("90.00");
    }

    @Test
    void getEffectivePrice_percentage50Discount_appliesCorrectly() {
        Product product = Product.builder()
                .price(new BigDecimal("200.00"))
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("50"))
                .build();

        // 200 * (1 - 50/100) = 200 * 0.5 = 100.00
        assertThat(product.getEffectivePrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void getEffectivePrice_percentageAt100_discountMakesItFree() {
        Product product = Product.builder()
                .price(new BigDecimal("50.00"))
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("100"))
                .build();

        // 50 * (1 - 100/100) = 0.00
        assertThat(product.getEffectivePrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void getEffectivePrice_percentageOver100_floorsAtZero() {
        // Defense in depth: if bad data exists (e.g. 150% discount),
        // the price is floored at zero, not negative
        Product product = Product.builder()
                .price(new BigDecimal("100.00"))
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("150"))
                .build();

        // 100 * (1 - 150/100) = 100 * -0.5 = -50 -> max(0) = 0.00
        assertThat(product.getEffectivePrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void getEffectivePrice_fixedDiscount_subtractsFromPrice() {
        Product product = Product.builder()
                .price(new BigDecimal("100.00"))
                .discountType("FIXED")
                .discountValue(new BigDecimal("15"))
                .build();

        // 100 - 15 = 85.00
        assertThat(product.getEffectivePrice()).isEqualByComparingTo("85.00");
    }

    @Test
    void getEffectivePrice_fixedDiscountLargerThanPrice_floorsAtZero() {
        Product product = Product.builder()
                .price(new BigDecimal("50.00"))
                .discountType("FIXED")
                .discountValue(new BigDecimal("75"))
                .build();

        // 50 - 75 = -25 -> max(0) = 0.00
        assertThat(product.getEffectivePrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void getEffectivePrice_unknownDiscountType_returnsOriginalPrice() {
        Product product = Product.builder()
                .price(new BigDecimal("100.00"))
                .discountType("UNKNOWN")
                .discountValue(new BigDecimal("50"))
                .build();

        assertThat(product.getEffectivePrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void getEffectivePrice_decimalPercentage_appliesCorrectly() {
        Product product = Product.builder()
                .price(new BigDecimal("99.99"))
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("7.5"))
                .build();

        // 99.99 * (1 - 7.5/100) = 99.99 * 0.925 = 92.49 (rounded HALF_UP to 2dp)
        assertThat(product.getEffectivePrice()).isEqualByComparingTo("92.49");
    }

    @Test
    void getEffectivePrice_discountValueNull_returnsOriginalPrice() {
        Product product = Product.builder()
                .price(new BigDecimal("100.00"))
                .discountType("PERCENTAGE")
                .discountValue(null)
                .build();

        assertThat(product.getEffectivePrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void getEffectivePrice_discountTypeNull_returnsOriginalPrice() {
        Product product = Product.builder()
                .price(new BigDecimal("100.00"))
                .discountType(null)
                .discountValue(new BigDecimal("10"))
                .build();

        assertThat(product.getEffectivePrice()).isEqualByComparingTo("100.00");
    }

    // ── packs (units of measure) ───────────────────────────────────────

    @Test
    void fixedCatalogDiscount_scalesWithPackSize() {
        com.ebee.product.entity.Product p = com.ebee.product.entity.Product.builder().name("Oil")
                .price(new java.math.BigDecimal("100.00")).discountType("FIXED").discountValue(new java.math.BigDecimal("5.00")).build();
        // Box of 12 listed at 1,200: the 5/unit catalog discount is 60 off the box.
        org.assertj.core.api.Assertions.assertThat(p.getEffectivePrice(new java.math.BigDecimal("1200.00"), 12))
                .isEqualByComparingTo("1140.00");
    }

    @Test
    void fixedPerOrderDiscountLimit_scalesWithPackSize() {
        com.ebee.product.entity.Product p = com.ebee.product.entity.Product.builder().name("Oil")
                .price(new java.math.BigDecimal("100.00")).maxDiscountType("FIXED").maxDiscountValue(new java.math.BigDecimal("10.00")).build();
        // Up to 10/unit → up to 120 off a box of 12.
        org.assertj.core.api.Assertions.assertThat(p.getPriceWithDiscount(new java.math.BigDecimal("1200.00"), 12, "FIXED",
                new java.math.BigDecimal("120.00"))).isEqualByComparingTo("1080.00");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> p.getPriceWithDiscount(new java.math.BigDecimal("1200.00"), 12,
                "FIXED", new java.math.BigDecimal("121.00"))).hasMessageContaining("exceeds the maximum allowed (120");
    }
}
