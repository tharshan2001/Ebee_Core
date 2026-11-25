package com.ebee.payout.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.payout.entity.Payout;
import com.ebee.payout.repository.PayoutRepository;
import com.ebee.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayoutBatchTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private PayoutRepository payoutRepository;
    @Mock private com.ebee.payout.mapper.PayoutMapper payoutMapper;
    @Mock private com.ebee.common.service.PrintTemplateService printTemplateService;
    @InjectMocks private PayoutService payoutService;

    @Test
    void onlyCommissionsNotOnAPayout_areIncluded_atNetPaidAmount() {
        User user = User.builder().firstName("Ana").lastName("Agent").build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).user(user).build();
        Commission a = Commission.builder().id(UUID.randomUUID()).agent(agent).status(CommissionStatus.PAID)
                .commissionAmount(new BigDecimal("500.00")).paidAmount(new BigDecimal("200.00")).build();
        Commission legacy = Commission.builder().id(UUID.randomUUID()).agent(agent).status(CommissionStatus.PAID)
                .commissionAmount(new BigDecimal("300.00")).build(); // paid before V124: no paidAmount
        when(commissionRepository.findPaidNotInPayoutForAgents(List.of(agent.getId()))).thenReturn(List.of(a, legacy));
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(payoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        payoutService.createBatchPayouts(List.of(agent.getId()));

        ArgumentCaptor<Payout> saved = ArgumentCaptor.forClass(Payout.class);
        verify(payoutRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("500.00"); // 200 net + 300 legacy
        assertThat(saved.getValue().getCommissionIds()).containsExactlyInAnyOrder(a.getId(), legacy.getId());
        verify(commissionRepository, never()).findByStatusWithDetails(any());
    }

    @Test
    void nothingLeftToInclude_isRejected() {
        UUID agentId = UUID.randomUUID();
        when(commissionRepository.findPaidNotInPayoutForAgents(List.of(agentId))).thenReturn(List.of());

        assertThatThrownBy(() -> payoutService.createBatchPayouts(List.of(agentId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No paid commissions left");
        verify(payoutRepository, never()).save(any());
    }
}
