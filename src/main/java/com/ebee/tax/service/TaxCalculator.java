package com.ebee.tax.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure VAT arithmetic. Tax is computed per unit and rounded to cents, so a line's tax is always
 * {@code unitTax × quantity} and a partial return reverses exactly {@code unitTax × returned}.
 */
public final class TaxCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private TaxCalculator() {
    }

    /** Gross price charged per unit and the VAT contained in it. Net = gross − tax. */
    public record UnitTax(BigDecimal grossUnitPrice, BigDecimal unitTax) {
        public BigDecimal netUnitPrice() {
            return grossUnitPrice.subtract(unitTax);
        }
    }

    /** A bundle component: its weight in the bundle (list price × qty) and its VAT rate (null = none). */
    public record Share(BigDecimal weight, BigDecimal ratePercent) {}

    /**
     * @param price            the (already discounted) price per unit, as entered in the catalog
     * @param ratePercent      VAT rate, e.g. 18; null or 0 = no VAT
     * @param pricesIncludeTax true if {@code price} already includes VAT
     */
    public static UnitTax forUnit(BigDecimal price, BigDecimal ratePercent, boolean pricesIncludeTax) {
        if (ratePercent == null || ratePercent.signum() == 0 || price.signum() == 0) {
            return new UnitTax(price, BigDecimal.ZERO.setScale(2));
        }
        BigDecimal tax = taxOn(price, ratePercent, pricesIncludeTax).setScale(2, RoundingMode.HALF_UP);
        return new UnitTax(pricesIncludeTax ? price : price.add(tax), tax);
    }

    /**
     * A bundle (combo) sold at {@code price}: the price is split across components by weight and
     * each part is taxed at its own rate. Rounded once at the end.
     */
    public static UnitTax forBundle(BigDecimal price, List<Share> shares, boolean pricesIncludeTax) {
        BigDecimal totalWeight = shares.stream().map(Share::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (price.signum() == 0 || totalWeight.signum() == 0) {
            return new UnitTax(price, BigDecimal.ZERO.setScale(2));
        }
        BigDecimal tax = BigDecimal.ZERO;
        for (Share s : shares) {
            if (s.ratePercent() == null || s.ratePercent().signum() == 0) {
                continue;
            }
            BigDecimal portion = price.multiply(s.weight()).divide(totalWeight, 10, RoundingMode.HALF_UP);
            tax = tax.add(taxOn(portion, s.ratePercent(), pricesIncludeTax));
        }
        tax = tax.setScale(2, RoundingMode.HALF_UP);
        return new UnitTax(pricesIncludeTax ? price : price.add(tax), tax);
    }

    /** The single rate shared by every share, or null when they differ (a "mixed" bundle). */
    public static BigDecimal commonRate(List<Share> shares) {
        BigDecimal first = null;
        for (Share s : shares) {
            BigDecimal r = s.ratePercent() == null ? BigDecimal.ZERO : s.ratePercent();
            if (first == null) {
                first = r;
            } else if (first.compareTo(r) != 0) {
                return null;
            }
        }
        return first == null || first.signum() == 0 ? null : first;
    }

    /** VAT added on top of a net amount (purchases are always quoted net). */
    public static BigDecimal taxOnNet(BigDecimal netAmount, BigDecimal ratePercent) {
        if (ratePercent == null || ratePercent.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return taxOn(netAmount, ratePercent, false).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal taxOn(BigDecimal amount, BigDecimal rate, boolean inclusive) {
        return inclusive
                ? amount.multiply(rate).divide(HUNDRED.add(rate), 10, RoundingMode.HALF_UP)
                : amount.multiply(rate).divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }
}
