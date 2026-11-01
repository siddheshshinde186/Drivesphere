package com.drivesphere.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe rate limiter for authentication, email actions, and AI requests.
 * Protects login, password reset, email verification, and AI endpoints.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes

    private static class AttemptRecord {
        int attempts;
        long lastAttemptTime;
        long lockedUntil;

        AttemptRecord(int attempts, long lastAttemptTime) {
            this.attempts = attempts;
            this.lastAttemptTime = lastAttemptTime;
            this.lockedUntil = 0;
        }
    }

    private static class WindowRecord {
        int count;
        long windowStart;

        WindowRecord(int count, long windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }

    private final Map<String, AttemptRecord> attemptsMap = new ConcurrentHashMap<>();
    private final Map<String, WindowRecord> windowMap = new ConcurrentHashMap<>();

    // Login Rate Limiting
    public boolean isBlocked(String key) {
        AttemptRecord record = attemptsMap.get(key.toLowerCase());
        if (record == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (record.lockedUntil > now) {
            return true;
        }
        if (record.lockedUntil > 0 && record.lockedUntil <= now) {
            attemptsMap.remove(key.toLowerCase());
            return false;
        }
        if (now - record.lastAttemptTime > LOGIN_LOCKOUT_DURATION_MS) {
            attemptsMap.remove(key.toLowerCase());
            return false;
        }
        return false;
    }

    public void loginFailed(String key) {
        String normalizedKey = key.toLowerCase();
        long now = System.currentTimeMillis();
        attemptsMap.compute(normalizedKey, (k, record) -> {
            if (record == null || now - record.lastAttemptTime > LOGIN_LOCKOUT_DURATION_MS) {
                return new AttemptRecord(1, now);
            }
            record.attempts++;
            record.lastAttemptTime = now;
            if (record.attempts >= MAX_LOGIN_ATTEMPTS) {
                record.lockedUntil = now + LOGIN_LOCKOUT_DURATION_MS;
                log.warn("Account/IP locked due to {} consecutive failed login attempts: {}", record.attempts, normalizedKey);
            }
            return record;
        });
    }

    public void loginSucceeded(String key) {
        attemptsMap.remove(key.toLowerCase());
    }

    public long getRemainingBlockTimeSeconds(String key) {
        AttemptRecord record = attemptsMap.get(key.toLowerCase());
        if (record != null && record.lockedUntil > System.currentTimeMillis()) {
            return (record.lockedUntil - System.currentTimeMillis()) / 1000;
        }
        return 0;
    }

    // General Action Rate Limiting (Sliding Window per action/key)
    public boolean isActionRateLimited(String actionPrefix, String key, int maxRequests, long windowMs) {
        String compositeKey = actionPrefix + ":" + key.toLowerCase().trim();
        long now = System.currentTimeMillis();

        WindowRecord record = windowMap.compute(compositeKey, (k, existing) -> {
            if (existing == null || (now - existing.windowStart) > windowMs) {
                return new WindowRecord(1, now);
            }
            existing.count++;
            return existing;
        });

        if (record.count > maxRequests) {
            log.warn("Rate limit exceeded for action [{}] on key [{}] ({}/{} in {}ms)",
                    actionPrefix, key, record.count, maxRequests, windowMs);
            return true;
        }
        return false;
    }
}
