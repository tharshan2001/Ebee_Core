package com.ebee.agent.service;

import com.ebee.agent.dto.AgentRequest;
import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.dto.AgentUpdateRequest;
import com.ebee.agent.dto.AgentWithCredentialsResponse;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.common.enums.District;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.commission.entity.CommissionConfig;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.repository.CommissionConfigRepository;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.alert.enums.AlertType;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.S3Service;
import com.ebee.notification.NotificationService;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final CommissionConfigRepository commissionConfigRepository;
    private final CommissionRepository commissionRepository;

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
    private static final int PASSWORD_LENGTH = 12;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public AgentWithCredentialsResponse createAgent(AgentRequest request, boolean createdByAdmin) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        District district = District.fromDisplayName(request.getDistrict());
        String agentCode = generateAgentCode(district);

        String generatedPassword = generatePassword();
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

        List<CompletableFuture<String>> uploadFutures = new ArrayList<>();
        boolean hasNicDoc = request.getNicDocument() != null && !request.getNicDocument().isEmpty();
        boolean hasPassbookDoc = request.getBankPassbookDocument() != null && !request.getBankPassbookDocument().isEmpty();

        if (hasNicDoc) {
            uploadFutures.add(s3Service.uploadFile(request.getNicDocument(), "agents/nic"));
        } else {
            uploadFutures.add(CompletableFuture.completedFuture(null));
        }

        if (hasPassbookDoc) {
            uploadFutures.add(s3Service.uploadFile(request.getBankPassbookDocument(), "agents/passbook"));
        } else {
            uploadFutures.add(CompletableFuture.completedFuture(null));
        }

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();

        if (hasNicDoc) {
            String nicUrl = uploadFutures.get(0).join();
            agent.setNicDocumentUrl(nicUrl);
            agent.setNicDocumentName(request.getNicDocument().getOriginalFilename());
            agent.setNicDocumentType(request.getNicDocument().getContentType());
        }

        if (hasPassbookDoc) {
            String passbookUrl = uploadFutures.get(1).join();
            agent.setBankPassbookDocumentUrl(passbookUrl);
            agent.setBankPassbookDocumentName(request.getBankPassbookDocument().getOriginalFilename());
            agent.setBankPassbookDocumentType(request.getBankPassbookDocument().getContentType());
        }

        Agent savedAgent = agentRepository.save(agent);

        sendWelcomeNotification(request.getFirstName(), request.getLastName(), agentCode, generatedPassword,
                savedAgent);

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
                .generatedPassword(generatedPassword)
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

        sendAgentStatusNotification(agent, "Approved",
                "Congratulations! Your agent application has been approved. You can now log in and start selling.",
                true);

        return mapToResponse(savedAgent);
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

        sendAgentStatusNotification(agent, "Rejected",
                "Your agent application has been rejected. Please contact support for more information.",
                false);

        return mapToResponse(savedAgent);
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

        sendAgentStatusNotification(agent, "Activated",
                "Your account has been activated. You can now log in and access the system.",
                true);

        return mapToResponse(savedAgent);
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

        sendAgentStatusNotification(agent, "Deactivated",
                "Your account has been deactivated. If you believe this is an error, please contact support.",
                true);

        return mapToResponse(savedAgent);
    }

    @Transactional(readOnly = true)
    public AgentResponse getAgentById(UUID id) {
        Agent agent = agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        return mapToResponse(agent);
    }

    public AgentResponse getAgentByUserId(UUID userId) {
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
        return mapToResponse(agent);
    }

    public List<AgentResponse> getAllAgents() {
        return agentRepository.findAllWithUser().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AgentResponse updateAgent(UUID id, AgentUpdateRequest request) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

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

        List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();
        List<CompletableFuture<String>> uploadFutures = new ArrayList<>();

        boolean hasNicDoc = request.getNicDocument() != null && !request.getNicDocument().isEmpty();
        boolean hasPassbookDoc = request.getBankPassbookDocument() != null && !request.getBankPassbookDocument().isEmpty();

        if (hasNicDoc) {
            if (agent.getNicDocumentUrl() != null) {
                s3Service.deleteFile(agent.getNicDocumentUrl());
            }
            uploadFutures.add(s3Service.uploadFile(request.getNicDocument(), "agents/nic"));
        }

        if (hasPassbookDoc) {
            if (agent.getBankPassbookDocumentUrl() != null) {
                s3Service.deleteFile(agent.getBankPassbookDocumentUrl());
            }
            uploadFutures.add(s3Service.uploadFile(request.getBankPassbookDocument(), "agents/passbook"));
        }

        if (!uploadFutures.isEmpty()) {
            CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        }

        int futureIndex = 0;
        if (hasNicDoc) {
            String nicUrl = uploadFutures.get(futureIndex++).join();
            agent.setNicDocumentUrl(nicUrl);
            agent.setNicDocumentName(request.getNicDocument().getOriginalFilename());
            agent.setNicDocumentType(request.getNicDocument().getContentType());
        }

        if (hasPassbookDoc) {
            String passbookUrl = uploadFutures.get(futureIndex).join();
            agent.setBankPassbookDocumentUrl(passbookUrl);
            agent.setBankPassbookDocumentName(request.getBankPassbookDocument().getOriginalFilename());
            agent.setBankPassbookDocumentType(request.getBankPassbookDocument().getContentType());
        }

        return mapToResponse(agentRepository.save(agent));
    }

    @Transactional(readOnly = true)
    public AgentResponse getAgentByAgentCode(String agentCode) {
        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "agentCode", agentCode));
        return mapToResponse(agent);
    }

    @Transactional
    public void deleteAgent(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        agent.setIsActive(false);
        agentRepository.save(agent);
    }

    @Transactional
    public AgentResponse updateCommissionRate(UUID id, Double commissionRate) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));
        agent.setCommissionRate(commissionRate);
        return mapToResponse(agentRepository.save(agent));
    }

    @Transactional
    public void resetPassword(UUID id) {
        Agent agent = agentRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", id));

        User user = agent.getUser();
        String rawPassword = generatePassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null);
        userRepository.save(user);

        sendPasswordResetNotification(agent, rawPassword);
    }

    private AgentResponse mapToResponse(Agent agent) {
        AgentResponse response = new AgentResponse();
        response.setId(agent.getId());
        response.setUserId(agent.getUser().getId());
        response.setEmail(agent.getUser().getEmail());
        response.setFirstName(agent.getUser().getFirstName());
        response.setLastName(agent.getUser().getLastName());
        response.setFullName(agent.getUser().getFirstName() + " " + agent.getUser().getLastName());
        response.setAgentCode(agent.getAgentCode());
        response.setAddress(agent.getAddress());
        response.setDistrict(agent.getDistrict() != null ? agent.getDistrict().getDisplayName() : null);
        response.setPhoneNumber(agent.getPhoneNumber());
        response.setCommissionRate(agent.getCommissionRate());
        response.setCommissionType(agent.getCommissionType());
        response.setTotalSales(agent.getTotalSales());
        response.setTotalCommission(commissionRepository.sumCommissionAmountByAgentId(agent.getId()).doubleValue());
        response.setIsActive(agent.getIsActive());
        response.setUserStatus(agent.getUser().getStatus());

        response.setNicNumber(agent.getNicNumber());
        response.setNicDocumentUrl(agent.getNicDocumentUrl());
        response.setNicDocumentName(agent.getNicDocumentName());
        response.setNicDocumentType(agent.getNicDocumentType());
        response.setBankPassbookDocumentUrl(agent.getBankPassbookDocumentUrl());
        response.setBankPassbookDocumentName(agent.getBankPassbookDocumentName());
        response.setBankPassbookDocumentType(agent.getBankPassbookDocumentType());
        response.setBankAccountNumber(agent.getBankAccountNumber());
        response.setBankName(agent.getBankName());
        response.setBankBranch(agent.getBankBranch());

        response.setCreatedAt(agent.getCreatedAt());
        response.setUpdatedAt(agent.getUpdatedAt());
        return response;
    }

    private void sendAgentStatusNotification(Agent agent, String statusLabel, String message, boolean includeSms) {
        String subject = "Agent Account " + statusLabel + " – Ebee";
        try {
            notificationService.notifyAgent(agent, subject, message);
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
            notificationService.createInternalAlert(agent.getUser(), subject, message, alertType, agent.getId(), "agent");
        }
    }

    private void sendWelcomeNotification(String firstName, String lastName, String agentCode,
                                          String password, Agent agent) {
        try {
            String subject = "Welcome to Ebee — Agent Registration Successful";
            String message = """
                    Dear %s %s,
                    
                    Congratulations! Your agent account has been successfully created.
                    
                    Your Login Credentials:
                    ───────────────────────────
                    Agent Code : %s
                    Email      : %s
                    Password   : %s
                    ───────────────────────────
                    
                    Please log in and change your password at your earliest convenience.
                    
                    Best regards,
                    Ebee Team
                    """.formatted(firstName, lastName, agentCode, agent.getUser().getEmail(), password);
            notificationService.notifyAgent(agent, subject, message);
            notificationService.createInternalAlert(agent.getUser(), subject, message, AlertType.AGENT_APPROVED, agent.getId(), "agent");
        } catch (Exception e) {
            log.warn("Failed to send welcome notification to agent {}: {}", agentCode, e.getMessage());
        }
    }

    private void sendPasswordResetNotification(Agent agent, String password) {
        try {
            String subject = "Password Reset – Ebee";
            String message = """
                    Dear %s %s,
                    
                    Your password has been reset by an administrator.
                    
                    Your new login credentials:
                    ───────────────────────────
                    Email      : %s
                    Password   : %s
                    ───────────────────────────
                    
                    You will be required to change your password upon login.
                    
                    Best regards,
                    Ebee Team
                    """.formatted(agent.getUser().getFirstName(), agent.getUser().getLastName(),
                    agent.getUser().getEmail(), password);
            notificationService.notifyAgent(agent, subject, message);
            notificationService.createInternalAlert(agent.getUser(), subject, message,
                    AlertType.AGENT_ACTIVATED, agent.getId(), "agent");
        } catch (Exception e) {
            log.warn("Failed to send password reset notification to agent {}: {}", agent.getAgentCode(), e.getMessage());
        }
    }

    private String generateAgentCode(District district) {
        String prefix = district.name();
        String maxCode = agentRepository.findMaxAgentCodeByPattern(prefix + "-%");
        int nextSeq = 1;
        if (maxCode != null) {
            String[] parts = maxCode.split("-");
            nextSeq = Integer.parseInt(parts[parts.length - 1]) + 1;
        }
        return String.format("%s-%03d", prefix, nextSeq);
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
