package com.ebee.agent.application.service;

import com.ebee.agent.application.dto.AgentApplicationRequest;
import com.ebee.agent.application.dto.AgentApplicationResponse;
import com.ebee.agent.application.entity.AgentApplication;
import com.ebee.agent.application.repository.AgentApplicationRepository;
import com.ebee.alert.enums.AlertType;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.notification.NotificationPort;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.sender.EmailSender;
import com.ebee.notification.template.NotificationTemplateService;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApplicationService {

    private final AgentApplicationRepository repository;
    private final EmailSender emailSender;
    private final NotificationTemplateService templateService;
    private final UserRepository userRepository;
    private final NotificationPort notificationPort;

    @Transactional
    public AgentApplicationResponse create(AgentApplicationRequest request) {
        AgentApplication application = AgentApplication.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .build();

        AgentApplication saved = repository.save(application);

        sendConfirmationEmail(request);

        String alertTitle = "New Agent Application";
        String alertMessage = request.getFirstName() + " " + request.getLastName()
                + " (" + request.getEmail() + ") has submitted an agent application.";
        userRepository.findByRole(RoleType.ADMIN).forEach(admin ->
                notificationPort.createInternalAlert(admin, alertTitle, alertMessage,
                        AlertType.AGENT_APPROVED, saved.getId(), "agent_application"));

        return mapToResponse(saved);
    }

    private void sendConfirmationEmail(AgentApplicationRequest request) {
        try {
            String html = templateService.resolve("agent-application-confirmation.html", Map.of(
                    "firstName", request.getFirstName(),
                    "lastName", request.getLastName(),
                    "email", request.getEmail(),
                    "phoneNumber", request.getPhoneNumber()
            ));

            EmailRequest emailReq = EmailRequest.builder()
                    .to(request.getEmail())
                    .subject("Application Received – Ebee Agent Registration")
                    .body(html)
                    .html(true)
                    .build();

            emailSender.send(emailReq);
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
