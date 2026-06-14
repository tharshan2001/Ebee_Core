package com.ebee.agent.service;

import com.ebee.agent.dto.AgentRequest;
import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.dto.AgentUpdateRequest;
import com.ebee.agent.dto.AgentWithCredentialsResponse;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.agent.mapper.AgentMapper;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.enums.District;
import com.ebee.common.util.PasswordGenerator;
import com.ebee.common.util.PhoneUtils;
import com.ebee.agent.entity.AgentCodeSequence;
import com.ebee.agent.repository.AgentCodeSequenceRepository;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.alert.enums.AlertType;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.notification.NotificationPort;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final AgentDocumentService agentDocumentService;
    private final AgentMapper agentMapper;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPort notificationPort;
    private final CommissionConfigRepository commissionConfigRepository;
    private final AuditLogService auditLogService;
    private final AgentCodeSequenceRepository agentCodeSequenceRepository;


    @Transactional
    public AgentWithCredentialsResponse createAgent(AgentRequest request, boolean createdByAdmin) {
        request.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        District district = District.fromDisplayName(request.getDistrict());
        String agentCode = generateAgentCode(district);

        String generatedPassword = PasswordGenerator.generate();
        UserStatus initialStatus = createdByAdmin ? UserStatus.ACTIVE : UserStatus.INACTIVE;

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(generatedPassword))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .role(RoleType.AGENT)
                .status(initialStatus)
                .isActive(true)
                .mustChangePassword(true)
                .build();

        CommissionConfig defaultConfig = commissionConfigRepository
                .findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT)
                .orElseThrow(() -> new IllegalStateException(
                        "No active default commission config found. Configure one via PUT /api/commission-configs/default before creating agents."));

        User savedUser = userRepository.save(user);

        Agent agent = Agent.builder()
                .user(savedUser)
                .agentCode(agentCode)
                .address(request.getAddress())
                .district(district)
                .phoneNumber(request.getPhoneNumber())
                .commissionRate(defaultConfig.getCommissionRate())
                .commissionType(AgentCommissionType.DEFAULT)
                .nicNumber(request.getNicNumber())
                .bankAccountNumber(request.getBankAccountNumber())
                .bankName(request.getBankName())
                .bankBranch(request.getBankBranch())
                .isActive(true)
                .build();

        agentDocumentService.uploadNewDocuments(agent, request.getNicDocument(), request.getBankPassbookDocument());

        Agent savedAgent = agentRepository.save(agent);

        auditLogService.record(AuditAction.CREATE, "Agent", savedAgent.getId(),
                "Agent " + savedAgent.getAgentCode() + " (" + savedAgent.getUser().getEmail() + ") created");

        log.info("Generated password for {}: {}", savedUser.getEmail(), generatedPassword);
        sendWelcomeNotification(request.getFirstName(), request.getLastName(), agentCode, savedAgent, generatedPassword);

        return AgentWithCredentialsResponse.builder()
                .agentId(savedAgent.getId())
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .agentCode(savedAgent.getAgentCode())
                .address(savedAgent.getAddress())
                .district(savedAgent.getDistrict() != null ? savedAgent.getDistrict().getDisplayName() : null)
                .phoneNumber(savedAgent.getPhoneNumber())
                .commissionRate(savedAgent.getCommissionRate())
                .commissionType(savedAgent.getCommissionType())
                .nicNumber(savedAgent.getNicNumber())
                .bankAccountNumber(savedAgent.getBankAccountNumber())
                .bankName(savedAgent.getBankName())
                .bankBranch(savedAgent.getBankBranch())
                .userStatus(savedUser.getStatus())
                .isActive(savedAgent.getIsActive())
                .createdAt(savedAgent.getCreatedAt())
                .mustChangePassword(true)
                .build();
    }

    @Transactional
    public AgentResponse approveAgent(UUID id) {
        Agent agent = agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        User user = agent.getUser();
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        agent.setIsActive(true);
        Agent savedAgent = agentRepository.save(agent);

        auditLogService.record(AuditAction.AGENT_APPROVE, "Agent", id,
                "Agent " + agent.getAgentCode() + " approved");

        sendAgentStatusNotification(agent, "Approved",
                "Congratulations! Your agent application has been approved. You can now log in and start selling.",
                true);

        return agentMapper.toResponse(savedAgent);
    }

    @Transactional
    public AgentResponse rejectAgent(UUID id) {
        Agent agent = agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        User user = agent.getUser();
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);

        agent.setIsActive(false);
        Agent savedAgent = agentRepository.save(agent);

        auditLogService.record(AuditAction.AGENT_REJECT, "Agent", id,
                "Agent " + agent.getAgentCode() + " rejected");

        sendAgentStatusNotification(agent, "Rejected",
                "Your agent application has been rejected. Please contact support for more information.",
                false);

        return agentMapper.toResponse(savedAgent);
    }

    @Transactional
    public AgentResponse activateAgent(UUID id) {
        Agent agent = agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        User user = agent.getUser();
        user.setStatus(UserStatus.ACTIVE);
        user.setIsActive(true);
        userRepository.save(user);

        agent.setIsActive(true);
        Agent savedAgent = agentRepository.save(agent);

        auditLogService.record(AuditAction.AGENT_ACTIVATE, "Agent", id,
                "Agent " + agent.getAgentCode() + " activated");

        sendAgentStatusNotification(agent, "Activated",
                "Your account has been activated. You can now log in and access the system.",
                true);

        return agentMapper.toResponse(savedAgent);
    }

    @Transactional
    public AgentResponse deactivateAgent(UUID id) {
        Agent agent = agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        User user = agent.getUser();
        user.setStatus(UserStatus.INACTIVE);
        user.setIsActive(false);
        userRepository.save(user);

        agent.setIsActive(false);
        Agent savedAgent = agentRepository.save(agent);

        auditLogService.record(AuditAction.AGENT_DEACTIVATE, "Agent", id,
                "Agent " + agent.getAgentCode() + " deactivated");

        sendAgentStatusNotification(agent, "Deactivated",
                "Your account has been deactivated. If you believe this is an error, please contact support.",
                true);

        return agentMapper.toResponse(savedAgent);
    }

    @Transactional(readOnly = true)
    public AgentResponse getAgentById(UUID id) {
        Agent agent = agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        return agentMapper.toResponse(agent);
    }

    public AgentResponse getAgentByUserId(UUID userId) {
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
        return agentMapper.toResponse(agent);
    }

    public List<AgentResponse> getAllAgents() {
        return agentRepository.findAllWithUser().stream()
                .map(agentMapper::toResponse)
                .toList();
    }

    @Transactional
    public AgentResponse updateAgent(UUID id, AgentUpdateRequest request) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        request.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (!agent.getAgentCode().equals(request.getAgentCode()) &&
            agentRepository.existsByAgentCode(request.getAgentCode())) {
            throw new IllegalArgumentException("Agent code already exists");
        }

        agent.setAgentCode(request.getAgentCode());

        if (request.getDistrict() != null) {
            agent.setDistrict(District.fromDisplayName(request.getDistrict()));
        }

        if (request.getCommissionType() != null) {
            agent.setCommissionType(request.getCommissionType());
        }

        if (request.getCommissionRate() != null) {
            agent.setCommissionRate(request.getCommissionRate());
        } else if (request.getCommissionType() == AgentCommissionType.DEFAULT) {
            CommissionConfig defaultConfig = commissionConfigRepository
                    .findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT)
                    .orElseThrow(() -> new IllegalStateException(
                            "No active default commission config found. Configure one via PUT /api/commission-configs/default first."));
            agent.setCommissionRate(defaultConfig.getCommissionRate());
        }

        agent.setAddress(request.getAddress());
        agent.setPhoneNumber(request.getPhoneNumber());
        agent.setNicNumber(request.getNicNumber());
        agent.setBankAccountNumber(request.getBankAccountNumber());
        agent.setBankName(request.getBankName());
        agent.setBankBranch(request.getBankBranch());

        agentDocumentService.replaceDocuments(agent, request.getNicDocument(), request.getBankPassbookDocument());

        return agentMapper.toResponse(agentRepository.save(agent));
    }

    @Transactional(readOnly = true)
    public AgentResponse getAgentByAgentCode(String agentCode) {
        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "agentCode", agentCode));
        return agentMapper.toResponse(agent);
    }

    @Transactional
    public void deleteAgent(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        agent.setIsActive(false);
        agentRepository.save(agent);

        User user = agent.getUser();
        user.setIsActive(false);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);

        auditLogService.record(AuditAction.AGENT_DEACTIVATE, "Agent", id,
                "Agent " + agent.getAgentCode() + " deleted (deactivated)");
    }

    @Transactional
    public AgentResponse updateCommissionRate(UUID id, Double commissionRate) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        agent.setCommissionRate(commissionRate);
        return agentMapper.toResponse(agentRepository.save(agent));
    }

    @Transactional
    public void resetPassword(UUID id) {
        Agent agent = agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        User user = agent.getUser();
        String rawPassword = PasswordGenerator.generate();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null);
        userRepository.save(user);

        auditLogService.record(AuditAction.PASSWORD_RESET, "Agent", id,
                "Password reset for agent " + agent.getAgentCode());

        sendPasswordResetNotification(agent);
    }

    private void sendAgentStatusNotification(Agent agent, String statusLabel, String message, boolean includeSms) {
        String subject = "Agent Account " + statusLabel + " – Ebee";
        try {
            notificationPort.notifyAgent(agent, subject, message);
        } catch (Exception e) {
            log.warn("Failed to send {} notification to agent {}: {}", statusLabel.toLowerCase(),
                    agent.getAgentCode(), e.getMessage());
        }

        AlertType alertType = switch (statusLabel) {
            case "Approved" -> AlertType.AGENT_APPROVED;
            case "Rejected" -> AlertType.AGENT_REJECTED;
            case "Activated" -> AlertType.AGENT_ACTIVATED;
            case "Deactivated" -> AlertType.AGENT_DEACTIVATED;
            default -> null;
        };
        if (alertType != null && agent.getUser() != null) {
            notificationPort.createInternalAlert(agent.getUser(), subject, message, alertType, agent.getId(), "agent");
        }
    }

    private void sendWelcomeNotification(String firstName, String lastName, String agentCode,
                                          Agent agent, String rawPassword) {
        try {
            String subject = "Welcome to Ebee — Agent Registration Successful";
            String message = """
                    Dear %s %s,
                    
                    Congratulations! Your agent account has been successfully created.
                    
                    Login Credentials:
                    Agent Code : %s
                    Email      : %s
                    Password   : %s
                    
                    For security reasons, please change your password after logging in.
                    
                    Best regards,
                    Ebee Team
                    """.formatted(firstName, lastName, agentCode, agent.getUser().getEmail(), rawPassword);
            notificationPort.notifyAgent(agent, subject, message);
            notificationPort.createInternalAlert(agent.getUser(), subject, message, AlertType.AGENT_APPROVED, agent.getId(), "agent");
        } catch (Exception e) {
            log.warn("Failed to send welcome notification to agent {}: {}", agentCode, e.getMessage());
        }
    }

    private void sendPasswordResetNotification(Agent agent) {
        try {
            String subject = "Password Reset – Ebee";
            String message = """
                    Dear %s %s,
                    
                    Your password has been reset by an administrator.
                    
                    Email: %s
                    
                    Please log in and use the "Forgot Password" option to set a new password.
                    
                    Best regards,
                    Ebee Team
                    """.formatted(agent.getUser().getFirstName(), agent.getUser().getLastName(),
                    agent.getUser().getEmail());
            notificationPort.notifyAgent(agent, subject, message);
            notificationPort.createInternalAlert(agent.getUser(), subject, message,
                    AlertType.AGENT_ACTIVATED, agent.getId(), "agent");
        } catch (Exception e) {
            log.warn("Failed to send password reset notification to agent {}: {}", agent.getAgentCode(), e.getMessage());
        }
    }

    private String generateAgentCode(District district) {
        String districtName = district.name();
        AgentCodeSequence seq = agentCodeSequenceRepository.findByDistrictWithLock(districtName)
                .orElseGet(() -> agentCodeSequenceRepository.save(
                        AgentCodeSequence.builder().district(districtName).nextValue(1).build()
                ));
        int code = seq.getNextValue();
        seq.setNextValue(code + 1);
        agentCodeSequenceRepository.save(seq);
        return String.format("%s-%03d", districtName, code);
    }

}
