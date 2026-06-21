package com.ebee.auth.security;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimitingService {

    @Value("${rate-limiting.login.max-attempts:5}")
    private int maxLoginAttempts;

    @Value("${rate-limiting.login.window-minutes:15}")
    private int loginWindowMinutes;

    @Value("${rate-limiting.generic.max-requests:20}")
    private int maxRequests;

    @Value("${rate-limiting.generic.window-minutes:15}")
    private int genericWindowMinutes;

    @Value("${rate-limiting.ip.max-requests:100}")
    private int maxIpRequests;

    @Value("${rate-limiting.ip.window-minutes:1}")
    private int ipWindowMinutes;

    private final Map<String, SlidingWindow> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindow> genericRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindow> ipRateLimiters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    void startCleaner() {
        cleaner.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        cleaner.shutdown();
    }

    public boolean isLoginAllowed(String key) {
        SlidingWindow window = loginAttempts.computeIfAbsent(key,
                k -> new SlidingWindow(maxLoginAttempts, loginWindowMinutes));
        return window.tryAcquire();
    }

    public boolean isAllowed(String key) {
        SlidingWindow window = genericRateLimiters.computeIfAbsent(key,
                k -> new SlidingWindow(maxRequests, genericWindowMinutes));
        return window.tryAcquire();
    }

    public boolean isIpAllowed(String ip) {
        SlidingWindow window = ipRateLimiters.computeIfAbsent(ip,
                k -> new SlidingWindow(maxIpRequests, ipWindowMinutes));
        return window.tryAcquire();
    }

    public void resetLoginAttempts(String key) {
        loginAttempts.remove(key);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        loginAttempts.values().removeIf(w -> now - w.getWindowStart() > w.getWindowDurationMs());
        genericRateLimiters.values().removeIf(w -> now - w.getWindowStart() > w.getWindowDurationMs());
        ipRateLimiters.values().removeIf(w -> now - w.getWindowStart() > w.getWindowDurationMs());
    }

    private static class SlidingWindow {
        private final int maxRequests;
        private final long windowDurationMs;
        private final java.util.Deque<Long> timestamps = new java.util.concurrent.ConcurrentLinkedDeque<>();

        SlidingWindow(int maxRequests, long windowMinutes) {
            this.maxRequests = maxRequests;
            this.windowDurationMs = windowMinutes * 60 * 1000;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long cutoff = now - windowDurationMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }

        long getWindowStart() {
            Long first = timestamps.peekFirst();
            return first != null ? first : System.currentTimeMillis();
        }

        long getWindowDurationMs() {
            return windowDurationMs;
        }
    }
}
