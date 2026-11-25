package com.ebee.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Default account for a payment method (CASH, CHEQUE…) or flow (EXPENSE, COMMISSION_PAYOUT, REFUND). */
@Entity
@Table(name = "payment_account_defaults")
public class PaymentAccountDefault {

    @Id
    @Column(length = 30)
    private String purpose;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private PaymentAccount account;

    protected PaymentAccountDefault() {
    }

    public PaymentAccountDefault(String purpose, PaymentAccount account) {
        this.purpose = purpose;
        this.account = account;
    }

    public String getPurpose() { return purpose; }
    public PaymentAccount getAccount() { return account; }
    public void setAccount(PaymentAccount account) { this.account = account; }
}
