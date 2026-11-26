package com.ebee.order.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.agent.entity.Agent;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.service.CustomerLedgerReconciliationService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.OrderSettlementRequest;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.enums.RefundChoice;
import com.ebee.product.entity.Product;
import com.ebee.support.RealDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real order → payment → cancel flow against the real schema, checking that the customer
 * ledger, the orders' outstanding balances and the customer's stored balance always agree
 * (what GET /api/accounting/reconciliation/customers checks in production).
 */
@Transactional
class CustomerDuesIntegrationTest extends RealDatabaseTest {

    @Autowired private OrderService orders;
    @Autowired private OrderPaymentService payments;
    @Autowired private InventoryService inventory;
    @Autowired private LedgerService ledger;
    @Autowired private CustomerLedgerReconciliationService reconciliation;

    private Agent agent;
    private Customer customer;
    private Product oil;

    @BeforeEach
    void setUpData() {
        ensureDefaultCommissionRate();
        agent = agent();
        customer = customer();
        customer.setAddress(com.ebee.customer.entity.Address.builder()
                .customer(customer).district(com.ebee.common.enums.District.MN)
                .city("Mannar").street("Main Street").country("Sri Lanka").build());
        customer = customerRepository.save(customer);
        oil = product("1000.00");
        inventory.recordPurchaseStocks(null, List.of(oil.getId()), List.of(10), List.of(new BigDecimal("600.00")),
                List.of("Opening"));
    }

    private OrderResponse codOrder(int qty) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductItemCode(oil.getItemCode());
        item.setQuantity(qty);
        OrderRequest request = new OrderRequest();
        request.setAgentCode(agent.getAgentCode());
        request.setCustomerPhoneNumber(customer.getPhoneNumber());
        request.setPaymentMethod(PaymentMethod.COD);
        request.setItems(List.of(item));
        return orders.createOrder(request);
    }

    private void assertBooksAgree() {
        var row = reconciliation.reconcile(false).stream()
                .filter(r -> r.customerId().equals(customer.getId())).findFirst().orElseThrow();
        assertThat(row.matches()).as("reconciliation row %s", row).isTrue();
    }

    @Test
    void validationErrors_reachTheCallerUnwrapped() {
        customer.setAddress(null);
        customerRepository.save(customer);
        // Used to surface as ExhaustedRetryException ("Cannot locate recovery method") → HTTP 500.
        assertThatThrownBy(() -> codOrder(1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no district");
    }

    @Test
    void codOrder_postsAnInvoice_andTheBooksAgree() {
        OrderResponse order = codOrder(3);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("3000.00");
        assertThat(ledger.getCustomerBalance(customer.getId())).isEqualByComparingTo("3000.00");
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getOutstandingBalance())
                .isEqualByComparingTo("3000.00");
        assertBooksAgree();
    }

    @Test
    void partAndFinalPayments_reduceEveryBalance_andOverpayingIsRefused() {
        OrderResponse order = codOrder(2);

        payments.recordPayment(order.getId(), new RecordPaymentRequest(new BigDecimal("500.00"), PaymentMethod.CASH, null, null));
        assertThat(ledger.getCustomerBalance(customer.getId())).isEqualByComparingTo("1500.00");
        assertBooksAgree();

        assertThatThrownBy(() -> payments.recordPayment(order.getId(),
                new RecordPaymentRequest(new BigDecimal("1500.01"), PaymentMethod.CASH, null, null)))
                .hasMessageContaining("cannot exceed outstanding");

        OrderResponse paid = payments.recordPayment(order.getId(),
                new RecordPaymentRequest(new BigDecimal("1500.00"), PaymentMethod.CASH, null, null));
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ledger.getCustomerBalance(customer.getId())).isEqualByComparingTo("0");
        assertBooksAgree();
    }

    @Test
    void cancellingAnUnpaidOrder_clearsTheDebt_andReturnsTheStock() {
        OrderResponse order = codOrder(4);

        orders.cancelOrder(order.getId(), new OrderSettlementRequest(RefundChoice.REFUND, "Customer changed mind"));

        assertThat(ledger.getCustomerBalance(customer.getId())).isEqualByComparingTo("0");
        assertThat(inventory.getAvailableStock(oil.getId())).isEqualTo(10);
        assertBooksAgree();
    }

    @Test
    void cancellingAPartPaidOrder_toAdvance_movesTheMoneyToTheWallet() {
        OrderResponse order = codOrder(1);
        payments.recordPayment(order.getId(), new RecordPaymentRequest(new BigDecimal("400.00"), PaymentMethod.CASH, null, null));

        orders.cancelOrder(order.getId(), new OrderSettlementRequest(RefundChoice.CREDIT_ADVANCE, null));

        Customer c = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(c.getAdvanceBalance()).isEqualByComparingTo("400.00");
        assertThat(c.getOutstandingBalance()).isEqualByComparingTo("0");
        assertBooksAgree();
    }
}
