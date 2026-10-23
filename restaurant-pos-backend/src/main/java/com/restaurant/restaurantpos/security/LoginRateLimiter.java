package com.restaurant.restaurantpos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;

/**
 * Thread-safe, bounded, in-memory sliding window rate limiter for login requests.
 * Enforces dual-key limits by client IP and normalized account identifier (SHA-256 hashed).
 *
 * Operational Limitation:
 * In-memory state resets upon application restart and is not shared across multi-instance clusters.
 * For horizontally scaled deployments, a distributed store (e.g. Redis) is required.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final int maxAttempts;
    private final long windowSeconds;
    private final int maxEntries;
    private final Map<String, AttemptWindow> cache;

    public static class RateLimitResult {
        private final boolean permitted;
        private final long retryAfterSeconds;
        private final String reason; // "IP" or "ACCOUNT" or null

        private RateLimitResult(boolean permitted, long retryAfterSeconds, String reason) {
            this.permitted = permitted;
            this.retryAfterSeconds = retryAfterSeconds;
            this.reason = reason;
        }

        public static RateLimitResult permitted() {
            return new RateLimitResult(true, 0, null);
        }

        public static RateLimitResult rejected(long retryAfterSeconds, String reason) {
            return new RateLimitResult(false, retryAfterSeconds, reason);
        }

        public boolean isPermitted() {
            return permitted;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        public String getReason() {
            return reason;
        }
    }

    @FunctionalInterface
    public interface MonotonicClock {
        long nanoTime();
    }

    private static class AttemptWindow {
        private final Deque<Long> attemptTimestamps = new ArrayDeque<>();
    }

    private final MonotonicClock monotonicClock;

    @Autowired
    public LoginRateLimiter(
            @Value("${app.security.rate-limit.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.rate-limit.login.window-seconds:60}") long windowSeconds,
            @Value("${app.security.rate-limit.login.max-entries:10000}") int maxEntries) {
        this(maxAttempts, windowSeconds, maxEntries, System::nanoTime);
    }

    public LoginRateLimiter(int maxAttempts, long windowSeconds, int maxEntries, Clock clock) {
        this(maxAttempts, windowSeconds, maxEntries,
                clock != null ? () -> clock.instant().toEpochMilli() * 1_000_000L : System::nanoTime);
    }

    public LoginRateLimiter(int maxAttempts, long windowSeconds, int maxEntries, MonotonicClock monotonicClock) {
        if (maxAttempts < 1 || maxAttempts > 100_000) {
            throw new IllegalArgumentException("app.security.rate-limit.login.max-attempts must be between 1 and 100,000, got: " + maxAttempts);
        }
        if (windowSeconds < 1 || windowSeconds > 86_400) {
            throw new IllegalArgumentException("app.security.rate-limit.login.window-seconds must be between 1 and 86,400 (max 24h), got: " + windowSeconds);
        }
        if (maxEntries < 1 || maxEntries > 1_000_000) {
            throw new IllegalArgumentException("app.security.rate-limit.login.max-entries must be between 1 and 1,000,000, got: " + maxEntries);
        }

        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.maxEntries = maxEntries;
        this.monotonicClock = monotonicClock != null ? monotonicClock : System::nanoTime;
        this.cache = new HashMap<>();
    }

    /**
     * Non-consuming check to determine if the client IP is already over its rate limit.
     * Does not read or buffer request bodies, and does not record an attempt.
     */
    public RateLimitResult checkIpLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
        String ipKey = "ip:" + clientIp.trim();
        long nowNanos = monotonicClock.nanoTime();
        long windowNanos = windowSeconds * 1_000_000_000L;

        synchronized (cache) {
            AttemptWindow ipWindow = cache.get(ipKey);
            if (ipWindow != null) {
                pruneExpired(ipWindow, nowNanos, windowNanos);
                if (ipWindow.attemptTimestamps.size() >= maxAttempts) {
                    long earliest = ipWindow.attemptTimestamps.peekFirst();
                    long waitNanos = (earliest + windowNanos) - nowNanos;
                    long retryAfter = Math.max(1L, (waitNanos + 999_999_999L) / 1_000_000_000L);
                    return RateLimitResult.rejected(retryAfter, "IP");
                }
            }
            return RateLimitResult.permitted();
        }
    }

    /**
     * Checks rate limits for client IP and account, recording the attempt if permitted.
     * Guarantees atomic dual-key enforcement and prevents active-window eviction bypass.
     *
     * @param clientIp raw or normalized client IP address
     * @param rawEmail user-submitted email address (normalized & hashed internally)
     * @return RateLimitResult indicating permission or rejection with Retry-After duration
     */
    public RateLimitResult checkAndConsume(String clientIp, String rawEmail) {
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
        String ipKey = "ip:" + clientIp.trim();
        String accountKey = hashAccountKey(rawEmail);

        long nowNanos = monotonicClock.nanoTime();
        long windowNanos = windowSeconds * 1_000_000_000L;

        synchronized (cache) {
            // 1. Check IP limit
            AttemptWindow ipWindow = cache.get(ipKey);
            if (ipWindow != null) {
                pruneExpired(ipWindow, nowNanos, windowNanos);
                if (ipWindow.attemptTimestamps.size() >= maxAttempts) {
                    long earliest = ipWindow.attemptTimestamps.peekFirst();
                    long waitNanos = (earliest + windowNanos) - nowNanos;
                    long retryAfter = Math.max(1L, (waitNanos + 999_999_999L) / 1_000_000_000L);
                    return RateLimitResult.rejected(retryAfter, "IP");
                }
            }

            // 2. Check Account limit (if account is present)
            if (accountKey != null) {
                AttemptWindow accountWindow = cache.get(accountKey);
                if (accountWindow != null) {
                    pruneExpired(accountWindow, nowNanos, windowNanos);
                    if (accountWindow.attemptTimestamps.size() >= maxAttempts) {
                        long earliest = accountWindow.attemptTimestamps.peekFirst();
                        long waitNanos = (earliest + windowNanos) - nowNanos;
                        long retryAfter = Math.max(1L, (waitNanos + 999_999_999L) / 1_000_000_000L);
                        return RateLimitResult.rejected(retryAfter, "ACCOUNT");
                    }
                }
            }

            // 3. SEC-03: Capacity management - never evict active windows
            int newKeysCount = (cache.containsKey(ipKey) ? 0 : 1)
                    + (accountKey != null && !cache.containsKey(accountKey) ? 1 : 0);

            if (cache.size() + newKeysCount > maxEntries) {
                purgeExpiredEntries(nowNanos, windowNanos);

                if (cache.size() + newKeysCount > maxEntries) {
                    log.warn("Login rate limiter cache capacity exhausted (maxEntries={}). Failing closed for new keys without evicting active limits.", maxEntries);
                    return RateLimitResult.rejected(windowSeconds, "CAPACITY");
                }
            }

            // 4. Record attempt for IP
            AttemptWindow ipWin = cache.computeIfAbsent(ipKey, k -> new AttemptWindow());
            ipWin.attemptTimestamps.addLast(nowNanos);

            // Record attempt for Account
            if (accountKey != null) {
                AttemptWindow accWin = cache.computeIfAbsent(accountKey, k -> new AttemptWindow());
                accWin.attemptTimestamps.addLast(nowNanos);
            }

            return RateLimitResult.permitted();
        }
    }

    private void purgeExpiredEntries(long nowNanos, long windowNanos) {
        cache.entrySet().removeIf(entry -> {
            pruneExpired(entry.getValue(), nowNanos, windowNanos);
            return entry.getValue().attemptTimestamps.isEmpty();
        });
    }

    private void pruneExpired(AttemptWindow window, long nowNanos, long windowNanos) {
        while (!window.attemptTimestamps.isEmpty() && (nowNanos - window.attemptTimestamps.peekFirst() >= windowNanos)) {
            window.attemptTimestamps.pollFirst();
        }
    }

    private String hashAccountKey(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder("account:");
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback (should never occur for SHA-256)
            return "account:" + normalized;
        }
    }

    public int getCacheSize() {
        synchronized (cache) {
            return cache.size();
        }
    }

    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public int getMaxEntries() {
        return maxEntries;
    }
}
