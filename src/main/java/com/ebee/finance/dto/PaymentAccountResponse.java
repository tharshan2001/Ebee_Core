package com.ebee.finance.dto;

import com.ebee.finance.entity.PaymentAccount;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAccountResponse(UUID id, String name, PaymentAccount.Type type, String bankName,
                                     String accountNumber, boolean active, BigDecimal balance) {
}
