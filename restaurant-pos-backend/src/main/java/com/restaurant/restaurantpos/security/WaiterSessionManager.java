package com.restaurant.restaurantpos.security;

import com.restaurant.restaurantpos.exception.DuplicateSessionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class WaiterSessionManager {

    public static class ActiveSession {
        private final String token;
        private final long loginTime;
        private volatile long lastActivityTime;

        public ActiveSession(String token, long loginTime) {
            this.token = token;
            this.loginTime = loginTime;
            this.lastActivityTime = loginTime;
        }

        public String getToken() {
            return token;
        }

        public long getLoginTime() {
            return loginTime;
        }

        public long getLastActivityTime() {
            return lastActivityTime;
        }

        public void touch(long now) {
            this.lastActivityTime = now;
        }

        public boolean isExpired(long now, long idleTimeoutMs) {
            return (now - lastActivityTime) > idleTimeoutMs;
        }

        public boolean matchesToken(String tokenToCheck) {
            return this.token != null && this.token.equals(tokenToCheck);
        }
    }

    private final ConcurrentHashMap<Long, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    @Value("${waiter.session.idle-timeout-ms:1800000}")
    private long idleTimeoutMs = 1800000L; // 30 minutes default

    public void registerSession(Long userId, String token) {
        long now = System.currentTimeMillis();
        activeSessions.compute(userId, (id, existing) -> {
            if (existing != null && !existing.isExpired(now, idleTimeoutMs)) {
                throw new DuplicateSessionException(
                        "This waiter account is already logged in on another device. Please log out from the other device first."
                );
            }
            return new ActiveSession(token, now);
        });
    }

    public boolean isSessionActive(Long userId, String token) {
        if (userId == null || token == null) {
            return false;
        }
        ActiveSession session = activeSessions.get(userId);
        if (session == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (session.isExpired(now, idleTimeoutMs)) {
            activeSessions.remove(userId, session);
            return false;
        }
        if (session.matchesToken(token)) {
            session.touch(now);
            return true;
        }
        return false;
    }

    public void releaseSession(Long userId, String token) {
        if (userId == null || token == null) {
            return;
        }
        activeSessions.computeIfPresent(userId, (id, existing) -> {
            if (existing.matchesToken(token)) {
                return null;
            }
            return existing;
        });
    }

    public void clearSession(Long userId) {
        if (userId != null) {
            activeSessions.remove(userId);
        }
    }

    public void clearAllSessions() {
        activeSessions.clear();
    }

    public boolean hasActiveSession(Long userId) {
        if (userId == null) {
            return false;
        }
        ActiveSession session = activeSessions.get(userId);
        if (session == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (session.isExpired(now, idleTimeoutMs)) {
            activeSessions.remove(userId, session);
            return false;
        }
        return true;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public void setIdleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
    }
}
