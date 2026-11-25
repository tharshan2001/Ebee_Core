package com.ebee.payout.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.PrintTemplateService;
import com.ebee.payout.dto.PayoutResponse;
import com.ebee.payout.entity.Payout;
import com.ebee.payout.enums.PayoutStatus;
import com.ebee.payout.mapper.PayoutMapper;
import com.ebee.payout.repository.PayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final CommissionRepository commissionRepository;
    private final AgentRepository agentRepository;
    private final PayoutMapper payoutMapper;
    private final PrintTemplateService printTemplateService;

    @Transactional(readOnly = true)
    public List<PayoutResponse> getAllPayouts() {
        List<Payout> payouts = payoutRepository.findAll();
        return payoutMapper.toResponseList(payouts);
    }

    @Transactional(readOnly = true)
    public PayoutResponse getPayoutById(UUID id) {
        Payout payout = payoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", "id", id));
        return payoutMapper.toResponse(payout);
    }

    @Transactional(readOnly = true)
    public List<PayoutResponse> getPayoutsByAgentId(UUID agentId) {
        List<Payout> payouts = payoutRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
        return payoutMapper.toResponseList(payouts);
    }

    @Transactional
    public List<PayoutResponse> createBatchPayouts(List<UUID> agentIds) {
        // Only PAID commissions not already on a payout — re-running a batch used to include every
        // paid commission again and produce duplicate payouts.
        List<Commission> paidCommissions = commissionRepository.findPaidNotInPayoutForAgents(agentIds);
        if (paidCommissions.isEmpty()) {
            throw new IllegalArgumentException("No paid commissions left to put on a payout for these agents");
        }

        Map<UUID, List<Commission>> commissionsByAgent = paidCommissions.stream()
                .collect(Collectors.groupingBy(c -> c.getAgent().getId()));

        List<Payout> payouts = new ArrayList<>();

        for (Map.Entry<UUID, List<Commission>> entry : commissionsByAgent.entrySet()) {
            UUID agentId = entry.getKey();
            List<Commission> commissions = entry.getValue();

            Agent agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));

            // Net cash actually paid (after any clawback recovered at payment time).
            BigDecimal totalAmount = commissions.stream()
                    .map(Commission::getNetPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<UUID> commissionIds = commissions.stream()
                    .map(Commission::getId)
                    .toList();

            Payout payout = Payout.builder()
                    .agentId(agentId)
                    .agentName(agent.getUser().getFirstName() + " " + agent.getUser().getLastName())
                    .totalAmount(totalAmount)
                    .commissionIds(new ArrayList<>(commissionIds))
                    .status(PayoutStatus.PENDING)
                    .build();

            payouts.add(payoutRepository.save(payout));

            log.info("Created payout for agent {} amount {}", agentId, totalAmount);
        }

        return payoutMapper.toResponseList(payouts);
    }

    @Transactional
    public PayoutResponse processPayout(UUID id) {
        Payout payout = payoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", "id", id));

        if (payout.getStatus() == PayoutStatus.PROCESSED) {
            throw new IllegalStateException("Payout " + id + " is already processed");
        }

        payout.setStatus(PayoutStatus.PROCESSED);
        payout.setPaidAt(LocalDateTime.now());
        payout = payoutRepository.save(payout);

        log.info("Payout {} processed", id);
        return payoutMapper.toResponse(payout);
    }

    public byte[] getPayoutPdf(UUID id) {
        Payout payout = payoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", "id", id));

        log.info("PDF requested for payout {}", id);

        var variables = new java.util.LinkedHashMap<String, Object>();
        variables.put("payoutId", payout.getId().toString());
        variables.put("agentName", payout.getAgentName());
        variables.put("totalAmount", payout.getTotalAmount());
        variables.put("createdAt", payout.getCreatedAt());
        variables.put("status", payout.getStatus().name());

        return printTemplateService.renderToPdf("payout-statement", variables);
    }
}
