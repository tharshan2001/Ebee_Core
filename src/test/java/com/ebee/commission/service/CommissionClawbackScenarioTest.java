package com.ebee.commission.service;

import com.ebee.accounting.entity.AgentLedger;
import com.ebee.accounting.repository.AgentLedgerRepository;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.accounting.service.AccountingPeriodService;
import com.ebee.accounting.service.LedgerService;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.security.SecurityService;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Commission adjustments, clawbacks and clawback recovery on the real lifecycle, payment and
 * ledger services with an in-memory agent ledger. Agent ledger balance = credit − debit
 * (positive = we owe the agent, negative = the agent owes us).
 */
class CommissionClawbackScenarioTest {

    private final List<AgentLedger> agentLedger = new ArrayList<>();
    private final List<Commission> commissions = new ArrayList<>();
    private CommissionLifecycleService lifecycle;
    private CommissionPaymentService payments;
    private Agent agent;

    @BeforeEach
    void setUp() {
        AgentLedgerRepository agentLedgerRepo = mock(AgentLedgerRepository.class);
        when(agentLedgerRepo.save(any())).thenAnswer(inv -> {
            agentLedger.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        LedgerService ledgerService = new LedgerService(mock(CustomerLedgerRepository.class),
                mock(SupplierLedgerRepository.class), agentLedgerRepo, mock(AccountingPeriodService.class));

        CommissionRepository commissionRepo = mock(CommissionRepository.class);
        when(commissionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commissionRepo.findCommissionByOrderId(any())).thenAnswer(inv ->
                commissions.stream().filter(c -> c.getOrder().getId().equals(inv.getArgument(0))).findFirst());
        when(commissionRepo.findByIdWithDetails(any())).thenAnswer(inv ->
                commissions.stream().filter(c -> c.getId().equals(inv.getArgument(0))).findFirst());
        when(commissionRepo.findById(any())).thenAnswer(inv ->
                commissions.stream().filter(c -> c.getId().equals(inv.getArgument(0))).findFirst());

        SecurityService security = mock(SecurityService.class);
        when(security.getCurrentUserId()).thenReturn(UUID.randomUUID());
        AuditLogService audit = mock(AuditLogService.class);
        CommissionMapper mapper = mock(CommissionMapper.class);
        CommissionNotificationService notifications = mock(CommissionNotificationService.class);
        AgentRepository agentRepo = mock(AgentRepository.class);
        when(agentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        payments = new CommissionPaymentService(commissionRepo, mapper, notifications, audit, ledgerService, security,
                mock(com.ebee.finance.service.MoneyAccountService.class));
        lifecycle = new CommissionLifecycleService(commissionRepo, mapper, notifications, payments, audit,
                ledgerService, security, agentRepo);

        agent = Agent.builder().id(UUID.randomUUID())
                .totalSales(BigDecimal.ZERO).totalCommission(BigDecimal.ZERO).build();
        when(agentRepo.findById(agent.getId())).thenReturn(Optional.of(agent));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private BigDecimal agentBalance() {
        return agentLedger.stream().map(e -> e.getCredit().subtract(e.getDebit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 10 units × 1,000 at 10% commission, CALCULATED with its ledger credit, like createOrder does. */
    private Commission newOrderWithCommission(String total, String rate) {
        OrderItem item = OrderItem.builder().id(UUID.randomUUID()).quantity(10)
                .unitPrice(new BigDecimal(total).divide(BigDecimal.TEN)).build();
        Order order = Order.builder().id(UUID.randomUUID()).orderNumber("JF-A-" + commissions.size())
                .totalAmount(new BigDecimal(total)).items(new ArrayList<>(List.of(item))).build();
        BigDecimal amount = new BigDecimal(total).multiply(new BigDecimal(rate)).divide(BigDecimal.valueOf(100));
        Commission c = Commission.builder().id(UUID.randomUUID()).agent(agent).order(order)
                .orderAmount(order.getTotalAmount()).commissionRate(new BigDecimal(rate))
                .commissionAmount(amount).status(CommissionStatus.CALCULATED).build();
        commissions.add(c);
        new LedgerServiceAccess().credit(c);
        return c;
    }

    /** Posts the calculation CREDIT exactly as CommissionCalculationService does. */
    private class LedgerServiceAccess {
        void credit(Commission c) {
            AgentLedger e = AgentLedger.builder().agentId(agent.getId()).commissionId(c.getId())
                    .debit(BigDecimal.ZERO).credit(c.getCommissionAmount()).transactionType("CREDIT").build();
            agentLedger.add(e);
        }
    }

    private void pay(Commission c) {
        c.setStatus(CommissionStatus.PROCESSING);
        payments.markAsPaid(c.getId());
    }

    /** Return {@code units} of the order's single line and lower its total, as settlement does. */
    private void returnUnits(Commission c, int units) {
        Order order = c.getOrder();
        OrderItem item = order.getItems().get(0);
        item.setReturnedQuantity(item.getReturnedQuantity() + units);
        order.setTotalAmount(order.getTotalAmount().subtract(item.getUnitPrice().multiply(BigDecimal.valueOf(units))));
    }

    // ── scenarios ──────────────────────────────────────────────────────

    @Test
    void partialReturnBeforePayment_reducesCommissionAndLedger() {
        Commission c = newOrderWithCommission("10000", "10");

        returnUnits(c, 3);
        lifecycle.adjustCommissionForOrder(c.getOrder());

        assertThat(c.getStatus()).isEqualTo(CommissionStatus.CALCULATED);
        assertThat(c.getCommissionAmount()).isEqualByComparingTo("700.00");
        assertThat(agentBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void partialReturnAfterPayment_clawsBackTheDifference() {
        Commission c = newOrderWithCommission("10000", "10");
        pay(c);
        assertThat(agentBalance()).isZero();

        returnUnits(c, 3);
        lifecycle.adjustCommissionForOrder(c.getOrder());

        assertThat(c.getStatus()).isEqualTo(CommissionStatus.PAID);
        assertThat(c.getClawbackAmount()).isEqualByComparingTo("300.00");
        assertThat(agent.getClawbackBalance()).isEqualByComparingTo("300.00");
        assertThat(agentBalance()).as("agent owes 300").isEqualByComparingTo("-300.00");
        assertThat(agent.getTotalCommission()).isEqualByComparingTo("700.00");
    }

    @Test
    void clawbackIsRecoveredFromNextPayment() {
        Commission first = newOrderWithCommission("10000", "10");
        pay(first);
        returnUnits(first, 3);
        lifecycle.adjustCommissionForOrder(first.getOrder()); // agent owes 300

        Commission second = newOrderWithCommission("5000", "10"); // 500 earned
        pay(second);

        assertThat(second.getPaidAmount()).as("500 earned − 300 recovered").isEqualByComparingTo("200.00");
        assertThat(agent.getClawbackBalance()).isZero();
        assertThat(agentBalance()).isZero();
    }

    @Test
    void clawbackLargerThanNextCommission_carriesOver() {
        Commission first = newOrderWithCommission("10000", "10");
        pay(first);
        lifecycle.cancelCommissionForOrder(first.getOrder().getId()); // agent owes 1,000

        Commission second = newOrderWithCommission("4000", "10"); // 400 earned
        pay(second);

        assertThat(second.getPaidAmount()).isZero();
        assertThat(agent.getClawbackBalance()).isEqualByComparingTo("600.00");
        assertThat(agentBalance()).isEqualByComparingTo("-600.00");
    }

    @Test
    void fullReturnAfterPayment_cancelsAndClawsBackEverythingOnce() {
        Commission c = newOrderWithCommission("10000", "10");
        pay(c);
        returnUnits(c, 3);
        lifecycle.adjustCommissionForOrder(c.getOrder()); // 300 clawed back

        returnUnits(c, 7);
        lifecycle.adjustCommissionForOrder(c.getOrder()); // everything back → cancel

        assertThat(c.getStatus()).isEqualTo(CommissionStatus.CANCELLED);
        assertThat(c.getClawbackAmount()).isEqualByComparingTo("1000.00");
        assertThat(agentBalance()).isEqualByComparingTo("-1000.00");

        lifecycle.cancelCommissionForOrder(c.getOrder().getId()); // idempotent
        assertThat(agentBalance()).isEqualByComparingTo("-1000.00");
    }

    @Test
    void rejectedCommission_netsToZeroOnLedger() {
        Commission c = newOrderWithCommission("10000", "10");
        lifecycle.approveCommission(c.getId());

        lifecycle.rejectCommission(c.getId(), "Duplicate order");

        assertThat(c.getStatus()).isEqualTo(CommissionStatus.REJECTED);
        assertThat(agentBalance()).isZero();
    }
}
