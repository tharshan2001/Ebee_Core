package com.ebee.order.service;

import com.ebee.accounting.entity.CustomerLedger;
import com.ebee.accounting.repository.AgentLedgerRepository;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.accounting.service.AccountingPeriodService;
import com.ebee.accounting.service.LedgerService;
import com.ebee.combo.entity.Combo;
import com.ebee.combo.entity.ComboItem;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.service.CustomerService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.enums.RefundChoice;
import com.ebee.product.entity.Product;
import com.ebee.stock.enums.StockMovementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end money scenarios on the real LedgerService + OrderSettlementService with an
 * in-memory ledger. After every scenario the receivable ledger balance must equal what the
 * order says is still owed, and the advance wallet must hold exactly what was moved there.
 */
class OrderSettlementLedgerScenarioTest {

    private final List<CustomerLedger> ledger = new ArrayList<>();
    private InventoryService inventoryService;
    private OrderSettlementService settlement;
    private LedgerService ledgerService;
    private com.ebee.tax.service.VatService vatService;
    private com.ebee.finance.service.MoneyAccountService moneyAccountService;
    private Customer customer;

    @BeforeEach
    void setUp() {
        CustomerLedgerRepository ledgerRepo = mock(CustomerLedgerRepository.class);
        when(ledgerRepo.save(any())).thenAnswer(inv -> {
            CustomerLedger e = inv.getArgument(0);
            ledger.add(e);
            return e;
        });
        ledgerService = new LedgerService(ledgerRepo, mock(SupplierLedgerRepository.class),
                mock(AgentLedgerRepository.class), mock(AccountingPeriodService.class));

        customer = Customer.builder().id(UUID.randomUUID()).firstName("Test").lastName("Customer").build();

        CustomerService customerService = mock(CustomerService.class);
        when(customerService.creditToAdvance(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setAdvanceBalance(c.getAdvanceBalance().add(inv.getArgument(1)));
            return null;
        });
        org.mockito.Mockito.doAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setOutstandingBalance(ledgerBalance());
            return null;
        }).when(customerService).syncAllBalances(any());

        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        inventoryService = mock(InventoryService.class);

        vatService = mock(com.ebee.tax.service.VatService.class);
        moneyAccountService = mock(com.ebee.finance.service.MoneyAccountService.class);
        settlement = new OrderSettlementService(inventoryService, ledgerService, customerService, securityService, vatService, moneyAccountService);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private BigDecimal ledgerBalance() {
        return ledger.stream().map(e -> e.getDebit().subtract(e.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** Order of {@code lines} × LKR 1,000 regular items, created the way OrderServiceImpl does it. */
    private Order createOrder(int units, String upfront) {
        Product product = Product.builder().id(UUID.randomUUID()).name("Widget").build();
        OrderItem item = OrderItem.builder().id(UUID.randomUUID()).product(product)
                .quantity(units).unitPrice(bd("1000")).subtotal(bd("1000").multiply(BigDecimal.valueOf(units))).build();
        BigDecimal total = item.getSubtotal();
        Order order = Order.builder().id(UUID.randomUUID()).orderNumber("JF-A-TEST").customer(customer)
                .totalAmount(total).items(new ArrayList<>(List.of(item))).build();
        ledgerService.recordCustomerInvoice(customer.getId(), order.getId(), total, "Order", null);
        pay(order, upfront);
        return order;
    }

    /** A payment recorded the way OrderPaymentService/createOrder do it. */
    private void pay(Order order, String amount) {
        BigDecimal a = bd(amount);
        if (a.signum() == 0) {
            order.setOutstandingBalance(order.getTotalAmount().subtract(order.getPaidAmount()));
            return;
        }
        ledgerService.recordCustomerPayment(customer.getId(), UUID.randomUUID(), a, "Payment", null);
        order.setPaidAmount(order.getPaidAmount().add(a));
        order.setOutstandingBalance(order.getTotalAmount().subtract(order.getPaidAmount()));
    }

    private void assertLedgerMatchesOrder(Order order) {
        assertThat(ledgerBalance())
                .as("ledger balance must equal what the order says is still owed")
                .isEqualByComparingTo(order.getOutstandingBalance());
    }

    private OrderSettlementService.ReturnLine line(Order order, int qty) {
        return new OrderSettlementService.ReturnLine(order.getItems().get(0), qty);
    }

    // ── cancellation ───────────────────────────────────────────────────

    @Test
    void cancelUnpaidOrder_customerOwesNothing() {
        Order order = createOrder(10, "0");
        assertThat(ledgerBalance()).isEqualByComparingTo("10000");

        settlement.cancel(order, RefundChoice.REFUND, true, "Order cancelled");

        assertThat(ledgerBalance()).isZero();
        assertLedgerMatchesOrder(order);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void cancelOrderPaidUpfront_refund_customerOwesNothing() {
        Order order = createOrder(10, "10000");
        assertThat(ledgerBalance()).as("upfront payment reaches the ledger").isZero();

        var result = settlement.cancel(order, RefundChoice.REFUND, true, "Order cancelled");

        assertThat(result.refunded()).isEqualByComparingTo("10000");
        // Cash leaves the account the order was paid through (the payout falls back to REFUND default).
        verify(moneyAccountService).recordPayout(org.mockito.ArgumentMatchers.argThat(x -> x.compareTo(new BigDecimal("10000")) == 0),
                any(), org.mockito.ArgumentMatchers.isNull(), eq(com.ebee.finance.entity.AccountTransaction.Type.CUSTOMER_REFUND),
                eq("ORDER"), eq(order.getId()), any());
        assertThat(ledgerBalance()).isZero();
        assertLedgerMatchesOrder(order);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(customer.getAdvanceBalance()).isZero();
    }

    @Test
    void cancelOrderPaidLater_toAdvance_moneyMovesToWalletNotLedger() {
        Order order = createOrder(10, "0");
        pay(order, "10000");

        settlement.cancel(order, RefundChoice.CREDIT_ADVANCE, true, "Order cancelled");

        assertThat(ledgerBalance()).isZero();
        assertLedgerMatchesOrder(order);
        assertThat(customer.getAdvanceBalance()).isEqualByComparingTo("10000");
    }

    @Test
    void cancelPartlyPaidOrder_refundsOnlyWhatWasPaid() {
        Order order = createOrder(10, "2500");

        var result = settlement.cancel(order, RefundChoice.REFUND, true, "Order cancelled");

        assertThat(result.refunded()).isEqualByComparingTo("2500");
        assertThat(ledgerBalance()).isZero();
        assertLedgerMatchesOrder(order);
    }

    @Test
    void cancelPaidOrder_withoutRefundChoice_isRejected() {
        Order order = createOrder(10, "10000");

        assertThatThrownBy(() -> settlement.cancel(order, null, true, "Order cancelled"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refundChoice");
    }

    // ── returns ────────────────────────────────────────────────────────

    @Test
    void partialReturnOnPartlyPaidOrder_reducesWhatIsOwed_noCashBack() {
        Order order = createOrder(10, "5000"); // owes 5,000

        var result = settlement.returnLines(order, List.of(line(order, 3)), RefundChoice.REFUND, "Order return");

        assertThat(result.refunded()).as("customer still owes, so no cash goes back").isZero();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("7000");
        assertThat(order.getOutstandingBalance()).isEqualByComparingTo("2000");
        assertLedgerMatchesOrder(order);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void partialReturnOnFullyPaidOrder_refundsReturnedValue() {
        Order order = createOrder(10, "10000");

        var result = settlement.returnLines(order, List.of(line(order, 3)), RefundChoice.REFUND, "Order return");

        assertThat(result.refunded()).isEqualByComparingTo("3000");
        assertThat(order.getPaidAmount()).isEqualByComparingTo("7000");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertLedgerMatchesOrder(order);
        assertThat(ledgerBalance()).isZero();
    }

    @Test
    void partialReturn_overpaymentGoesToAdvance() {
        Order order = createOrder(10, "9000"); // owes 1,000

        var result = settlement.returnLines(order, List.of(line(order, 3)), RefundChoice.CREDIT_ADVANCE, "Order return");

        // New total 7,000; paid 9,000 → 2,000 overpaid moves to the wallet.
        assertThat(result.toAdvance()).isEqualByComparingTo("2000");
        assertThat(customer.getAdvanceBalance()).isEqualByComparingTo("2000");
        assertThat(order.getOutstandingBalance()).isZero();
        assertLedgerMatchesOrder(order);
    }

    @Test
    void twoPartialReturnsThenRest_endsBalanced() {
        Order order = createOrder(10, "0");
        pay(order, "4000");

        settlement.returnLines(order, List.of(line(order, 2)), RefundChoice.REFUND, "Order return");
        assertLedgerMatchesOrder(order);
        settlement.returnLines(order, List.of(line(order, 5)), RefundChoice.REFUND, "Order return");
        assertLedgerMatchesOrder(order);
        settlement.returnLines(order, List.of(line(order, 3)), RefundChoice.REFUND, "Order return");

        assertThat(order.getItems().get(0).isFullyReturned()).isTrue();
        assertThat(order.getTotalAmount()).isZero();
        assertThat(ledgerBalance()).isZero();
        assertLedgerMatchesOrder(order);
    }

    @Test
    void returningMoreThanReturnable_isRejected() {
        Order order = createOrder(2, "0");

        assertThatThrownBy(() -> settlement.returnLines(order, List.of(line(order, 3)), RefundChoice.REFUND, "Order return"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Returnable: 2");
    }

    // ── VAT ────────────────────────────────────────────────────────────

    private Order createVatOrder() {
        Order order = createOrder(10, "0");          // 10 × 1,000 incl. VAT
        OrderItem item = order.getItems().get(0);
        item.setUnitTax(new BigDecimal("152.54"));
        item.setTaxAmount(new BigDecimal("1525.40"));
        order.setTaxTotal(new BigDecimal("1525.40"));
        return order;
    }

    @Test
    void partialReturn_reversesVatOnReturnedUnits_inTheReturnPeriod() {
        Order order = createVatOrder();

        settlement.returnLines(order, List.of(line(order, 3)), RefundChoice.REFUND, "Order return");

        assertThat(order.getTaxTotal()).isEqualByComparingTo("1067.78"); // 7 × 152.54
        verify(vatService).post(eq(com.ebee.tax.entity.VatEntry.Direction.OUTPUT),
                eq(com.ebee.tax.entity.VatEntry.SourceType.CREDIT_NOTE), eq(order.getId()), any(),
                org.mockito.ArgumentMatchers.argThat(a -> a.compareTo(new BigDecimal("-2542.38")) == 0),
                org.mockito.ArgumentMatchers.argThat(a -> a.compareTo(new BigDecimal("-457.62")) == 0));
    }

    @Test
    void cancel_reversesVatOnEverythingStillKept() {
        Order order = createVatOrder();
        settlement.returnLines(order, List.of(line(order, 4)), RefundChoice.REFUND, "Order return");

        settlement.cancel(order, RefundChoice.REFUND, true, "Order cancelled");

        // 6 units were still kept → 6 × 152.54 reversed on cancel.
        verify(vatService).post(eq(com.ebee.tax.entity.VatEntry.Direction.OUTPUT),
                eq(com.ebee.tax.entity.VatEntry.SourceType.CREDIT_NOTE), eq(order.getId()), any(),
                any(), org.mockito.ArgumentMatchers.argThat(a -> a.compareTo(new BigDecimal("-915.24")) == 0));
    }

    // ── stock ──────────────────────────────────────────────────────────

    @Test
    void cancellingComboLine_restoresComponentProducts() {
        Product a = Product.builder().id(UUID.randomUUID()).name("A").build();
        Product b = Product.builder().id(UUID.randomUUID()).name("B").build();
        Combo combo = Combo.builder().id(UUID.randomUUID()).name("Pack").build();
        combo.getItems().add(ComboItem.builder().product(a).quantity(2).build());
        combo.getItems().add(ComboItem.builder().product(b).quantity(1).build());
        OrderItem comboLine = OrderItem.builder().id(UUID.randomUUID()).combo(combo)
                .quantity(1).unitPrice(bd("2500")).subtotal(bd("2500")).build();
        Order order = Order.builder().id(UUID.randomUUID()).orderNumber("JF-A-COMBO").customer(customer)
                .totalAmount(bd("2500")).items(new ArrayList<>(List.of(comboLine))).build();
        ledgerService.recordCustomerInvoice(customer.getId(), order.getId(), bd("2500"), "Order", null);
        order.setOutstandingBalance(bd("2500"));

        settlement.cancel(order, RefundChoice.REFUND, true, "Order cancelled");

        // Combo components go back at the current average cost (null = not known per component).
        verify(inventoryService).returnStock(eq(List.of(a.getId(), b.getId())), eq(List.of(2, 1)),
                eq(java.util.Arrays.asList(null, null)), any());
        assertLedgerMatchesOrder(order);
    }
}
