package com.ebee.finance.service;

import com.ebee.accounting.repository.AgentLedgerRepository;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.customer.repository.CustomerAdvanceTransactionRepository;
import com.ebee.finance.dto.BalanceSheet;
import com.ebee.finance.dto.CashFlow;
import com.ebee.finance.entity.AccountTransaction.Direction;
import com.ebee.finance.entity.AccountTransaction.Type;
import com.ebee.finance.repository.AccountTransactionRepository;
import com.ebee.finance.repository.PaymentAccountRepository;
import com.ebee.inventory.repository.InventoryLayerRepository;
import com.ebee.tax.entity.VatEntry;
import com.ebee.tax.repository.VatEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Balance sheet and cash-flow statement built from the accounts and the ledgers. */
@Service
public class FinancialStatementService {

    private final PaymentAccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final MoneyAccountService moneyAccountService;
    private final CustomerLedgerRepository customerLedgerRepository;
    private final SupplierLedgerRepository supplierLedgerRepository;
    private final AgentLedgerRepository agentLedgerRepository;
    private final CustomerAdvanceTransactionRepository advanceRepository;
    private final VatEntryRepository vatEntryRepository;
    private final InventoryLayerRepository inventoryLayerRepository;

    public FinancialStatementService(PaymentAccountRepository accountRepository,
                                     AccountTransactionRepository transactionRepository,
                                     MoneyAccountService moneyAccountService,
                                     CustomerLedgerRepository customerLedgerRepository,
                                     SupplierLedgerRepository supplierLedgerRepository,
                                     AgentLedgerRepository agentLedgerRepository,
                                     CustomerAdvanceTransactionRepository advanceRepository,
                                     VatEntryRepository vatEntryRepository,
                                     InventoryLayerRepository inventoryLayerRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.moneyAccountService = moneyAccountService;
        this.customerLedgerRepository = customerLedgerRepository;
        this.supplierLedgerRepository = supplierLedgerRepository;
        this.agentLedgerRepository = agentLedgerRepository;
        this.advanceRepository = advanceRepository;
        this.vatEntryRepository = vatEntryRepository;
        this.inventoryLayerRepository = inventoryLayerRepository;
    }

    @Transactional(readOnly = true)
    public BalanceSheet balanceSheet(LocalDate asOf) {
        LocalDateTime before = asOf.plusDays(1).atStartOfDay();

        Map<UUID, BigDecimal> balances = moneyAccountService.balancesAsOf(asOf);
        List<BalanceSheet.Line> cash = new ArrayList<>();
        BigDecimal totalCash = BigDecimal.ZERO;
        for (var a : accountRepository.findAllByOrderByNameAsc()) {
            BigDecimal b = balances.getOrDefault(a.getId(), BigDecimal.ZERO);
            if (a.isActive() || b.signum() != 0) {
                cash.add(new BalanceSheet.Line(a.getName(), b));
                totalCash = totalCash.add(b);
            }
        }

        // Each party's balance is an asset or a liability depending on its sign.
        BigDecimal[] customers = split(customerLedgerRepository.balancesBefore(before));   // + owes us
        BigDecimal[] suppliers = split(supplierLedgerRepository.balancesBefore(before));   // + we owe
        BigDecimal[] agents = split(agentLedgerRepository.balancesBefore(before));         // + we owe
        BigDecimal advances = advanceRepository.sumAllBefore(before);
        BigDecimal vatNet = vatEntryRepository.sumTaxAsOf(VatEntry.Direction.OUTPUT, asOf)
                .subtract(vatEntryRepository.sumTaxAsOf(VatEntry.Direction.INPUT, asOf));
        BigDecimal stock = inventoryLayerRepository.currentStockValue().setScale(2, RoundingMode.HALF_UP);

        BigDecimal receivables = customers[0];
        BigDecimal customerCredits = customers[1];
        BigDecimal supplierPayables = suppliers[0];
        BigDecimal supplierAdvances = suppliers[1];
        BigDecimal commissionPayable = agents[0];
        BigDecimal clawbacks = agents[1];
        BigDecimal vatPayable = vatNet.max(BigDecimal.ZERO);
        BigDecimal vatRefundable = vatNet.min(BigDecimal.ZERO).negate();

        BigDecimal assets = totalCash.add(receivables).add(stock).add(clawbacks).add(supplierAdvances).add(vatRefundable);
        BigDecimal liabilities = supplierPayables.add(commissionPayable).add(advances).add(customerCredits).add(vatPayable);

        return new BalanceSheet(asOf, cash, totalCash, receivables, stock, clawbacks, supplierAdvances, vatRefundable,
                assets, supplierPayables, commissionPayable, advances, customerCredits, vatPayable, liabilities,
                assets.subtract(liabilities), true);
    }

    @Transactional(readOnly = true)
    public CashFlow cashFlow(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        Map<Type, BigDecimal> net = new EnumMap<>(Type.class);
        for (Object[] r : transactionRepository.flowsByType(from, to)) {
            BigDecimal amt = (BigDecimal) r[2];
            net.merge((Type) r[0], r[1] == Direction.IN ? amt : amt.negate(), BigDecimal::add);
        }

        List<CashFlow.Line> operating = lines(net, Type.CUSTOMER_RECEIPT, "Received from customers",
                Type.CUSTOMER_REFUND, "Refunded to customers", Type.SUPPLIER_PAYMENT, "Paid to suppliers",
                Type.EXPENSE, "Expenses", Type.COMMISSION_PAYOUT, "Commission paid to agents");
        List<CashFlow.Line> financing = lines(net, Type.OWNER_DEPOSIT, "Owner deposits",
                Type.OWNER_WITHDRAWAL, "Owner withdrawals", Type.OPENING, "Opening balances");
        List<CashFlow.Line> other = lines(net, Type.ADJUSTMENT, "Adjustments");

        BigDecimal opening = sum(moneyAccountService.balancesAsOf(from.minusDays(1)));
        BigDecimal netOp = total(operating);
        BigDecimal netFin = total(financing);
        BigDecimal netOther = total(other);
        BigDecimal change = netOp.add(netFin).add(netOther);
        return new CashFlow(from, to, opening, operating, netOp, financing, netFin, other, netOther, change,
                sum(moneyAccountService.balancesAsOf(to)));
    }

    /** [Σ positives, Σ |negatives|] of per-party balances. */
    private static BigDecimal[] split(List<Object[]> rows) {
        BigDecimal pos = BigDecimal.ZERO;
        BigDecimal neg = BigDecimal.ZERO;
        for (Object[] r : rows) {
            BigDecimal b = (BigDecimal) r[1];
            if (b.signum() > 0) {
                pos = pos.add(b);
            } else {
                neg = neg.add(b.negate());
            }
        }
        return new BigDecimal[]{pos, neg};
    }

    private static List<CashFlow.Line> lines(Map<Type, BigDecimal> net, Object... typeLabelPairs) {
        List<CashFlow.Line> out = new ArrayList<>();
        for (int i = 0; i < typeLabelPairs.length; i += 2) {
            BigDecimal v = net.getOrDefault((Type) typeLabelPairs[i], BigDecimal.ZERO);
            out.add(new CashFlow.Line((String) typeLabelPairs[i + 1], v));
        }
        return out;
    }

    private static BigDecimal total(List<CashFlow.Line> lines) {
        return lines.stream().map(CashFlow.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(Map<UUID, BigDecimal> m) {
        return m.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
