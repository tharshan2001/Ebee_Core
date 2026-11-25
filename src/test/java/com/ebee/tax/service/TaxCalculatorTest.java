package com.ebee.tax.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void inclusivePrice_extractsVatFromPrice() {
        var t = TaxCalculator.forUnit(bd("1180.00"), bd("18"), true);
        assertThat(t.grossUnitPrice()).isEqualByComparingTo("1180.00");
        assertThat(t.unitTax()).isEqualByComparingTo("180.00");
        assertThat(t.netUnitPrice()).isEqualByComparingTo("1000.00");
    }

    @Test
    void exclusivePrice_addsVatOnTop() {
        var t = TaxCalculator.forUnit(bd("1000.00"), bd("18"), false);
        assertThat(t.grossUnitPrice()).isEqualByComparingTo("1180.00");
        assertThat(t.unitTax()).isEqualByComparingTo("180.00");
    }

    @Test
    void inclusive_roundsPerUnitToCents() {
        // 1000 × 18 / 118 = 152.5423…
        assertThat(TaxCalculator.forUnit(bd("1000.00"), bd("18"), true).unitTax()).isEqualByComparingTo("152.54");
    }

    @Test
    void noRateOrZeroRate_meansNoTax() {
        assertThat(TaxCalculator.forUnit(bd("500.00"), null, true).unitTax()).isZero();
        assertThat(TaxCalculator.forUnit(bd("500.00"), BigDecimal.ZERO, false).grossUnitPrice()).isEqualByComparingTo("500.00");
    }

    @Test
    void bundle_taxesEachComponentShareAtItsOwnRate() {
        // Combo sold at 2,000 incl. VAT; components weigh equally: one at 18%, one exempt.
        var shares = List.of(new TaxCalculator.Share(bd("1000"), bd("18")),
                new TaxCalculator.Share(bd("1000"), null));
        var inclusive = TaxCalculator.forBundle(bd("2000.00"), shares, true);
        assertThat(inclusive.unitTax()).isEqualByComparingTo("152.54"); // 1000 × 18/118
        assertThat(inclusive.grossUnitPrice()).isEqualByComparingTo("2000.00");

        var exclusive = TaxCalculator.forBundle(bd("2000.00"), shares, false);
        assertThat(exclusive.unitTax()).isEqualByComparingTo("180.00");
        assertThat(exclusive.grossUnitPrice()).isEqualByComparingTo("2180.00");
        assertThat(TaxCalculator.commonRate(shares)).isNull(); // mixed
    }

    @Test
    void bundle_withOneRate_reportsThatRate() {
        var shares = List.of(new TaxCalculator.Share(bd("300"), bd("18")), new TaxCalculator.Share(bd("700"), bd("18")));
        assertThat(TaxCalculator.commonRate(shares)).isEqualByComparingTo("18");
        assertThat(TaxCalculator.forBundle(bd("1180.00"), shares, true).unitTax()).isEqualByComparingTo("180.00");
    }

    @Test
    void taxOnNet_forPurchases() {
        assertThat(TaxCalculator.taxOnNet(bd("250.00"), bd("18"))).isEqualByComparingTo("45.00");
        assertThat(TaxCalculator.taxOnNet(bd("250.00"), null)).isZero();
    }
}
