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
        this.sessionRevocationManager = sessionRevocationManager;
        this.tokenRevocationService = tokenRevocationService;
    }

    public StompOutboundChannelInterceptor(WebSocketSessionRevocationManager sessionRevocationManager) {
        this(sessionRevocationManager, null);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        // Only inspect STOMP MESSAGE payloads sent outbound to client subscriptions
        if (StompCommand.MESSAGE.equals(accessor.getCommand())) {
            String sessionId = accessor.getSessionId();
            if (sessionId != null && sessionRevocationManager != null) {
                // Check if session has been marked revoked or its token jti is revoked
                if (sessionRevocationManager.isSessionRevoked(sessionId)) {
                    log.warn("Suppressing message delivery to revoked WebSocket session: {}", sessionId);
                    return null; // Suppress delivery
                }

                String jti = sessionRevocationManager.getJtiForSession(sessionId);
                if (jti != null && tokenRevocationService != null && tokenRevocationService.isRevoked(jti)) {
                    log.warn("Suppressing message delivery: token jti for WebSocket session {} is revoked", sessionId);
                    sessionRevocationManager.invalidateSessionsForJti(jti);
                    return null; // Suppress delivery
                }
            }
        }

        return message;
    }
}
