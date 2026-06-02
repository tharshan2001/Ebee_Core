package com.ebee.agent.application.service;

import com.ebee.agent.application.dto.AgentApplicationRequest;
import com.ebee.agent.application.dto.AgentApplicationResponse;
import com.ebee.agent.application.entity.AgentApplication;
import com.ebee.agent.application.repository.AgentApplicationRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentApplicationService {

    private final AgentApplicationRepository repository;

    @Transactional
    public AgentApplicationResponse create(AgentApplicationRequest request) {
        AgentApplication application = AgentApplication.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .build();

        AgentApplication saved = repository.save(application);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AgentApplicationResponse> getAll() {
        return repository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentApplicationResponse getById(UUID id) {
        AgentApplication application = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AgentApplication", "id", id.toString()));
        return mapToResponse(application);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("AgentApplication", "id", id.toString());
        }
        repository.deleteById(id);
    }

    private AgentApplicationResponse mapToResponse(AgentApplication application) {
        return AgentApplicationResponse.builder()
                .id(application.getId())
                .firstName(application.getFirstName())
                .lastName(application.getLastName())
                .email(application.getEmail())
                .phoneNumber(application.getPhoneNumber())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
}
