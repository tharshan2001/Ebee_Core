package com.ebee.commission.service;

import com.ebee.commission.dto.CommissionConfigRequest;
import com.ebee.commission.dto.CommissionConfigResponse;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommissionConfigService {

    private final CommissionConfigRepository configRepository;
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
        mapper.updateConfigFromRequest(request, config);
        config = configRepository.save(config);
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
                .orElseThrow(() -> new EntityNotFoundException("No active default commission config found"));
        return mapper.toConfigResponse(config);
    }

    @Transactional
    public CommissionConfigResponse updateDefaultConfig(CommissionConfigRequest request) {
        Optional<CommissionConfig> existing = configRepository.findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT);
        CommissionConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            mapper.updateConfigFromRequest(request, config);
        } else {
            config = mapper.toConfigEntity(request);
            config.setType(CommissionType.DEFAULT);
        }
        if (request.getEffectiveFrom() == null) {
            config.setEffectiveFrom(LocalDateTime.now());
        }
        config.setIsActive(true);
        config = configRepository.save(config);
        return mapper.toConfigResponse(config);
    }

    private CommissionConfig getConfigEntity(UUID id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission config not found: " + id));
    }
}
