package com.ebee.common.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.notification.NotificationPort;
import com.ebee.user.repository.UserRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ebee.user.enums.RoleType.ADMIN;
import static com.ebee.user.enums.RoleType.SUPER_ADMIN;

/**
 * Makes scheduled-job failures visible. Every {@code @Scheduled} method runs through here: when it
 * throws, the failure is logged and admins get an in-app {@code SCHEDULED_JOB_FAILED} alert.
 * Jobs that keep going past a bad item call {@link #reportPartialFailure} instead.
 * Alerts are throttled to one per job per hour, so a job failing every minute doesn't flood admins.
 * Runs outermost (before the transaction and ShedLock proxies), so it sees rollback failures too.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScheduledJobMonitor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobMonitor.class);
    static final Duration ALERT_EVERY = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final NotificationPort notificationPort;
    private final Clock clock;
    private final Map<String, Instant> lastAlert = new ConcurrentHashMap<>();

    @Autowired
    public ScheduledJobMonitor(UserRepository userRepository, NotificationPort notificationPort) {
        this(userRepository, notificationPort, Clock.systemUTC());
    }

    ScheduledJobMonitor(UserRepository userRepository, NotificationPort notificationPort, Clock clock) {
        this.userRepository = userRepository;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object monitor(ProceedingJoinPoint pjp) throws Throwable {
        String job = pjp.getSignature().getDeclaringType().getSimpleName() + "." + pjp.getSignature().getName();
        try {
            return pjp.proceed();
        } catch (Exception e) {
            log.error("Scheduled job {} failed", job, e);
            alertAdmins(job, "The scheduled job " + job + " failed and did nothing this run: " + describe(e)
                    + ". It will run again at its next scheduled time; check the server logs.");
            return null; // already logged and alerted; the scheduler has nothing more to do with it
        }
    }

    /** For jobs that skip a bad item and carry on: the run finished, but these items didn't. */
    public void reportPartialFailure(String job, int failed, int total, String firstError) {
        if (failed <= 0) {
            return;
        }
        log.error("Scheduled job {}: {} of {} items failed (first error: {})", job, failed, total, firstError);
        alertAdmins(job, "The scheduled job " + job + " could not process " + failed + " of " + total
                + " items. First error: " + firstError + ". They will be retried on the next run; check the server logs.");
    }

    private void alertAdmins(String job, String message) {
        Instant now = clock.instant();
        Instant last = lastAlert.get(job);
        if (last != null && now.isBefore(last.plus(ALERT_EVERY))) {
            return;
        }
        lastAlert.put(job, now);
        try {
            for (var admin : userRepository.findByRoleIn(List.of(ADMIN, SUPER_ADMIN))) {
                notificationPort.createInternalAlert(admin, "Scheduled job failed", message,
                        AlertType.SCHEDULED_JOB_FAILED, null, "system");
            }
        } catch (RuntimeException alertFailure) {
            // The job failure itself is already in the log; don't let alerting hide it.
            log.error("Could not alert admins about failed job {}", job, alertFailure);
        }
    }

    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return root.getClass().getSimpleName() + (msg == null ? "" : ": " + (msg.length() > 300 ? msg.substring(0, 300) + "…" : msg));
    }
}
