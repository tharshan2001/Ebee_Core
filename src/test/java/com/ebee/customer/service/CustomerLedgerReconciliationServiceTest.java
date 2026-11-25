package com.ebee.customer.service;

import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerLedgerReconciliationServiceTest {

    private final CustomerLedgerRepository ledgerRepo = mock(CustomerLedgerRepository.class);
    private final OrderRepository orderRepo = mock(OrderRepository.class);
    private final CustomerRepository customerRepo = mock(CustomerRepository.class);
    private final CustomerLedgerReconciliationService service =
            new CustomerLedgerReconciliationService(ledgerRepo, orderRepo, customerRepo);

    private static Object[] row(UUID id, Object v) {
        return new Object[]{id, v};
    }

    @Test
    void flagsCustomerWhoseLedgerDisagreesWithOrders_andPassesBalancedOne() {
        UUID ok = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        // ok: owes 3,000 on orders + 500 manual adjustment = 3,500 in ledger.
        // bad: paid upfront 2,000 but the payment never reached the ledger (old bug).
        when(ledgerRepo.sumBalanceByCustomer()).thenReturn(List.of(
                row(ok, new BigDecimal("3500")), row(bad, new BigDecimal("2000"))));
        when(orderRepo.sumOutstandingByCustomer()).thenReturn(List.of(
                row(ok, new BigDecimal("3000")), row(bad, BigDecimal.ZERO)));
        when(ledgerRepo.sumUnlinkedByCustomer()).thenReturn(List.<Object[]>of(row(ok, new BigDecimal("500"))));
        when(ledgerRepo.countLegacyAdvanceByCustomer()).thenReturn(List.of());
        when(customerRepo.findAllById(any())).thenReturn(List.of(
                Customer.builder().id(ok).firstName("Ok").lastName("C").outstandingBalance(new BigDecimal("3500")).build(),
                Customer.builder().id(bad).firstName("Bad").lastName("C").outstandingBalance(new BigDecimal("2000")).build()));

        List<CustomerLedgerReconciliationService.Row> mismatches = service.reconcile(true);

        assertThat(mismatches).hasSize(1);
        assertThat(mismatches.get(0).customerId()).isEqualTo(bad);
        assertThat(mismatches.get(0).difference()).isEqualByComparingTo("2000");
        assertThat(service.reconcile(false)).hasSize(2);
    }

    @Test
    void legacyAdvanceEntries_areFlaggedEvenWhenBalanced() {
        UUID id = UUID.randomUUID();
        when(ledgerRepo.sumBalanceByCustomer()).thenReturn(List.<Object[]>of(row(id, BigDecimal.ZERO)));
        when(orderRepo.sumOutstandingByCustomer()).thenReturn(List.<Object[]>of(row(id, BigDecimal.ZERO)));
        when(ledgerRepo.sumUnlinkedByCustomer()).thenReturn(List.of());
        when(ledgerRepo.countLegacyAdvanceByCustomer()).thenReturn(List.<Object[]>of(row(id, 2L)));
        when(customerRepo.findAllById(any())).thenReturn(List.of(
                Customer.builder().id(id).firstName("L").lastName("C").outstandingBalance(BigDecimal.ZERO).build()));

        assertThat(service.reconcile(true)).singleElement()
                .satisfies(r -> assertThat(r.legacyAdvanceEntries()).isEqualTo(2));
    }
}
