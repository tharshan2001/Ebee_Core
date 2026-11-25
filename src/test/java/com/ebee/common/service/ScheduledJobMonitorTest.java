package com.ebee.common.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.notification.NotificationPort;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledJobMonitorTest {

    private final UserRepository users = mock(UserRepository.class);
    private final NotificationPort notifications = mock(NotificationPort.class);
    private final User admin = new User();
    private Instant now = Instant.parse("2026-11-25T02:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };
    private final ScheduledJobMonitor monitor = new ScheduledJobMonitor(users, notifications, clock);

    static class CommissionJob {}

    @BeforeEach
    void setUp() {
        when(users.findByRoleIn(anyList())).thenReturn(List.of(admin));
    }

    private ProceedingJoinPoint failingJob() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.getDeclaringType()).thenReturn(CommissionJob.class);
        when(sig.getName()).thenReturn("run");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenThrow(new IllegalStateException("wrap", new RuntimeException("deadlock detected")));
        return pjp;
    }

    @Test
    void failedJob_alertsAdminsWithTheRootCause_andDoesNotRethrow() throws Throwable {
        assertThat(monitor.monitor(failingJob())).isNull();

        verify(notifications).createInternalAlert(eq(admin), eq("Scheduled job failed"),
                contains("CommissionJob.run failed and did nothing this run: RuntimeException: deadlock detected"),
                eq(AlertType.SCHEDULED_JOB_FAILED), isNull(), eq("system"));
    }

    @Test
    void repeatedFailures_alertOncePerHourPerJob() throws Throwable {
        ProceedingJoinPoint job = failingJob();
        monitor.monitor(job);
        now = now.plus(Duration.ofMinutes(59));
        monitor.monitor(job);
        verify(notifications, times(1)).createInternalAlert(any(), any(), any(), any(), any(), any());

        now = now.plus(Duration.ofMinutes(2));
        monitor.monitor(job);
        verify(notifications, times(2)).createInternalAlert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void successfulJob_returnsItsResult_noAlert() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.getDeclaringType()).thenReturn(CommissionJob.class);
        when(sig.getName()).thenReturn("run");
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenReturn("ok");

        assertThat(monitor.monitor(pjp)).isEqualTo("ok");
        verify(notifications, never()).createInternalAlert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void partialFailure_alertsWithCounts_zeroFailuresIsSilent() {
        monitor.reportPartialFailure("Reminders.exactAlerts", 0, 10, null);
        verify(notifications, never()).createInternalAlert(any(), any(), any(), any(), any(), any());

        monitor.reportPartialFailure("Reminders.exactAlerts", 2, 10, "reminder 42: boom");
        verify(notifications).createInternalAlert(eq(admin), any(), contains("could not process 2 of 10 items. First error: reminder 42: boom"),
                eq(AlertType.SCHEDULED_JOB_FAILED), isNull(), eq("system"));
    }

    @Test
    void alertingItselfFailing_isContained() throws Throwable {
        when(users.findByRoleIn(anyList())).thenThrow(new RuntimeException("db down"));
        assertThat(monitor.monitor(failingJob())).isNull();
    }
}
