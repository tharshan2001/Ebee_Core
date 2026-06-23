package com.ebee.auth.service;

import com.ebee.auth.dto.IpWhitelistRequest;
import com.ebee.auth.dto.IpWhitelistResponse;
import com.ebee.auth.entity.IpWhitelist;
import com.ebee.auth.repository.IpWhitelistRepository;
import com.ebee.common.config.AppProperties;
import com.ebee.common.security.SecurityService;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IpWhitelistService {

    private final IpWhitelistRepository ipWhitelistRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final AppProperties appProperties;

    public boolean isAllowed(User user, String ip) {
        if (user.getRole() == RoleType.AGENT || user.getRole() == RoleType.DRIVER) {
            return true;
        }

        if (user.getRole() == RoleType.SUPER_ADMIN) {
            String superAdminIps = appProperties.getIpWhitelist().getSuperAdminIps();
            if (superAdminIps == null || superAdminIps.isBlank()) {
                return true;
            }
            return matchesAny(ip, superAdminIps);
        }

        if (user.getRole() == RoleType.ADMIN) {
            List<IpWhitelist> entries = ipWhitelistRepository.findByUserId(user.getId());
            if (entries.isEmpty()) {
                log.warn("Admin user {} ({}) has no IP whitelist entries — access blocked from IP {}",
                        user.getEmail(), user.getId(), ip);
                return false;
            }
            String normalizedIp = ip.replace("0:0:0:0:0:0:0:1", "::1");
            return entries.stream().anyMatch(e -> e.getIpAddress().equals(ip) || e.getIpAddress().equals(normalizedIp));
        }

        return true;
    }

    @Transactional(readOnly = true)
    public List<IpWhitelistResponse> getAllEntries() {
        List<IpWhitelist> entries = ipWhitelistRepository.findAll();
        return entries.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<IpWhitelistResponse> getEntriesForUser(UUID userId) {
        List<IpWhitelist> entries = ipWhitelistRepository.findByUserId(userId);
        return entries.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public IpWhitelistResponse addEntry(@Valid IpWhitelistRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getUserId()));

        if (user.getRole() == RoleType.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super admin IPs are managed via the SUPER_ADMIN_IPS environment variable");
        }

        if (ipWhitelistRepository.existsByUserIdAndIpAddress(request.getUserId(), request.getIpAddress())) {
            throw new IllegalArgumentException("IP " + request.getIpAddress() + " is already whitelisted for this user");
        }

        User currentUser = userRepository.findByEmail(securityService.getCurrentUserEmail())
                .orElseThrow(() -> new IllegalStateException("Current user not found"));

        IpWhitelist entry = IpWhitelist.builder()
                .user(user)
                .ipAddress(request.getIpAddress())
                .createdBy(currentUser)
                .build();

        entry = ipWhitelistRepository.save(entry);
        log.info("IP whitelist entry added: user={} ({}) ip={} by={}",
                user.getEmail(), user.getId(), request.getIpAddress(), currentUser.getEmail());

        return toResponse(entry);
    }

    @Transactional
    public void removeEntry(UUID id) {
        IpWhitelist entry = ipWhitelistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("IP whitelist entry not found: " + id));

        if (entry.getUser().getRole() == RoleType.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super admin IPs are managed via the SUPER_ADMIN_IPS environment variable");
        }

        ipWhitelistRepository.delete(entry);
        log.info("IP whitelist entry removed: user={} ip={}", entry.getUser().getEmail(), entry.getIpAddress());
    }

    private boolean matchesAny(String ip, String csv) {
        String normalized = ip.replace("0:0:0:0:0:0:0:1", "::1");
        for (String allowed : csv.split(",")) {
            if (allowed.trim().equals(ip) || allowed.trim().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private IpWhitelistResponse toResponse(IpWhitelist entry) {
        return IpWhitelistResponse.builder()
                .id(entry.getId())
                .userId(entry.getUser().getId())
                .userEmail(entry.getUser().getEmail())
                .ipAddress(entry.getIpAddress())
                .createdByEmail(entry.getCreatedBy() != null ? entry.getCreatedBy().getEmail() : null)
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
