package com.ebee.tax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * VAT return for a period. Credit note and purchase return figures are negative.
 * vatPayable = outputTax − inputTax (negative = refundable).
 */
public record VatReport(
        LocalDate from, LocalDate to,
        BigDecimal salesTaxable, BigDecimal salesTax,
        BigDecimal creditNotesTaxable, BigDecimal creditNotesTax,
        BigDecimal outputTax,
        BigDecimal purchasesTaxable, BigDecimal purchasesTax,
        BigDecimal purchaseReturnsTaxable, BigDecimal purchaseReturnsTax,
        BigDecimal inputTax,
        BigDecimal vatPayable) {
}
