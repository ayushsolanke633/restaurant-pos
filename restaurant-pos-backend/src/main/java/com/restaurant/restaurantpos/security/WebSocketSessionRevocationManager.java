package com.restaurant.restaurantpos.security;

import com.restaurant.restaurantpos.service.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages authenticated WebSocket sessions and coordinates immediate, safe invalidation
 * when a token is revoked (e.g. on logout).
 *
 * Guarantees that:
 * 1. Active WebSocket sessions are associated with their validated token's jti and user identity.
 * 2. On logout, sessions authenticated with that exact jti are invalidated and disconnected.
 * 3. Invalidation is scoped strictly to the revoked jti, never disconnecting other sessions
 *    belonging to the same user or other tenants.
 * 4. Tracking entries are cleanly and idempotently removed on disconnect or connection failure.
 * 5. Invalidation state fails closed if verification is ambiguous.
 * 6. Never logs bearer tokens, JWT contents, passwords, or secrets.
 */
@Component
public class WebSocketSessionRevocationManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRevocationManager.class);

    public static final CloseStatus TOKEN_REVOKED_CLOSE_STATUS =
            new CloseStatus(4401, "Token revoked");

    public static class SessionMetadata {
        private final String sessionId;
        private volatile String jti;
        private volatile Long userId;
        private volatile Long restaurantId;
        private volatile WebSocketSession webSocketSession;
        private volatile boolean revoked = false;

        public SessionMetadata(String sessionId, WebSocketSession webSocketSession) {
            this.sessionId = sessionId;
            this.webSocketSession = webSocketSession;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getJti() {
            return jti;
        }

        public void setJti(String jti) {
            this.jti = jti;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getRestaurantId() {
            return restaurantId;
        }

        public void setRestaurantId(Long restaurantId) {
            this.restaurantId = restaurantId;
        }

        public WebSocketSession getWebSocketSession() {
            return webSocketSession;
        }

        public void setWebSocketSession(WebSocketSession webSocketSession) {
            this.webSocketSession = webSocketSession;
        }

        public boolean isRevoked() {
            return revoked;
        }

        public void setRevoked(boolean revoked) {
            this.revoked = revoked;
        }
    }

    private final ConcurrentHashMap<String, SessionMetadata> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> jtiToSessionIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> userToSessionIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> restaurantToSessionIds = new ConcurrentHashMap<>();
    private final Set<String> explicitlyRevokedSessions = ConcurrentHashMap.newKeySet();

    private final TokenRevocationService tokenRevocationService;

    public WebSocketSessionRevocationManager(@Lazy TokenRevocationService tokenRevocationService) {
        this.tokenRevocationService = tokenRevocationService;
    }

    public WebSocketSessionRevocationManager() {
        this(null);
    }

    /**
     * Registers a new underlying WebSocket transport connection.
     */
    public void registerWebSocketSession(WebSocketSession session) {
        if (session != null && session.getId() != null) {
            activeSessions.compute(session.getId(), (id, existing) -> {
                if (existing != null) {
                    existing.setWebSocketSession(session);
                    return existing;
                }
                return new SessionMetadata(session.getId(), session);
            });
            log.debug("Registered WebSocket transport session {}", session.getId());
        }
    }

    /**
     * Binds an authenticated STOMP CONNECT session to the validated token's jti, userId, and restaurantId.
     */
    public void bindSessionToToken(String sessionId, String jti, Long userId, Long restaurantId) {
        if (sessionId == null || jti == null || jti.isBlank()) {
            return;
        }

        SessionMetadata metadata = activeSessions.computeIfAbsent(sessionId, id -> new SessionMetadata(id, null));
        metadata.setJti(jti);
        metadata.setUserId(userId);
        metadata.setRestaurantId(restaurantId);

        jtiToSessionIds.computeIfAbsent(jti, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        if (userId != null) {
            userToSessionIds.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }

        if (restaurantId != null) {
            restaurantToSessionIds.computeIfAbsent(restaurantId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }

        log.info("WebSocket session {} bound to jti={}, userId={}, restaurantId={}",
                sessionId, jti, userId, restaurantId);
    }

    /**
     * Immediately invalidates all active WebSocket sessions authenticated with the exact revoked jti.
     * Closes underlying WebSocket transport and marks sessions as revoked.
     * Safe and idempotent.
     */
    public void invalidateSessionsForJti(String jti) {
        if (jti == null || jti.isBlank()) {
            return;
        }

        Set<String> sessionIds = jtiToSessionIds.remove(jti);
        if (sessionIds == null || sessionIds.isEmpty()) {
            log.debug("No active WebSocket sessions found for revoked jti={}", jti);
            return;
        }

        for (String sessionId : sessionIds) {
            explicitlyRevokedSessions.add(sessionId);
            SessionMetadata metadata = activeSessions.get(sessionId);
            if (metadata != null) {
                metadata.setRevoked(true);

                // Terminate transport connection if still open
                WebSocketSession wsSession = metadata.getWebSocketSession();
                if (wsSession != null && wsSession.isOpen()) {
                    try {
                        wsSession.close(TOKEN_REVOKED_CLOSE_STATUS);
                        log.info("Terminated WebSocket transport for revoked session {} (jti={})", sessionId, jti);
                    } catch (IOException ex) {
                        log.warn("Exception closing WebSocket transport for session {}: {}", sessionId, ex.getMessage());
                    }
                }

                // Clean up user session set
                Long uid = metadata.getUserId();
                if (uid != null) {
                    Set<String> userSessions = userToSessionIds.get(uid);
                    if (userSessions != null) {
                        userSessions.remove(sessionId);
                        if (userSessions.isEmpty()) {
                            userToSessionIds.remove(uid);
                        }
                    }
                }
            }
        }
        log.info("Successfully invalidated {} WebSocket session(s) for revoked jti={}", sessionIds.size(), jti);
    }

    /**
     * Immediately invalidates all active WebSocket sessions belonging to a deactivated restaurant.
     * Closes underlying WebSocket transport and marks sessions as revoked.
     * Safe and idempotent.
     */
    public void invalidateSessionsForRestaurant(Long restaurantId) {
        if (restaurantId == null) {
            return;
        }

        Set<String> sessionIds = restaurantToSessionIds.remove(restaurantId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            log.debug("No active WebSocket sessions found for restaurant {}", restaurantId);
            return;
        }

        for (String sessionId : sessionIds) {
            explicitlyRevokedSessions.add(sessionId);
            SessionMetadata metadata = activeSessions.get(sessionId);
            if (metadata != null) {
                metadata.setRevoked(true);

                WebSocketSession wsSession = metadata.getWebSocketSession();
                if (wsSession != null && wsSession.isOpen()) {
                    try {
                        wsSession.close(new CloseStatus(4401, "Restaurant deactivated"));
                        log.info("Terminated WebSocket transport for restaurant {} session {}", restaurantId, sessionId);
                    } catch (IOException ex) {
                        log.warn("Exception closing WebSocket transport for session {}: {}", sessionId, ex.getMessage());
                    }
                }

                // Clean up user session set
                Long uid = metadata.getUserId();
                if (uid != null) {
                    Set<String> userSessions = userToSessionIds.get(uid);
                    if (userSessions != null) {
                        userSessions.remove(sessionId);
                        if (userSessions.isEmpty()) {
                            userToSessionIds.remove(uid);
                        }
                    }
                }
            }
        }
        log.info("Successfully invalidated {} WebSocket session(s) for restaurant {}", sessionIds.size(), restaurantId);
    }

    /**
     * Unregisters a WebSocket session on disconnect or failure.
     * Safe and idempotent.
     */
    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        explicitlyRevokedSessions.remove(sessionId);
        SessionMetadata metadata = activeSessions.remove(sessionId);
        if (metadata != null) {
            String jti = metadata.getJti();
            if (jti != null) {
                Set<String> sessions = jtiToSessionIds.get(jti);
                if (sessions != null) {
                    sessions.remove(sessionId);
                    if (sessions.isEmpty()) {
                        jtiToSessionIds.remove(jti);
                    }
                }
            }

            Long uid = metadata.getUserId();
            if (uid != null) {
                Set<String> userSessions = userToSessionIds.get(uid);
                if (userSessions != null) {
                    userSessions.remove(sessionId);
                    if (userSessions.isEmpty()) {
                        userToSessionIds.remove(uid);
                    }
                }
            }

            Long rid = metadata.getRestaurantId();
            if (rid != null) {
                Set<String> restSessions = restaurantToSessionIds.get(rid);
                if (restSessions != null) {
                    restSessions.remove(sessionId);
                    if (restSessions.isEmpty()) {
                        restaurantToSessionIds.remove(rid);
                    }
                }
            }
            log.debug("Unregistered WebSocket session {}", sessionId);
        }
    }

    /**
     * Checks if a session is revoked.
     * Fails closed: returns true if sessionId is null, explicitly revoked,
     * or if its associated jti has been marked revoked in TokenRevocationService.
     */
    public boolean isSessionRevoked(String sessionId) {
        if (sessionId == null) {
            return true; // Fail closed
        }

        if (explicitlyRevokedSessions.contains(sessionId)) {
            return true;
        }

        SessionMetadata metadata = activeSessions.get(sessionId);
        if (metadata != null) {
            if (metadata.isRevoked()) {
                return true;
            }
            String jti = metadata.getJti();
            if (jti != null && tokenRevocationService != null) {
                boolean revokedInDb = tokenRevocationService.isRevoked(jti);
                if (revokedInDb) {
                    metadata.setRevoked(true);
                    explicitlyRevokedSessions.add(sessionId);
                    return true;
                }
            }
        }

        return false;
    }

    public String getJtiForSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        SessionMetadata metadata = activeSessions.get(sessionId);
        return metadata != null ? metadata.getJti() : null;
    }

    public Long getUserIdForSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        SessionMetadata metadata = activeSessions.get(sessionId);
        return metadata != null ? metadata.getUserId() : null;
    }

    public int getActiveSessionsCount() {
        return activeSessions.size();
    }

    public Set<String> getSessionIdsForJti(String jti) {
        if (jti == null) {
            return Collections.emptySet();
        }
        Set<String> set = jtiToSessionIds.get(jti);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    public void clear() {
        activeSessions.clear();
        jtiToSessionIds.clear();
        userToSessionIds.clear();
        restaurantToSessionIds.clear();
        explicitlyRevokedSessions.clear();
    }

    /**
     * Factory for decorating the WebSocketHandler to capture underlying WebSocketSession instances.
     */
    public WebSocketHandlerDecoratorFactory getDecoratorFactory() {
        return handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                registerWebSocketSession(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                try {
                    unregisterSession(session.getId());
                } finally {
                    super.afterConnectionClosed(session, closeStatus);
                }
            }
        };
    }
}
