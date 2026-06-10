package com.ebee.audit.service;

import com.ebee.audit.entity.AuditLog;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.repository.AuditLogRepository;
import com.ebee.common.security.SecurityService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    @Transactional
    public AuditLog record(AuditAction action, String entityType, UUID entityId, String summary) {
        UUID performedBy;
        String performedByEmail;
        String performedByRole;
        try {
            performedBy = securityService.getCurrentUserId();
            performedByEmail = securityService.getCurrentUserEmail();
            performedByRole = securityService.getCurrentUserRole();
        } catch (Exception e) {
            performedBy = null;
            performedByEmail = "system";
            performedByRole = "SYSTEM";
            log.warn("Could not resolve current user for audit log: {}", e.getMessage());
        }

        AuditLog log = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .summary(summary)
                .performedBy(performedBy)
                .performedByEmail(performedByEmail)
                .performedByRole(performedByRole)
                .build();
        return auditLogRepository.save(log);
    }

    public Page<AuditLog> getLogs(String entityType, UUID entityId, AuditAction action,
                                   UUID performedBy, LocalDateTime startDate, LocalDateTime endDate,
                                   int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (performedBy != null) {
                predicates.add(cb.equal(root.get("performedBy"), performedBy));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return auditLogRepository.findAll(spec, pageable);
    }

    public AuditLog getLog(UUID id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found: " + id));
    }
}
