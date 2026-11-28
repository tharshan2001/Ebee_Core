package com.ebee.common;

import com.ebee.customer.service.CustomerService;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.enums.ExpenseStatus;
import com.ebee.expense.service.ExpenseService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.report.service.ReportsService;
import com.ebee.supplier.dto.SupplierRequest;
import com.ebee.supplier.service.SupplierService;
import com.ebee.support.RealDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The paged list queries behind the money, stock and expense screens, against the real schema. */
@Transactional
class ServerPagingIntegrationTest extends RealDatabaseTest {

    @Autowired private CustomerService customers;
    @Autowired private SupplierService suppliers;
    @Autowired private com.ebee.supplier.repository.SupplierRepository supplierRepository;
    @Autowired private InventoryService inventory;
    @Autowired private ExpenseService expenses;
    @Autowired private ReportsService reports;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private com.ebee.report.service.LedgerBrowseService ledgers;
    @Autowired private com.ebee.accounting.service.LedgerService ledger;

    @Test
    void customerBalances_areFilteredSortedAndPaged_withTotalsOverAll() {
        for (int i = 1; i <= 3; i++) {
            var c = customer();
            c.setFirstName("Pg" + suffix);
            c.setOutstandingBalance(new BigDecimal(i * 100));
            c.setAdvanceBalance(i == 2 ? new BigDecimal("5") : BigDecimal.ZERO);
            customerRepository.save(c);
        }

        var first = customers.getBalances("dues", false, "pg" + suffix, 0, 2);
        assertThat(first.page().getTotalElements()).isEqualTo(3);
        assertThat(first.page().getContent()).extracting(c -> c.getOutstandingBalance().intValue()).containsExactly(300, 200);
        assertThat(first.count()).isGreaterThanOrEqualTo(3);
        assertThat(first.totalOutstanding()).isGreaterThanOrEqualTo(new BigDecimal("600"));

        assertThat(customers.getBalances("dues", true, "pg" + suffix, 0, 10).page().getContent()).hasSize(1);
        assertThat(customers.getBalances("advance", false, "pg" + suffix, 0, 10).page().getContent()).hasSize(1);
        assertThat(customers.getBalances("dues", false, "nobody-" + suffix, 0, 10).page().getContent()).isEmpty();
    }

    @Test
    void supplierDues_stock_expenses_andAging_page() {
        var created = suppliers.createSupplier(new SupplierRequest("Paged " + suffix, null, null, null, "1 Road", null,
                "Jaffna", null, null, null));
        var supplier = supplierRepository.findById(created.id()).orElseThrow();
        supplier.setOutstandingBalance(new BigDecimal("42"));
        supplierRepository.save(supplier);
        var dues = suppliers.getDuesPage("paged " + suffix, 0, 5);
        assertThat(dues.page().getContent()).hasSize(1);
        assertThat(dues.largestName()).isNotNull();

        var product = product("10.00");
        inventory.recordPurchaseStocks(null, java.util.List.of(product.getId()), java.util.List.of(4),
                java.util.List.of(new BigDecimal("6.00")), java.util.List.of("in"));
        var stock = inventory.searchStock(product.getItemCode(), null, PageRequest.of(0, 5));
        assertThat(stock.getTotalElements()).isEqualTo(1);
        assertThat(inventory.searchStock(null, java.util.UUID.randomUUID(), PageRequest.of(0, 5)).getContent()).isEmpty();

        assertThat(expenses.searchExpenses(null, null, null, false, false, 0, 5).getSize()).isEqualTo(5);
        assertThat(expenses.searchExpenses(ExpenseStatus.PENDING, ExpenseCategory.PETROL, "zz" + suffix, true, true, 0, 5)
                .getContent()).isEmpty();
        assertThat(expenses.getSummary().pendingAmount()).isNotNull();

        var aging = reports.getAgingReport(0, 1);
        assertThat(aging.getCustomerBuckets().size()).isLessThanOrEqualTo(1);
        assertThat(aging.getTotalBuckets()).isNotNull();
    }

    @Test
    void ledgers_pageNewestFirst_withEachPartysRunningBalance() {
        var c = customer();
        ledger.recordCustomerAdjustment(c.getId(), new BigDecimal("100"), java.time.LocalDate.now(), "first " + suffix,
                "test", admin.getId());
        ledger.recordCustomerAdjustment(c.getId(), new BigDecimal("50"), java.time.LocalDate.now(), "second " + suffix,
                "test", admin.getId());

        entityManager.flush(); // the ledger screen reads with plain SQL

        var page = ledgers.page("customer", suffix, 0, 1);
        assertThat(page.page().getTotalElements()).isEqualTo(2);
        assertThat(page.page().getTotalPages()).isEqualTo(2);
        var all = ledgers.page("customer", suffix, 0, 10).page().getContent();
        assertThat(all).extracting(r -> r.balance().intValue()).containsExactlyInAnyOrder(100, 150);
        // A search for one entry still shows the balance over the party's whole history.
        assertThat(ledgers.page("customer", "second " + suffix, 0, 10).page().getContent())
                .singleElement().satisfies(r -> assertThat(r.balance()).isEqualByComparingTo("150"));
        assertThat(ledgers.page("supplier", null, 0, 5).customerBalance()).isNotNull();
        assertThat(ledgers.page("agent", null, 0, 5).page().getPageSize()).isEqualTo(5);
    }
}
