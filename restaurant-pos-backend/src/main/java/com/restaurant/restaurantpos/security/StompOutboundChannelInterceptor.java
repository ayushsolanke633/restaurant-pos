package com.restaurant.restaurantpos.security;

import com.restaurant.restaurantpos.service.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * ChannelInterceptor for clientOutboundChannel that suppresses delivery of messages
 * to WebSocket sessions whose authentication tokens have been revoked.
 *
 * Prevents existing subscriptions on revoked sessions from continuing to receive
 * broadcasted events (orders, notifications) after token logout.
 */
@Component
public class StompOutboundChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompOutboundChannelInterceptor.class);

    private final WebSocketSessionRevocationManager sessionRevocationManager;
    private final TokenRevocationService tokenRevocationService;

    @Autowired
    public StompOutboundChannelInterceptor(
            WebSocketSessionRevocationManager sessionRevocationManager,
            @Lazy TokenRevocationService tokenRevocationService) {
        this.sessionRevocationManager = java.util.Objects.requireNonNull(
                sessionRevocationManager, "sessionRevocationManager must not be null");
        this.tokenRevocationService = java.util.Objects.requireNonNull(
                tokenRevocationService, "tokenRevocationService must not be null");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (message == null) {
            return null;
        }

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        // Only inspect STOMP MESSAGE payloads sent outbound to client subscriptions
        boolean isSubscriptionMessage = StompCommand.MESSAGE.equals(accessor.getCommand())
                || org.springframework.messaging.simp.SimpMessageType.MESSAGE.equals(accessor.getMessageType());

        if (isSubscriptionMessage) {
            // Fail-closed: check mandatory revocation dependencies
            if (sessionRevocationManager == null || tokenRevocationService == null) {
                log.warn("Suppressing outbound message delivery: required revocation dependency is missing fail-closed");
                return null;
            }

            // Fail-closed: session ID must be present on user subscription delivery
            String sessionId = accessor.getSessionId();
            if (sessionId == null || sessionId.trim().isEmpty()) {
                log.warn("Suppressing outbound message delivery: missing session ID on outbound subscription message");
                return null;
            }

            try {
                // Fail-closed: verify session is not revoked in session manager
                if (sessionRevocationManager.isSessionRevoked(sessionId)) {
                    log.warn("Suppressing message delivery to revoked WebSocket session: sessionId={}", sessionId);
                    return null;
                }

                // Fail-closed: verify authenticated principal is present
                java.security.Principal principal = accessor.getUser();
                Long userId = sessionRevocationManager.getUserIdForSession(sessionId);
                if (principal == null && userId == null) {
                    log.warn("Suppressing outbound message delivery: missing authenticated principal for session: sessionId={}", sessionId);
                    return null;
                }

                // Fail-closed: verify token jti is present
                String jti = sessionRevocationManager.getJtiForSession(sessionId);
                if (jti == null || jti.trim().isEmpty()) {
                    log.warn("Suppressing outbound message delivery: missing token identifier (jti) for session: sessionId={}", sessionId);
                    return null;
                }

                // Fail-closed: verify jti is well-formed UUID
                try {
                    java.util.UUID.fromString(jti.trim());
                } catch (IllegalArgumentException ex) {
                    log.warn("Suppressing outbound message delivery: malformed token identifier (jti) for session: sessionId={}", sessionId);
                    return null;
                }

                // Check persistent revocation status in store
                if (tokenRevocationService.isRevoked(jti)) {
                    log.warn("Suppressing message delivery: token jti for WebSocket session is revoked: sessionId={}", sessionId);
                    sessionRevocationManager.invalidateSessionsForJti(jti);
                    return null;
                }
            } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | com.restaurant.restaurantpos.exception.RevocationStorageException ex) {
                log.warn("Revocation storage failure during outbound message check: operation=checkOutboundRevocation, sessionId={}, errorClass={}. Suppressing delivery fail-closed.",
                        sessionId, ex.getClass().getSimpleName());
                return null; // Suppress delivery fail-closed without marking session permanently revoked
            } catch (Exception ex) {
                log.error("Unexpected error during outbound message check: operation=checkOutboundRevocation, sessionId={}, errorClass={}. Suppressing delivery fail-closed.",
                        sessionId, ex.getClass().getSimpleName());
                return null; // Suppress delivery fail-closed on any error to prevent outbound delivery bypass
            }
        }

        return message;
    }
}
