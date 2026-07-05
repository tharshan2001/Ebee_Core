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
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.common.util.PasswordGenerator;
import com.ebee.common.util.PhoneUtils;
import com.ebee.agent.entity.AgentCodeSequence;
import com.ebee.agent.repository.AgentCodeSequenceRepository;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.alert.enums.AlertType;
import com.ebee.notification.NotificationPort;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final AgentDocumentService agentDocumentService;
    private final AgentMapper agentMapper;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPort notificationPort;
    private final CommissionConfigRepository commissionConfigRepository;
    private final CommissionRepository commissionRepository;
    private final AuditLogService auditLogService;
    private final AgentCodeSequenceRepository agentCodeSequenceRepository;
    private final SecurityService securityService;


    @Transactional
    @Override
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

        Agent savedAgent = agentRepository.save(agent);

        agentDocumentService.uploadNewDocuments(agent, request.getNicDocument(), request.getBankPassbookDocument());

        auditLogService.record(AuditAction.CREATE, "Agent", savedAgent.getId(),
                "Agent " + savedAgent.getAgentCode() + " (" + savedAgent.getUser().getEmail() + ") created");

        log.info("Agent created: {}", savedUser.getEmail());
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
                .commissionRate(savedAgent.getCommissionRate().doubleValue())
                .effectiveCommissionRate(savedAgent.getCommissionRate().doubleValue())
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
    @Override
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

        return toAgentResponse(savedAgent);
    }

    @Transactional
    @Override
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

        return toAgentResponse(savedAgent);
    }

    @Transactional
    @Override
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

        return toAgentResponse(savedAgent);
    }

    @Transactional
    @Override
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

        return toAgentResponse(savedAgent);
    }

    @Transactional(readOnly = true)
    @Override
    public AgentResponse getAgentById(UUID id) {
        assertCanAccessAgent(id);
        Agent agent = findAgentById(id);
        return toAgentResponse(agent);
    }

    @Override
    public AgentResponse getAgentByUserId(UUID userId) {
        if (!securityService.isAdmin()) {
            UUID currentUserId = securityService.getCurrentUserId();
            if (!currentUserId.equals(userId)) {
                throw new IllegalArgumentException("Access denied: you can only access your own agent record");
            }
        }
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
        return toAgentResponse(agent);
    }

    @Override
    public List<AgentResponse> getAllAgents() {
        if (!securityService.isAdmin()) {
            UUID currentUserId = securityService.getCurrentUserId();
            return agentRepository.findByUserId(currentUserId)
                    .map(agent -> List.of(toAgentResponse(agent)))
                    .orElse(List.of());
        }
        return agentRepository.findAllWithUser().stream()
                .map(this::toAgentResponse)
                .toList();
    }

    @Override
    public Page<AgentResponse> getAllAgentsPaginated(Pageable pageable) {
        if (!securityService.isAdmin()) {
            UUID currentUserId = securityService.getCurrentUserId();
            Agent agent = agentRepository.findByUserId(currentUserId).orElse(null);
            if (agent == null) {
                return Page.empty();
            }
            return new PageImpl<>(List.of(toAgentResponse(agent)), pageable, 1);
        }
        Page<Agent> agents = agentRepository.findAllWithUser(pageable);
        return agents.map(this::toAgentResponse);
    }

    @Override
    public Page<AgentResponse> searchAgentsPaginated(String search, Pageable pageable) {
        if (!securityService.isAdmin()) {
            UUID currentUserId = securityService.getCurrentUserId();
            Agent agent = agentRepository.findByUserId(currentUserId).orElse(null);
            if (agent == null) {
                return Page.empty();
            }
            boolean matches = agent.getAgentCode().toLowerCase().contains(search.toLowerCase())
                    || agent.getUser().getFirstName().toLowerCase().contains(search.toLowerCase())
                    || agent.getUser().getLastName().toLowerCase().contains(search.toLowerCase());
            return matches ? new PageImpl<>(List.of(toAgentResponse(agent)), pageable, 1) : Page.empty();
        }
        Page<Agent> agents = agentRepository.findBySearchTerm(search, pageable);
        return agents.map(this::toAgentResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public List<AgentResponse> searchAgents(String search) {
        if (!securityService.isAdmin()) {
            UUID currentUserId = securityService.getCurrentUserId();
            Agent agent = agentRepository.findByUserId(currentUserId).orElse(null);
            if (agent == null) {
                return List.of();
            }
            boolean matches = agent.getAgentCode().toLowerCase().contains(search.toLowerCase())
                    || agent.getUser().getFirstName().toLowerCase().contains(search.toLowerCase())
                    || agent.getUser().getLastName().toLowerCase().contains(search.toLowerCase());
            return matches ? List.of(toAgentResponse(agent)) : List.of();
        }
        List<Agent> agents = agentRepository.findBySearchTerm(search);
        return agents.stream().map(this::toAgentResponse).toList();
    }

    @Transactional
    @Override
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
            agent.setCommissionRate(BigDecimal.valueOf(request.getCommissionRate()));
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

        Agent savedAgent = agentRepository.save(agent);
        agentDocumentService.replaceDocuments(savedAgent, request.getNicDocument(), request.getBankPassbookDocument());

        return toAgentResponse(savedAgent);
    }

    @Transactional(readOnly = true)
    @Override
    public AgentResponse getAgentByAgentCode(String agentCode) {
        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "agentCode", agentCode));
        if (!securityService.isAdmin()) {
            UUID currentUserId = securityService.getCurrentUserId();
            if (!agent.getUser().getId().equals(currentUserId)) {
                throw new IllegalArgumentException("Access denied: you can only access your own agent record");
            }
        }
        return toAgentResponse(agent);
    }

    @Transactional
    @Override
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
    @Override
    public AgentResponse updateCommissionRate(UUID id, Double commissionRate, AgentCommissionType commissionType) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        agent.setCommissionRate(BigDecimal.valueOf(commissionRate));
        if (commissionType != null) {
            agent.setCommissionType(commissionType);
        }
        return toAgentResponse(agentRepository.save(agent));
    }

    @Transactional
    @Override
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
            Map<String, String> p = new HashMap<>();
            p.put("agentName", agent.getUser().getFirstName());
            p.put("statusLabel", statusLabel.toLowerCase());
            p.put("statusMessage", message);
            notificationPort.notifyAgent(agent, subject, "agent-status-changed", p, Set.of());
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
            Map<String, String> p = new HashMap<>();
            p.put("firstName", firstName);
            p.put("lastName", lastName);
            p.put("agentCode", agentCode);
            p.put("email", agent.getUser().getEmail());
            p.put("password", rawPassword);
            notificationPort.notifyAgent(agent, subject, "agent-welcome", p, Set.of());
            String alertMsg = "Dear %s %s, welcome to Ebee! Agent code: %s".formatted(firstName, lastName, agentCode);
            notificationPort.createInternalAlert(agent.getUser(), subject, alertMsg,
                    AlertType.AGENT_APPROVED, agent.getId(), "agent");
        } catch (Exception e) {
            log.warn("Failed to send welcome notification to agent {}: {}", agentCode, e.getMessage());
        }
    }

    private void sendPasswordResetNotification(Agent agent) {
        try {
            String subject = "Password Reset – Ebee";
            Map<String, String> p = new HashMap<>();
            p.put("firstName", agent.getUser().getFirstName());
            p.put("lastName", agent.getUser().getLastName());
            p.put("email", agent.getUser().getEmail());
            notificationPort.notifyAgent(agent, subject, "agent-password-reset", p, Set.of());
            notificationPort.createInternalAlert(agent.getUser(), subject,
                    "Password reset for agent " + agent.getAgentCode(),
                    AlertType.AGENT_ACTIVATED, agent.getId(), "agent");
        } catch (Exception e) {
            log.warn("Failed to send password reset notification to agent {}: {}", agent.getAgentCode(), e.getMessage());
        }
    }

    private void assertCanAccessAgent(UUID agentId) {
        if (!securityService.isAdmin()) {
            UUID currentUserId = securityService.getCurrentUserId();
            Agent agent = agentRepository.findByIdWithUser(agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));
            if (!agent.getUser().getId().equals(currentUserId)) {
                throw new IllegalArgumentException("Access denied: you can only access your own agent record");
            }
        }
    }

    private Agent findAgentById(UUID id) {
        return agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
    }

    private AgentResponse toAgentResponse(Agent agent) {
        AgentResponse response = agentMapper.toResponse(agent);
        BigDecimal totalCommission = commissionRepository.sumCommissionAmountByAgentId(agent.getId());
        response.setTotalCommission(totalCommission != null ? totalCommission.doubleValue() : 0.0);
        if (agent.getCommissionType() == AgentCommissionType.PERFORMANCE) {
            response.setEffectiveCommissionRate(agent.getCommissionRate().doubleValue());
        } else {
            CommissionConfig config = commissionConfigRepository
                    .findFirstByTypeAndIsActiveTrue(CommissionType.DEFAULT)
                    .orElse(null);
            response.setEffectiveCommissionRate(config != null ? config.getCommissionRate().doubleValue() : agent.getCommissionRate().doubleValue());
        }
        return response;
    }

    private List<AgentResponse> toAgentResponseList(List<Agent> agents) {
        return agents.stream().map(this::toAgentResponse).toList();
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
