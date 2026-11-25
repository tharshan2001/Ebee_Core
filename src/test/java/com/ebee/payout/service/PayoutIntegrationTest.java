package com.ebee.payout.service;

import com.ebee.agent.entity.Agent;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.customer.entity.Customer;
import com.ebee.payout.dto.PayoutResponse;
import com.ebee.support.RealDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Payout batches against the real schema (the "already on a payout" check is SQL). */
@Transactional
class PayoutIntegrationTest extends RealDatabaseTest {

    @Autowired private PayoutService payouts;
    @Autowired private CommissionRepository commissions;

    private Commission commission(Agent agent, Customer customer, CommissionStatus status, String amount, String paid) {
        return commissions.save(Commission.builder()
                .agent(agent)
                .order(order(agent, customer, "10000.00"))
                .orderAmount(new BigDecimal("10000.00"))
                .commissionRate(new BigDecimal("7.5"))
                .commissionAmount(new BigDecimal(amount))
                .paidAmount(paid == null ? null : new BigDecimal(paid))
                .status(status)
                .approvedAt(LocalDateTime.now().minusDays(20))
                .paidAt(status == CommissionStatus.PAID ? LocalDateTime.now() : null)
                .build());
    }

    @Test
    void batch_paysTheNetCashOfPaidCommissions_once() {
        Agent agent = agent();
        Customer customer = customer();
        Commission a = commission(agent, customer, CommissionStatus.PAID, "750.00", "500.00"); // 250 clawback recovered
        Commission b = commission(agent, customer, CommissionStatus.PAID, "300.00", "300.00");
        commission(agent, customer, CommissionStatus.APPROVED, "999.00", null);              // not paid yet: excluded

        List<PayoutResponse> batch = payouts.createBatchPayouts(List.of(agent.getId()));

        assertThat(batch).singleElement().satisfies(p -> {
            assertThat(p.getTotalAmount()).isEqualByComparingTo("800.00");
            assertThat(p.getCommissionIds()).containsExactlyInAnyOrder(a.getId(), b.getId());
        });

        // Running the batch again must not pay the same commissions twice.
        assertThatThrownBy(() -> payouts.createBatchPayouts(List.of(agent.getId())))
                .hasMessageContaining("No paid commissions left");

        // A commission paid later goes on the next payout on its own.
        Commission c = commission(agent, customer, CommissionStatus.PAID, "120.00", "120.00");
        assertThat(payouts.createBatchPayouts(List.of(agent.getId())))
                .singleElement().satisfies(p -> assertThat(p.getCommissionIds()).containsExactly(c.getId()));
    }

    @Test
    void processingIsOneWay() {
        Agent agent = agent();
        commission(agent, customer(), CommissionStatus.PAID, "100.00", "100.00");
        PayoutResponse payout = payouts.createBatchPayouts(List.of(agent.getId())).get(0);

        payouts.processPayout(payout.getId());

        assertThatThrownBy(() -> payouts.processPayout(payout.getId())).hasMessageContaining("already processed");
    }
}
