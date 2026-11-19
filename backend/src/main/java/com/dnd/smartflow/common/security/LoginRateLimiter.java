package com.dnd.smartflow.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Sliding-window limiter protecting authentication endpoints against brute force (per client IP). */
@Component
public class LoginRateLimiter {
    /** Generous per-IP budget: event visitors often share one public IP; per-account lockout still applies. */
    @Value("${smartflow.security.login-rate-limit:120}")
    private int maxAttempts = 120;
    private static final long WINDOW_SECONDS = 300;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        Deque<Instant> q = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        while (!q.isEmpty() && q.peekFirst().isBefore(now.minusSeconds(WINDOW_SECONDS))) q.pollFirst();
        if (q.size() >= maxAttempts) return false;
        q.addLast(now);
        if (attempts.size() > 10_000) attempts.clear();
        return true;
    }
}
