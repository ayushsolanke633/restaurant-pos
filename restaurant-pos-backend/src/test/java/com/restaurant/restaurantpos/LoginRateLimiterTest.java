package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class LoginRateLimiterTest {

    private static class TestClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        public TestClock(Instant initialInstant) {
            this.instant = initialInstant;
            this.zone = ZoneOffset.UTC;
        }

        public void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private TestClock testClock;
    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        testClock = new TestClock(Instant.parse("2026-10-22T12:00:00Z"));
        // 5 attempts per 60-second window, max 100 entries in cache
        rateLimiter = new LoginRateLimiter(5, 60, 100, testClock);
    }

    @Test
    @DisplayName("1. Requests under the limit are permitted")
    void testRequestsUnderLimitPermitted() {
        for (int i = 1; i <= 5; i++) {
            LoginRateLimiter.RateLimitResult result = rateLimiter.checkAndConsume("192.168.1.10", "user@test.com");
            assertTrue(result.isPermitted(), "Attempt " + i + " should be permitted");
            assertEquals(0, result.getRetryAfterSeconds());
            assertNull(result.getReason());
        }
    }

    @Test
    @DisplayName("2. The request beyond the configured limit returns 429 rejection with Retry-After")
    void testRequestBeyondLimitRejected() {
        // First 5 attempts permitted
        for (int i = 1; i <= 5; i++) {
            assertTrue(rateLimiter.checkAndConsume("192.168.1.10", "user@test.com").isPermitted());
        }

        // 6th attempt rejected
        LoginRateLimiter.RateLimitResult result = rateLimiter.checkAndConsume("192.168.1.10", "user@test.com");
        assertFalse(result.isPermitted());
        assertTrue(result.getRetryAfterSeconds() > 0, "Retry-After must be positive");
        assertTrue(result.getRetryAfterSeconds() <= 60, "Retry-After should not exceed window");
        assertNotNull(result.getReason());
    }

    @Test
    @DisplayName("3. IP-based limiting works across different account identifiers")
    void testIpBasedLimitingAcrossDifferentAccounts() {
        // An attacker tries 5 different accounts from the same IP
        for (int i = 1; i <= 5; i++) {
            LoginRateLimiter.RateLimitResult result = rateLimiter.checkAndConsume("192.168.1.10", "user" + i + "@test.com");
            assertTrue(result.isPermitted(), "Attempt " + i + " should be permitted");
        }

        // 6th attempt with a new account from the same IP is blocked
        LoginRateLimiter.RateLimitResult blocked = rateLimiter.checkAndConsume("192.168.1.10", "brand_new_user@test.com");
        assertFalse(blocked.isPermitted(), "6th attempt from same IP must be blocked regardless of account");
        assertEquals("IP", blocked.getReason());
    }

    @Test
    @DisplayName("4. Account-based limiting works across different IPs")
    void testAccountBasedLimitingAcrossDifferentIps() {
        // An attacker attacks the same victim account from 5 different IPs
        for (int i = 1; i <= 5; i++) {
            LoginRateLimiter.RateLimitResult result = rateLimiter.checkAndConsume("10.0.0." + i, "victim@test.com");
            assertTrue(result.isPermitted(), "Attempt from IP 10.0.0." + i + " should be permitted");
        }

        // 6th attempt for the victim account from a 6th IP is blocked
        LoginRateLimiter.RateLimitResult blocked = rateLimiter.checkAndConsume("10.0.0.99", "victim@test.com");
        assertFalse(blocked.isPermitted(), "Attempt on targeted account must be blocked regardless of IP");
        assertEquals("ACCOUNT", blocked.getReason());

        // Meanwhile, an unrelated account from that 6th IP is still permitted
        LoginRateLimiter.RateLimitResult otherAccount = rateLimiter.checkAndConsume("10.0.0.99", "unrelated@test.com");
        assertTrue(otherAccount.isPermitted(), "Unrelated account from IP 10.0.0.99 must be permitted");
    }

    @Test
    @DisplayName("5. The time window resets or expires as designed without sleep")
    void testTimeWindowResetsDeterministically() {
        // Exhaust limit at T=0
        for (int i = 1; i <= 5; i++) {
            assertTrue(rateLimiter.checkAndConsume("192.168.1.20", "reset_test@test.com").isPermitted());
        }

        // Verify blocked at T=0
        assertFalse(rateLimiter.checkAndConsume("192.168.1.20", "reset_test@test.com").isPermitted());

        // Advance controllable clock by 30 seconds (still within window)
        testClock.advance(Duration.ofSeconds(30));
        assertFalse(rateLimiter.checkAndConsume("192.168.1.20", "reset_test@test.com").isPermitted(),
                "Must still be blocked at 30 seconds");

        // Advance clock by another 31 seconds (total 61 seconds > 60-second window)
        testClock.advance(Duration.ofSeconds(31));

        // Now attempts must be permitted again
        LoginRateLimiter.RateLimitResult permittedAgain = rateLimiter.checkAndConsume("192.168.1.20", "reset_test@test.com");
        assertTrue(permittedAgain.isPermitted(), "Should be permitted after window expiration");
    }

    @Test
    @DisplayName("6. Invalid or missing account identifiers cannot bypass IP limiting")
    void testMissingAccountCannotBypassIpLimit() {
        // 5 requests with null / empty / blank account identifiers
        assertTrue(rateLimiter.checkAndConsume("192.168.1.30", null).isPermitted());
        assertTrue(rateLimiter.checkAndConsume("192.168.1.30", "").isPermitted());
        assertTrue(rateLimiter.checkAndConsume("192.168.1.30", "   ").isPermitted());
        assertTrue(rateLimiter.checkAndConsume("192.168.1.30", null).isPermitted());
        assertTrue(rateLimiter.checkAndConsume("192.168.1.30", "valid@test.com").isPermitted());

        // 6th attempt must be blocked by IP
        LoginRateLimiter.RateLimitResult blocked = rateLimiter.checkAndConsume("192.168.1.30", "another@test.com");
        assertFalse(blocked.isPermitted(), "Missing/blank accounts cannot bypass IP limit");
        assertEquals("IP", blocked.getReason());
    }

    @Test
    @DisplayName("7. Arbitrary identifiers cannot grow limiter state without bounds")
    void testBoundedStatePreventsMemoryExhaustion() {
        // Limiter configured with maxEntries = 20
        LoginRateLimiter boundedLimiter = new LoginRateLimiter(5, 60, 20, testClock);

        // Attacker generates 200 distinct email keys from distinct IPs
        for (int i = 1; i <= 200; i++) {
            boundedLimiter.checkAndConsume("172.16.0." + (i % 250), "random_user_" + i + "@attacker.com");
        }

        // Cache size must NEVER exceed maxEntries (20)
        assertTrue(boundedLimiter.getCacheSize() <= 20,
                "Cache size (" + boundedLimiter.getCacheSize() + ") must be <= maxEntries (20)");
    }

    @Test
    @DisplayName("10. Concurrent requests cannot exceed the configured limit due to race conditions")
    void testConcurrencySafetyPreventsLimitBreach() throws Exception {
        int threads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger permittedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // ensure all threads fire at the exact same instant
                    LoginRateLimiter.RateLimitResult res = rateLimiter.checkAndConsume("192.168.1.50", "concurrent@test.com");
                    if (res.isPermitted()) {
                        permittedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // unblock all threads simultaneously

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly 5 attempts must have succeeded; the other 25 must have been rejected
        assertEquals(5, permittedCount.get(), "Exactly maxAttempts (5) must be permitted under high concurrency");
        assertEquals(threads - 5, rejectedCount.get(), "Remaining attempts must be rejected with 429");
    }

    @Test
    @DisplayName("11. SEC-01: checkIpLimit is strictly non-consuming and does not double-count")
    void testCheckIpLimitDoesNotConsumeAttempt() {
        String testIp = "192.168.1.99";

        // Call checkIpLimit 10 times
        for (int i = 0; i < 10; i++) {
            LoginRateLimiter.RateLimitResult precheck = rateLimiter.checkIpLimit(testIp);
            assertTrue(precheck.isPermitted(), "Precheck should permit unblocked IP");
        }

        // Verify the IP still has all 5 attempts available in checkAndConsume
        for (int i = 1; i <= 5; i++) {
            LoginRateLimiter.RateLimitResult res = rateLimiter.checkAndConsume(testIp, "precheck_user@test.com");
            assertTrue(res.isPermitted(), "Attempt " + i + " must succeed because precheck was non-consuming");
        }

        // 6th attempt is blocked
        LoginRateLimiter.RateLimitResult blocked = rateLimiter.checkAndConsume(testIp, "precheck_user@test.com");
        assertFalse(blocked.isPermitted());

        // Now precheck also returns blocked
        LoginRateLimiter.RateLimitResult precheckBlocked = rateLimiter.checkIpLimit(testIp);
        assertFalse(precheckBlocked.isPermitted(), "Precheck must return blocked once IP limit is exhausted");
        assertEquals("IP", precheckBlocked.getReason());
        assertTrue(precheckBlocked.getRetryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("12. SEC-03: Active account window cannot be evicted or reset by cache churn")
    void testTargetAccountCannotBeEvictedByCacheChurn() {
        // Limiter configured with maxEntries = 10
        LoginRateLimiter smallLimiter = new LoginRateLimiter(5, 60, 10, testClock);

        // Exhaust limit for target account
        String targetAccount = "target_admin@restaurant.com";
        for (int i = 1; i <= 5; i++) {
            assertTrue(smallLimiter.checkAndConsume("10.0.0." + i, targetAccount).isPermitted());
        }

        // Verify targeted account is blocked
        LoginRateLimiter.RateLimitResult blockedBefore = smallLimiter.checkAndConsume("10.0.0.99", targetAccount);
        assertFalse(blockedBefore.isPermitted(), "Target account must be blocked");

        // Attacker creates heavy cache churn with 50 distinct accounts/IPs
        for (int i = 1; i <= 50; i++) {
            smallLimiter.checkAndConsume("172.16.1." + (i % 250), "churn_user_" + i + "@attacker.com");
        }

        // Target account MUST STILL BE BLOCKED; active window must NOT have been evicted
        LoginRateLimiter.RateLimitResult blockedAfter = smallLimiter.checkAndConsume("10.0.0.99", targetAccount);
        assertFalse(blockedAfter.isPermitted(),
                "Target account must remain blocked despite cache churn; unexpired window must not be evicted");
        assertEquals("ACCOUNT", blockedAfter.getReason());
    }

    @Test
    @DisplayName("13. SEC-06: Startup configuration validation rejects zero, negative, and out-of-bounds values")
    void testStartupConfigurationValidation() {
        // maxAttempts boundary and invalid checks
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(0, 60, 100, testClock),
                "maxAttempts=0 must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(-5, 60, 100, testClock),
                "maxAttempts=-5 must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(200_000, 60, 100, testClock),
                "maxAttempts > 100000 must throw IllegalArgumentException");

        // windowSeconds boundary and invalid checks
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(5, 0, 100, testClock),
                "windowSeconds=0 must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(5, -10, 100, testClock),
                "windowSeconds=-10 must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(5, 100_000, 100, testClock),
                "windowSeconds > 86400 must throw IllegalArgumentException");

        // maxEntries boundary and invalid checks
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(5, 60, 0, testClock),
                "maxEntries=0 must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(5, 60, -1, testClock),
                "maxEntries=-1 must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(5, 60, 2_000_000, testClock),
                "maxEntries > 1000000 must throw IllegalArgumentException");
    }

    @Test
    @DisplayName("14. SEC-07: Monotonic clock handles clock regression without breaking rate limiting")
    void testMonotonicClockHandlesClockRegressionGracefully() {
        // Monotonic test clock using an AtomicLong of nanoseconds
        java.util.concurrent.atomic.AtomicLong nanoTime = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
        LoginRateLimiter.MonotonicClock monoClock = nanoTime::get;

        LoginRateLimiter monoLimiter = new LoginRateLimiter(3, 60, 100, monoClock);

        // Exhaust 3 attempts at T=1s
        assertTrue(monoLimiter.checkAndConsume("192.168.1.10", "mono@test.com").isPermitted());
        assertTrue(monoLimiter.checkAndConsume("192.168.1.10", "mono@test.com").isPermitted());
        assertTrue(monoLimiter.checkAndConsume("192.168.1.10", "mono@test.com").isPermitted());

        // 4th is blocked
        assertFalse(monoLimiter.checkAndConsume("192.168.1.10", "mono@test.com").isPermitted());

        // Monotonic clock advances by 30 seconds
        nanoTime.addAndGet(30_000_000_000L);
        assertFalse(monoLimiter.checkAndConsume("192.168.1.10", "mono@test.com").isPermitted());

        // Monotonic clock advances by another 31 seconds (total 61s > 60s window)
        nanoTime.addAndGet(31_000_000_000L);
        assertTrue(monoLimiter.checkAndConsume("192.168.1.10", "mono@test.com").isPermitted(),
                "Must be permitted after 60s has elapsed monotonically");
    }
}
