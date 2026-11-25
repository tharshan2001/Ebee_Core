package com.ebee.accounting.enums;

/**
 * Customer ledger = accounts receivable only (balance = debit - credit = what the customer owes).
 * The advance balance is a separate wallet in customer_advance_transactions.
 *
 * <ul>
 *   <li>INVOICE          debit  — order created</li>
 *   <li>PAYMENT          credit — money received (or paid from the advance wallet)</li>
 *   <li>CREDIT_NOTE      credit — order value removed by cancellation / return / failed delivery</li>
 *   <li>REFUND           debit  — overpaid money given back in cash/bank</li>
 *   <li>ADVANCE_TRANSFER debit  — overpaid money moved into the advance wallet</li>
 *   <li>ADJUSTMENT       debit or credit — manual correction</li>
 *   <li>ADVANCE          legacy (pre ledger-rules fix) — wallet credits posted to A/R; no longer written</li>
 * </ul>
 */
public enum LedgerTransactionType {
    INVOICE,
    ADVANCE,
    ADVANCE_TRANSFER,
    PAYMENT,
    REFUND,
    ADJUSTMENT,
    CREDIT_NOTE,
    RETURN_REFUND,
    RETURN_EXCHANGE_CREDIT,
    DEBIT,
    CREDIT
}
