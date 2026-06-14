package com.ebee.report.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.report.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.NumberUtils;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionReportService {

    private final CommissionRepository commissionRepository;
    private final AgentRepository agentRepository;

    public CommissionSummaryReport getSummary(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        BigDecimal approved = commissionRepository.sumCommissionAmountByStatus(CommissionStatus.APPROVED);
        BigDecimal paid = commissionRepository.sumCommissionAmountByStatus(CommissionStatus.PAID);
        BigDecimal processing = commissionRepository.sumCommissionAmountByStatus(CommissionStatus.PROCESSING);
        BigDecimal pending = commissionRepository.sumCommissionAmountByStatus(CommissionStatus.PENDING);
        BigDecimal liability = approved.add(processing);

        return CommissionSummaryReport.builder()
                .totalApproved(approved)
                .totalPaid(paid)
                .totalProcessing(processing)
                .totalPending(pending)
                .outstandingLiability(liability)
                .period(range)
                .build();
    }

    public List<CommissionByAgentReport> getByAgent(PeriodRange range) {
        List<Agent> agents = agentRepository.findAllWithUser();
        List<UUID> agentIds = agents.stream().map(Agent::getId).toList();
        Map<UUID, Object[]> summaryMap = commissionRepository.findCommissionSummaryByAgentIds(agentIds)
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> row));

        List<CommissionByAgentReport> list = new ArrayList<>();
        for (Agent agent : agents) {
            Object[] summary = summaryMap.get(agent.getId());
            BigDecimal total = summary != null ? (BigDecimal) summary[1] : BigDecimal.ZERO;
            BigDecimal paid = summary != null ? (BigDecimal) summary[2] : BigDecimal.ZERO;
            BigDecimal pending = summary != null ? (BigDecimal) summary[3] : BigDecimal.ZERO;

            list.add(CommissionByAgentReport.builder()
                    .agentId(agent.getId())
                    .agentCode(agent.getAgentCode())
                    .agentName(agent.getUser().getFirstName() + " " + agent.getUser().getLastName())
                    .totalCommission(total)
                    .paidAmount(paid)
                    .pendingAmount(pending)
                    .build());
        }
        return list;
    }

    public List<CommissionPendingAgingReport> getPendingAging(PeriodRange range) {
        List<Commission> allPending = commissionRepository.findByStatus(CommissionStatus.PENDING);
        List<CommissionPendingAgingReport> list = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        long d0to7 = 0, d7to14 = 0, d14to30 = 0, d30plus = 0;
        BigDecimal a0to7 = BigDecimal.ZERO, a7to14 = BigDecimal.ZERO, a14to30 = BigDecimal.ZERO, a30plus = BigDecimal.ZERO;

        for (Commission c : allPending) {
            long days = ChronoUnit.DAYS.between(c.getCreatedAt(), now);
            BigDecimal amt = c.getCommissionAmount() != null ? c.getCommissionAmount() : BigDecimal.ZERO;
            if (days <= 7) { d0to7++; a0to7 = a0to7.add(amt); }
            else if (days <= 14) { d7to14++; a7to14 = a7to14.add(amt); }
            else if (days <= 30) { d14to30++; a14to30 = a14to30.add(amt); }
            else { d30plus++; a30plus = a30plus.add(amt); }
        }

        list.add(CommissionPendingAgingReport.builder().ageBucket("0-7 days").count(d0to7).totalAmount(a0to7).build());
        list.add(CommissionPendingAgingReport.builder().ageBucket("7-14 days").count(d7to14).totalAmount(a7to14).build());
        list.add(CommissionPendingAgingReport.builder().ageBucket("14-30 days").count(d14to30).totalAmount(a14to30).build());
        list.add(CommissionPendingAgingReport.builder().ageBucket("30+ days").count(d30plus).totalAmount(a30plus).build());
        return list;
    }

}
