package com.ebee.customer.service;

import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only check that each customer's receivable ledger agrees with their orders.
 *
 * <p>Invariant: {@code ledgerBalance == Σ order.outstandingBalance + Σ unlinked entries}
 * (manual adjustments and account-level payments that aren't tied to an order).
 * Any difference is either an old bug (pre ledger-rules fix) or a new one.
 */
@Service
public class CustomerLedgerReconciliationService {

    private final CustomerLedgerRepository customerLedgerRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public CustomerLedgerReconciliationService(CustomerLedgerRepository customerLedgerRepository,
                                               OrderRepository orderRepository,
                                               CustomerRepository customerRepository) {
        this.customerLedgerRepository = customerLedgerRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
    }

    public record Row(UUID customerId, String customerName,
                      BigDecimal ledgerBalance, BigDecimal ordersOutstanding, BigDecimal unlinkedEntries,
                      BigDecimal expected, BigDecimal difference,
                      BigDecimal storedOutstanding, long legacyAdvanceEntries) {
        public boolean matches() {
            return difference.signum() == 0 && legacyAdvanceEntries == 0
                    && storedOutstanding.compareTo(ledgerBalance) == 0;
        }
    }

    @Transactional(readOnly = true)
    public List<Row> reconcile(boolean mismatchesOnly) {
        Map<UUID, BigDecimal> ledger = toMap(customerLedgerRepository.sumBalanceByCustomer());
        Map<UUID, BigDecimal> orders = toMap(orderRepository.sumOutstandingByCustomer());
        Map<UUID, BigDecimal> unlinked = toMap(customerLedgerRepository.sumUnlinkedByCustomer());
        Map<UUID, Long> legacy = new HashMap<>();
        for (Object[] r : customerLedgerRepository.countLegacyAdvanceByCustomer()) {
            legacy.put((UUID) r[0], ((Number) r[1]).longValue());
        }

        Set<UUID> ids = new HashSet<>(ledger.keySet());
        ids.addAll(orders.keySet());
        Map<UUID, Customer> customers = new HashMap<>();
        customerRepository.findAllById(ids).forEach(c -> customers.put(c.getId(), c));

        return ids.stream()
                .map(id -> {
                    BigDecimal bal = ledger.getOrDefault(id, BigDecimal.ZERO);
                    BigDecimal ord = orders.getOrDefault(id, BigDecimal.ZERO);
                    BigDecimal unl = unlinked.getOrDefault(id, BigDecimal.ZERO);
                    BigDecimal expected = ord.add(unl);
                    Customer c = customers.get(id);
                    String name = c == null ? "(deleted)" : (c.getFirstName() + " " + c.getLastName()).trim();
                    BigDecimal stored = c == null || c.getOutstandingBalance() == null
                            ? BigDecimal.ZERO : c.getOutstandingBalance();
                    return new Row(id, name, bal, ord, unl, expected, bal.subtract(expected),
                            stored, legacy.getOrDefault(id, 0L));
                })
                .filter(r -> !mismatchesOnly || !r.matches())
                .sorted(Comparator.comparing((Row r) -> r.difference().abs()).reversed())
                .toList();
    }

    private static Map<UUID, BigDecimal> toMap(List<Object[]> rows) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((UUID) r[0], (BigDecimal) r[1]);
        }
        return m;
    }
}
