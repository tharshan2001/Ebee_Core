package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommissionLifecycleService {

    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;

    @Transactional
    public CommissionResponse approveCommission(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.APPROVED);
        commission.setApprovedAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse markAsProcessing(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.PROCESSING);
        commission = commissionRepository.save(commission);
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse markAsPaid(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.PAID);
        commission.setPaidAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);

        Agent agent = commission.getAgent();
        agent.setTotalCommission(agent.getTotalCommission() + commission.getCommissionAmount().doubleValue());
        agent.setTotalSales(agent.getTotalSales() + commission.getOrderAmount().doubleValue());

        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse rejectCommission(UUID commissionId, String reason) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.REJECTED);
        commission.setErrorMessage(reason);
        commission = commissionRepository.save(commission);
        return mapper.toResponse(commission);
    }

    private Commission getCommissionEntity(UUID id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission not found: " + id));
    }
}
