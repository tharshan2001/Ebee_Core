package com.ebee.auth.service;

import com.ebee.auth.dto.IpWhitelistRequest;
import com.ebee.auth.dto.IpWhitelistResponse;
import com.ebee.auth.dto.WhitelistStatusResponse;
import com.ebee.auth.entity.IpWhitelist;
import com.ebee.auth.entity.WhitelistConfig;
import com.ebee.auth.repository.IpWhitelistRepository;
import com.ebee.auth.repository.WhitelistConfigRepository;
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
    private final WhitelistConfigRepository whitelistConfigRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    public boolean isGloballyEnabled() {
        return whitelistConfigRepository.findById(1)
                .map(WhitelistConfig::isEnabled)
                .orElse(true);
    }

    @Transactional
    public WhitelistStatusResponse setWhitelistEnabled(boolean enabled) {
        WhitelistConfig config = whitelistConfigRepository.findById(1)
                .orElseGet(() -> {
                    WhitelistConfig c = new WhitelistConfig();
                    c.setId(1);
                    return c;
                });
        config.setEnabled(enabled);
        config.setUpdatedAt(java.time.LocalDateTime.now());
        whitelistConfigRepository.save(config);
        log.info("IP whitelist global toggle set to: {}", enabled);
        return new WhitelistStatusResponse(enabled);
    }

    public boolean isAllowed(User user, String ip) {
        if (!isGloballyEnabled()) {
            return true;
        }

        if (user.getRole() == RoleType.AGENT || user.getRole() == RoleType.DRIVER) {
            return true;
        }

        if (user.getRole() == RoleType.SUPER_ADMIN) {
            return true;
        }

        if (user.getRole() == RoleType.ADMIN) {
            List<IpWhitelist> entries = ipWhitelistRepository.findByUserId(user.getId());
            if (entries.isEmpty()) {
                log.warn("Admin user {} ({}) has no IP whitelist entries — access blocked from IP {}",
                        user.getEmail(), user.getId(), ip);
                return false;
            }
            String detected = normalizeIp(ip);
            return entries.stream().anyMatch(e -> ipMatches(e.getIpAddress(), detected));
        }

        return true;
    }

    static String normalizeIp(String ip) {
        if (ip == null) return "";
        String normalized = ip.trim();
        if (normalized.startsWith("::ffff:")) {
            normalized = normalized.substring(7);
        }
        normalized = normalized.replace("0:0:0:0:0:0:0:1", "::1");
        if (normalized.equals("127.0.0.1") || normalized.equals("::1")) {
            normalized = "127.0.0.1";
        }
        return normalized;
    }

    static boolean ipMatches(String stored, String detected) {
        if (stored == null || detected == null) return false;
        String s = normalizeIp(stored);
        String d = normalizeIp(detected);
        if (s.equals(d)) return true;
        if (s.contains(",")) {
            for (String part : s.split(",")) {
                if (ipMatches(part.trim(), d)) return true;
            }
        }
        if (s.contains("/")) {
            return cidrMatches(s, d);
        }
        return false;
    }

    private static boolean cidrMatches(String cidr, String ip) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) return false;
        try {
            int prefixLen = Integer.parseInt(parts[1]);
            if (prefixLen < 0 || prefixLen > 32) return false;
            long cidrAddr = ipToLong(parts[0]);
            long ipAddr = ipToLong(ip);
            long mask = prefixLen == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixLen)) & 0xFFFFFFFFL;
            return (cidrAddr & mask) == (ipAddr & mask);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (String octet : octets) {
            result = (result << 8) | Integer.parseInt(octet);
        }
        return result;
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
            throw new IllegalArgumentException("Super admins are not restricted by IP whitelist");
        }

        String normalizedIp = normalizeIp(request.getIpAddress());

        if (ipWhitelistRepository.existsByUserIdAndIpAddress(request.getUserId(), normalizedIp)) {
            throw new IllegalArgumentException("IP " + request.getIpAddress() + " is already whitelisted for this user");
        }

        User currentUser = userRepository.findByEmail(securityService.getCurrentUserEmail())
                .orElseThrow(() -> new IllegalStateException("Current user not found"));

        IpWhitelist entry = IpWhitelist.builder()
                .user(user)
                .ipAddress(normalizedIp)
                .createdBy(currentUser)
                .build();

        entry = ipWhitelistRepository.save(entry);
        log.info("IP whitelist entry added: user={} ({}) ip={} (normalized: {}) by={}",
                user.getEmail(), user.getId(), request.getIpAddress(), normalizedIp, currentUser.getEmail());

        return toResponse(entry);
    }

    @Transactional
    public void removeEntry(UUID id) {
        IpWhitelist entry = ipWhitelistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("IP whitelist entry not found: " + id));

        if (entry.getUser().getRole() == RoleType.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super admins are not restricted by IP whitelist");
        }

        ipWhitelistRepository.delete(entry);
        log.info("IP whitelist entry removed: user={} ip={}", entry.getUser().getEmail(), entry.getIpAddress());
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
