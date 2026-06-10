package com.ebee.audit.aspect;

import com.ebee.audit.entity.AuditLog;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RepositoryAuditAspect {

    private final AuditLogService auditLogService;

    private final ThreadLocal<Boolean> auditing = ThreadLocal.withInitial(() -> false);

    @Around("execution(* org.springframework.data.repository.CrudRepository+.save(..)) && args(entity)")
    public Object aroundSave(ProceedingJoinPoint jp, Object entity) throws Throwable {
        if (auditing.get() || entity instanceof AuditLog) {
            return jp.proceed();
        }
        UUID entityId = extractId(entity);
        boolean isNew = entityId == null;
        Object result = jp.proceed();
        entityId = extractId(result);
        if (entityId != null) {
            String entityType = result.getClass().getSimpleName();
            String summary = (isNew ? "Created " : "Updated ") + entityType + " " + entityId;
            auditing.set(true);
            try {
                auditLogService.record(isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                        entityType, entityId, summary);
            } catch (Exception e) {
                log.warn("Failed to record audit log for {} {}: {}", entityType, entityId, e.getMessage());
            } finally {
                auditing.set(false);
            }
        }
        return result;
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository+.delete(..)) && args(entity)")
    public Object aroundDelete(ProceedingJoinPoint jp, Object entity) throws Throwable {
        if (auditing.get() || entity instanceof AuditLog) {
            return jp.proceed();
        }
        UUID entityId = extractId(entity);
        if (entityId != null) {
            String entityType = entity.getClass().getSimpleName();
            auditing.set(true);
            try {
                auditLogService.record(AuditAction.DELETE, entityType, entityId,
                        "Deleted " + entityType + " " + entityId);
            } catch (Exception e) {
                log.warn("Failed to record audit log for {} {}: {}", entityType, entityId, e.getMessage());
            } finally {
                auditing.set(false);
            }
        }
        return jp.proceed();
    }

    private UUID extractId(Object entity) {
        try {
            var method = entity.getClass().getMethod("getId");
            Object id = method.invoke(entity);
            return id instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            return null;
        }
    }
}
