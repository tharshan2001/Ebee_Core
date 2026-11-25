package com.ebee.finance.service;

import com.ebee.common.security.SecurityService;
import com.ebee.finance.entity.AccountTransaction;
import com.ebee.finance.entity.AccountTransaction.Direction;
import com.ebee.finance.entity.AccountTransaction.Type;
import com.ebee.finance.entity.PaymentAccount;
import com.ebee.finance.entity.PaymentAccountDefault;
import com.ebee.finance.repository.AccountTransactionRepository;
import com.ebee.finance.repository.PaymentAccountDefaultRepository;
import com.ebee.finance.repository.PaymentAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MoneyAccountService on an in-memory set of accounts and transactions. */
class MoneyAccountServiceTest {

    private final List<AccountTransaction> txns = new ArrayList<>();
    private final Map<UUID, PaymentAccount> accounts = new HashMap<>();
    private final Map<String, PaymentAccountDefault> defaults = new HashMap<>();
    private MoneyAccountService service;
    private PaymentAccount cash;
    private PaymentAccount bank;
    private PaymentAccount cheques;

    @BeforeEach
    void setUp() {
        PaymentAccountRepository accountRepo = mock(PaymentAccountRepository.class);
        when(accountRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(accounts.get((UUID) i.getArgument(0))));
        AccountTransactionRepository txnRepo = mock(AccountTransactionRepository.class);
        when(txnRepo.save(any())).thenAnswer(i -> {
            txns.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(txnRepo.balanceAsOf(any(), any())).thenAnswer(i -> balance(i.getArgument(0), i.getArgument(1)));
        when(txnRepo.statement(any(), any(), any())).thenAnswer(i -> txns.stream()
                .filter(t -> t.getAccount().getId().equals(i.getArgument(0)))
                .filter(t -> !t.getTxnDate().isBefore(i.getArgument(1)) && !t.getTxnDate().isAfter(i.getArgument(2)))
                .toList());
        PaymentAccountDefaultRepository defaultRepo = mock(PaymentAccountDefaultRepository.class);
        when(defaultRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(defaults.get((String) i.getArgument(0))));
        SecurityService security = mock(SecurityService.class);

        service = new MoneyAccountService(accountRepo, txnRepo, defaultRepo, security);

        cash = account("Cash in hand", PaymentAccount.Type.CASH);
        bank = account("Main bank", PaymentAccount.Type.BANK);
        cheques = account("Cheques in hand", PaymentAccount.Type.CHEQUES_IN_HAND);
        defaults.put("CASH", new PaymentAccountDefault("CASH", cash));
        defaults.put("REFUND", new PaymentAccountDefault("REFUND", cash));
        defaults.put("CHEQUE", new PaymentAccountDefault("CHEQUE", cheques));
        defaults.put("BANK_TRANSFER", new PaymentAccountDefault("BANK_TRANSFER", bank));
    }

    private PaymentAccount account(String name, PaymentAccount.Type type) {
        PaymentAccount a = new PaymentAccount(name, type, null, null);
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        accounts.put(a.getId(), a);
        return a;
    }

    private BigDecimal balance(UUID accountId, LocalDate asOf) {
        return txns.stream().filter(t -> t.getAccount().getId().equals(accountId) && !t.getTxnDate().isAfter(asOf))
                .map(AccountTransaction::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal bal(PaymentAccount a) {
        return balance(a.getId(), LocalDate.now());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void customerPayment_goesToDefaultAccountForItsMethod() {
        service.recordMovement(Direction.IN, bd("5000"), "CASH", null, Type.CUSTOMER_RECEIPT, "PAYMENT", UUID.randomUUID(), "x");
        service.recordMovement(Direction.IN, bd("2000"), "CHEQUE", null, Type.CUSTOMER_RECEIPT, "PAYMENT", UUID.randomUUID(), "x");

        assertThat(bal(cash)).isEqualByComparingTo("5000");
        assertThat(bal(cheques)).isEqualByComparingTo("2000");
    }

    @Test
    void explicitAccount_overridesTheDefault() {
        service.recordMovement(Direction.IN, bd("750"), "CASH", bank.getId(), Type.CUSTOMER_RECEIPT, null, null, "x");
        assertThat(bal(bank)).isEqualByComparingTo("750");
        assertThat(bal(cash)).isZero();
    }

    @Test
    void creditMethod_movesNoMoney() {
        assertThat(service.recordMovement(Direction.IN, bd("100"), "CREDIT", null, Type.CUSTOMER_RECEIPT, null, null, "x")).isNull();
        assertThat(txns).isEmpty();
    }

    @Test
    void refundOnCreditOrder_fallsBackToRefundAccount() {
        service.recordPayout(bd("300"), "CREDIT", null, Type.CUSTOMER_REFUND, "ORDER", UUID.randomUUID(), "Refund");
        assertThat(bal(cash)).isEqualByComparingTo("-300");
    }

    @Test
    void inactiveAccount_isRejected() {
        bank.setActive(false);
        assertThatThrownBy(() -> service.recordMovement(Direction.IN, bd("1"), null, bank.getId(), Type.ADJUSTMENT, null, null, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inactive");
    }

    @Test
    void transfer_movesMoneyBetweenAccounts_totalUnchanged() {
        service.recordMovement(Direction.IN, bd("10000"), "CASH", null, Type.CUSTOMER_RECEIPT, null, null, "x");

        UUID group = service.transfer(cash.getId(), bank.getId(), bd("8000"), null, "Banked takings");

        assertThat(bal(cash)).isEqualByComparingTo("2000");
        assertThat(bal(bank)).isEqualByComparingTo("8000");
        assertThat(txns.stream().filter(t -> group.equals(t.getTransferGroup()))).hasSize(2);
        assertThatThrownBy(() -> service.transfer(cash.getId(), cash.getId(), bd("1"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chequeDeposit_onlyFromChequesInHandIntoBank() {
        service.recordMovement(Direction.IN, bd("2000"), "CHEQUE", null, Type.CUSTOMER_RECEIPT, null, null, "x");

        service.depositCheques(cheques.getId(), bank.getId(), bd("2000"), null, "CHQ-001");

        assertThat(bal(cheques)).isZero();
        assertThat(bal(bank)).isEqualByComparingTo("2000");
        assertThatThrownBy(() -> service.depositCheques(cash.getId(), bank.getId(), bd("1"), null, null))
                .hasMessageContaining("not a cheques-in-hand account");
        assertThatThrownBy(() -> service.depositCheques(cheques.getId(), cash.getId(), bd("1"), null, null))
                .hasMessageContaining("bank account");
    }

    @Test
    void statement_hasOpeningRunningAndClosingBalances() {
        LocalDate today = LocalDate.now();
        txns.add(new AccountTransaction(cash, today.minusDays(5), Direction.IN, bd("1000"), Type.OPENING, null, null, null, "Opening", null));
        service.recordMovement(Direction.IN, bd("500"), "CASH", null, Type.CUSTOMER_RECEIPT, null, null, "Sale");
        service.recordMovement(Direction.OUT, bd("200"), "CASH", null, Type.EXPENSE, null, null, "Fuel");

        var st = service.statement(cash.getId(), today, today);

        assertThat(st.openingBalance()).isEqualByComparingTo("1000");
        assertThat(st.lines()).extracting(l -> l.balanceAfter().toPlainString()).containsExactly("1500", "1300");
        assertThat(st.closingBalance()).isEqualByComparingTo("1300");
    }
}
