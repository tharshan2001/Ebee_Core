package com.ebee.commission.service;

import com.ebee.commission.dto.CommissionConfigRequest;
import com.ebee.commission.dto.CommissionConfigResponse;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommissionConfigService {

    private final CommissionConfigRepository configRepository;
    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;

    @Transactional
    public CommissionConfigResponse createConfig(CommissionConfigRequest request) {
        if (request.getType() == CommissionType.DEFAULT &&
            configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT).isPresent()) {
            throw new IllegalStateException("A default commission config already exists. Use PUT /api/commission-configs/default to update it.");
        }
        CommissionConfig config = mapper.toConfigEntity(request);
        config = configRepository.save(config);
        return mapper.toConfigResponse(config);
    }

    @Transactional
    public CommissionConfigResponse updateConfig(UUID id, CommissionConfigRequest request) {
        CommissionConfig config = getConfigEntity(id);
        BigDecimal previousRate = config.getCommissionRate();
        mapper.updateConfigFromRequest(request, config);
        config = configRepository.save(config);
        if (config.getType() == CommissionType.DEFAULT && rateChanged(previousRate, request.getCommissionRate() != null ? BigDecimal.valueOf(request.getCommissionRate()) : null)) {
            recalculateLinkedCommissions(config);
        }
        return mapper.toConfigResponse(config);
    }

    @Transactional
    public void deleteConfig(UUID id) {
        CommissionConfig config = getConfigEntity(id);
        config.setIsActive(false);
        configRepository.save(config);
    }

    public CommissionConfigResponse getConfigById(UUID id) {
        return mapper.toConfigResponse(getConfigEntity(id));
    }

    public List<CommissionConfigResponse> getAllConfigs() {
        return configRepository.findAll().stream()
                .map(mapper::toConfigResponse)
                .toList();
    }

    public List<CommissionConfigResponse> getActiveConfigs() {
        return configRepository.findByIsActiveTrue().stream()
                .map(mapper::toConfigResponse)
                .toList();
    }

    public List<CommissionConfigResponse> getConfigsByType(CommissionType type) {
        return configRepository.findByType(type).stream()
                .map(mapper::toConfigResponse)
                .toList();
    }

    public List<CommissionConfigResponse> getActiveConfigsByType(CommissionType type) {
        return configRepository.findByIsActiveTrueAndType(type).stream()
                .map(mapper::toConfigResponse)
                .toList();
    }

    public CommissionConfigResponse getDefaultConfig() {
        CommissionConfig config = configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT)
                .orElseThrow(() -> new ResourceNotFoundException("No active default commission config found"));
        return mapper.toConfigResponse(config);
    }

    @Transactional
    public CommissionConfigResponse updateDefaultConfig(CommissionConfigRequest request) {
        Optional<CommissionConfig> existing = configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT);
        CommissionConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            BigDecimal previousRate = config.getCommissionRate();
            mapper.updateConfigFromRequest(request, config);
            config.setType(CommissionType.DEFAULT);
            if (config.getMinAmount() == null) config.setMinAmount(BigDecimal.ZERO);
            config = configRepository.save(config);
            if (rateChanged(previousRate, request.getCommissionRate() != null ? BigDecimal.valueOf(request.getCommissionRate()) : null)) {
                recalculateLinkedCommissions(config);
            }
        } else {
            config = mapper.toConfigEntity(request);
            config.setType(CommissionType.DEFAULT);
            if (config.getEffectiveFrom() == null) {
                config.setEffectiveFrom(LocalDateTime.now());
            }
            config.setIsActive(true);
            config = configRepository.save(config);
        }
        return mapper.toConfigResponse(config);
    }

    private boolean rateChanged(BigDecimal previousRate, BigDecimal newRate) {
        if (previousRate == null && newRate == null) {
            return false;
        }
        if (previousRate == null || newRate == null) {
            return true;
        }
        return previousRate.compareTo(newRate) != 0;
    }

    private void recalculateLinkedCommissions(CommissionConfig config) {
        List<Commission> commissions = commissionRepository.findByConfigIdAndStatusIn(
                config.getId(), List.of(CommissionStatus.CALCULATED, CommissionStatus.APPROVED));

        BigDecimal newRate = config.getCommissionRate();
        for (Commission commission : commissions) {
            BigDecimal amount = commission.getOrderAmount()
                    .multiply(newRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            commission.setCommissionRate(newRate);
            commission.setCommissionAmount(amount);
        }
        if (!commissions.isEmpty()) {
            commissionRepository.saveAll(commissions);
        }
    }

    private CommissionConfig getConfigEntity(UUID id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commission config not found: " + id));
    }
}
