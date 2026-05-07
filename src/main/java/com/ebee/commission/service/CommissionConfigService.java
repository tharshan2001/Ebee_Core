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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommissionConfigService {

    private final CommissionConfigRepository configRepository;
    private final CommissionMapper mapper;

    @Transactional
    public CommissionConfigResponse createConfig(CommissionConfigRequest request) {
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
                .collect(Collectors.toList());
    }

    public List<CommissionConfigResponse> getActiveConfigs() {
        return configRepository.findByIsActiveTrue().stream()
                .map(mapper::toConfigResponse)
                .collect(Collectors.toList());
    }

    public List<CommissionConfigResponse> getConfigsByType(CommissionType type) {
        return configRepository.findByType(type).stream()
                .map(mapper::toConfigResponse)
                .collect(Collectors.toList());
    }

    public List<CommissionConfigResponse> getActiveConfigsByType(CommissionType type) {
        return configRepository.findByIsActiveTrueAndType(type).stream()
                .map(mapper::toConfigResponse)
                .collect(Collectors.toList());
    }

    public List<CommissionConfigResponse> getDefaultConfigs() {
        return getConfigsByType(CommissionType.DEFAULT);
    }

    public List<CommissionConfigResponse> getPerformanceConfigs() {
        return getConfigsByType(CommissionType.PERFORMANCE);
    }

    private CommissionConfig getConfigEntity(UUID id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission config not found: " + id));
    }
}
