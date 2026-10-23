package com.restaurant.restaurantpos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Spring application event listener that reliably cleans up WebSocket session tracking
 * upon any session termination (including client disconnect, TCP reset, or transport failure).
 */
@Component
public class WebSocketDisconnectListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketDisconnectListener.class);

    private final WebSocketSessionRevocationManager sessionRevocationManager;
    private final RestaurantWebSocketSessionManager restaurantWebSocketSessionManager;

    public WebSocketDisconnectListener(
            WebSocketSessionRevocationManager sessionRevocationManager,
            RestaurantWebSocketSessionManager restaurantWebSocketSessionManager) {
        this.sessionRevocationManager = sessionRevocationManager;
        this.restaurantWebSocketSessionManager = restaurantWebSocketSessionManager;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null) {
            log.debug("SessionDisconnectEvent received for session {}", sessionId);
            if (sessionRevocationManager != null) {
                sessionRevocationManager.unregisterSession(sessionId);
            }
            if (restaurantWebSocketSessionManager != null) {
                restaurantWebSocketSessionManager.unregisterSession(sessionId);
            }
        }
    }
}
