package com.ebee.agent.application.service;

import com.ebee.agent.application.dto.AgentApplicationRequest;
import com.ebee.agent.application.dto.AgentApplicationResponse;
import com.ebee.agent.application.entity.AgentApplication;
import com.ebee.agent.application.repository.AgentApplicationRepository;
import com.ebee.alert.enums.AlertType;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.result.Result;
import com.ebee.common.util.PhoneUtils;
import com.ebee.notification.NotificationPort;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ebee.user.enums.RoleType.ADMIN;
import static com.ebee.user.enums.RoleType.SUPER_ADMIN;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApplicationService {

    private final AgentApplicationRepository repository;
    private final UserRepository userRepository;
    private final NotificationPort notificationPort;

    @Transactional
    public AgentApplicationResponse create(AgentApplicationRequest request) {
        AgentApplication application = AgentApplication.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(PhoneUtils.normalize(request.getPhoneNumber()))
                .build();

        AgentApplication saved = repository.save(application);

        sendConfirmationEmail(request);

        String alertTitle = "New Agent Application";
        String alertMessage = request.getFirstName() + " " + request.getLastName()
                + " (" + request.getEmail() + ") has submitted an agent application.";
        userRepository.findByRoleIn(List.of(ADMIN, SUPER_ADMIN)).forEach(admin ->
                notificationPort.createInternalAlert(admin, alertTitle, alertMessage,
                        AlertType.AGENT_APPLICATION_SUBMITTED, saved.getId(), "agent_application"));

        return mapToResponse(saved);
    }

    private void sendConfirmationEmail(AgentApplicationRequest request) {
        try {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("firstName", request.getFirstName());
            placeholders.put("lastName", request.getLastName());
            placeholders.put("email", request.getEmail());
            placeholders.put("phoneNumber", request.getPhoneNumber());

            notificationPort.sendHtmlEmail(
                    request.getEmail(),
                    "Application Received – Ebee Agent Registration",
                    "agent-application-confirmation-email.html",
                    placeholders
            );
            log.info("Confirmation email sent to: {}", request.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send confirmation email to {}: {}", request.getEmail(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AgentApplicationResponse> getAll() {
        return repository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Result<AgentApplicationResponse> getById(UUID id) {
        return repository.findById(id)
                .map(app -> Result.<AgentApplicationResponse>ok(mapToResponse(app)))
                .orElseGet(() -> Result.<AgentApplicationResponse>notFound("AgentApplication", "id", id));
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
