package com.ebee.payout.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.security.SecurityService;
import com.ebee.common.service.PrintTemplateService;
import com.ebee.payout.entity.Payout;
import com.ebee.payout.enums.PayoutStatus;
import com.ebee.payout.mapper.PayoutMapper;
import com.ebee.payout.repository.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** An agent may download only their own payout statement; admins may download any. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayoutPdfAccessTest {

    @Mock private PayoutRepository payoutRepository;
    @Mock private CommissionRepository commissionRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private PayoutMapper payoutMapper;
    @Mock private PrintTemplateService printTemplateService;
    @Mock private SecurityService securityService;
    @InjectMocks private PayoutService payoutService;

    private final UUID ownerAgentId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Payout payout;

    @BeforeEach
    void setUp() {
        payout = Payout.builder().id(UUID.randomUUID()).agentId(ownerAgentId).agentName("Ana Agent")
                .totalAmount(new BigDecimal("100.00")).status(PayoutStatus.PENDING).build();
        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(printTemplateService.renderToPdf(eq("payout-statement"), any())).thenReturn(new byte[] {1});
        when(securityService.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void admin_canDownloadAnyStatement() {
        when(securityService.isAdmin()).thenReturn(true);
        assertThat(payoutService.getPayoutPdf(payout.getId())).hasSize(1);
    }

    @Test
    void agent_canDownloadOwnStatement() {
        when(securityService.isAdmin()).thenReturn(false);
        when(agentRepository.findByUserId(userId)).thenReturn(Optional.of(Agent.builder().id(ownerAgentId).build()));
        assertThat(payoutService.getPayoutPdf(payout.getId())).hasSize(1);
    }

    @Test
    void agent_cannotDownloadAnotherAgentsStatement() {
        when(securityService.isAdmin()).thenReturn(false);
        when(agentRepository.findByUserId(userId)).thenReturn(Optional.of(Agent.builder().id(UUID.randomUUID()).build()));
        assertThatThrownBy(() -> payoutService.getPayoutPdf(payout.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userWithoutAgentProfile_isDenied() {
        when(securityService.isAdmin()).thenReturn(false);
        when(agentRepository.findByUserId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> payoutService.getPayoutPdf(payout.getId())).isInstanceOf(AccessDeniedException.class);
    }
}
