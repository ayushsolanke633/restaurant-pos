package com.restaurant.restaurantpos.security;

import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.TokenRevocationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String SESSION_ATTR_JTI = "WS_AUTH_JTI";
    public static final String SESSION_ATTR_USER_ID = "WS_AUTH_USER_ID";
    public static final String SESSION_ATTR_REST_ID = "WS_AUTH_REST_ID";

    private static final Pattern KITCHEN_TOPIC_PATTERN = Pattern.compile("^/topic/restaurants/(\\d+)/kitchen/orders$");
    private static final Pattern NOTIFICATION_TOPIC_PATTERN = Pattern.compile("^/topic/restaurants/(\\d+)/notifications/(\\d+)$");

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final WaiterSessionManager waiterSessionManager;
    private final RestaurantWebSocketSessionManager restaurantWebSocketSessionManager;
    private final TokenRevocationService tokenRevocationService;
    private final WebSocketSessionRevocationManager webSocketSessionRevocationManager;

    public StompAuthChannelInterceptor(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this(jwtService, userDetailsService, null, null, null, null);
    }

    public StompAuthChannelInterceptor(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            WaiterSessionManager waiterSessionManager) {
        this(jwtService, userDetailsService, waiterSessionManager, null, null, null);
    }

    public StompAuthChannelInterceptor(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            WaiterSessionManager waiterSessionManager,
            RestaurantWebSocketSessionManager restaurantWebSocketSessionManager) {
        this(jwtService, userDetailsService, waiterSessionManager, restaurantWebSocketSessionManager, null, null);
    }

    public StompAuthChannelInterceptor(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            WaiterSessionManager waiterSessionManager,
            RestaurantWebSocketSessionManager restaurantWebSocketSessionManager,
            TokenRevocationService tokenRevocationService) {
        this(jwtService, userDetailsService, waiterSessionManager, restaurantWebSocketSessionManager, tokenRevocationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StompAuthChannelInterceptor(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            WaiterSessionManager waiterSessionManager,
            RestaurantWebSocketSessionManager restaurantWebSocketSessionManager,
            @Lazy TokenRevocationService tokenRevocationService,
            @Lazy WebSocketSessionRevocationManager webSocketSessionRevocationManager) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.waiterSessionManager = waiterSessionManager;
        this.restaurantWebSocketSessionManager = restaurantWebSocketSessionManager;
        this.tokenRevocationService = tokenRevocationService;
        this.webSocketSessionRevocationManager = webSocketSessionRevocationManager;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorization = accessor.getNativeHeader("Authorization");
            String token = null;

            if (authorization != null && !authorization.isEmpty()) {
                String authHeader = authorization.get(0);
                if (authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            } else {
                List<String> passcodes = accessor.getNativeHeader("passcode");
                if (passcodes != null && !passcodes.isEmpty()) {
                    token = passcodes.get(0);
                }
            }

            if (token != null) {
                try {
                    String jti = jwtService.extractJti(token);
                    if (tokenRevocationService != null && tokenRevocationService.isRevoked(jti)) {
                        return message;
                    }
                    String userEmail = jwtService.extractUsername(token);
                    if (userEmail != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
                        if (jwtService.isTokenValid(token, userDetails) && isSessionValid(userDetails, token)) {
                            if (userDetails instanceof CustomUserDetails cud && cud.getRestaurantId() != null) {
                                if (restaurantWebSocketSessionManager != null && restaurantWebSocketSessionManager.isRestaurantDeactivated(cud.getRestaurantId())) {
                                    throw new AccessDeniedException("Access denied: Restaurant is deactivated");
                                }
                                if (restaurantWebSocketSessionManager != null) {
                                    restaurantWebSocketSessionManager.registerSession(accessor.getSessionId(), cud.getRestaurantId());
                                }
                            }

                            // Bind session to token jti and user identity in session revocation manager
                            if (webSocketSessionRevocationManager != null && accessor.getSessionId() != null) {
                                Long uid = (userDetails instanceof CustomUserDetails cud) ? cud.getUserId() : null;
                                Long rid = (userDetails instanceof CustomUserDetails cud) ? cud.getRestaurantId() : null;
                                webSocketSessionRevocationManager.bindSessionToToken(accessor.getSessionId(), jti, uid, rid);
                            }

                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
                            mutableAccessor.setLeaveMutable(true);
                            mutableAccessor.setUser(authentication);

                            Map<String, Object> sessionAttributes = mutableAccessor.getSessionAttributes();
                            if (sessionAttributes != null) {
                                sessionAttributes.put(SESSION_ATTR_JTI, jti);
                                if (userDetails instanceof CustomUserDetails cud) {
                                    sessionAttributes.put(SESSION_ATTR_USER_ID, cud.getUserId());
                                    sessionAttributes.put(SESSION_ATTR_REST_ID, cud.getRestaurantId());
                                }
                            }

                            return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
                        }
                    }
                } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | com.restaurant.restaurantpos.exception.RevocationStorageException ex) {
                    throw new org.springframework.messaging.MessageDeliveryException("Service temporarily unavailable");
                } catch (Exception ex) {
                    // Invalid token or deactivated restaurant - do not authenticate
                }
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            assertSessionNotRevoked(accessor);

            String destination = accessor.getDestination();
            if (destination != null) {
                Matcher kitchenMatcher = KITCHEN_TOPIC_PATTERN.matcher(destination);
                Matcher notifMatcher = NOTIFICATION_TOPIC_PATTERN.matcher(destination);

                if (kitchenMatcher.matches()) {
                    Long targetRestaurantId = Long.parseLong(kitchenMatcher.group(1));
                    if (restaurantWebSocketSessionManager != null && restaurantWebSocketSessionManager.isRestaurantDeactivated(targetRestaurantId)) {
                        throw new AccessDeniedException("Access denied: Restaurant is deactivated");
                    }
                    CustomUserDetails userDetails = getAuthenticatedUser(accessor);

                    if (userDetails.getRestaurantId() == null || !userDetails.getRestaurantId().equals(targetRestaurantId)) {
                        throw new AccessDeniedException("Access denied: Tenant subscription mismatch");
                    }

                    String userRole = userDetails.getRoleName();
                    if (!"KITCHEN".equalsIgnoreCase(userRole) && !"ADMIN".equalsIgnoreCase(userRole)) {
                        throw new AccessDeniedException("Access denied: Role unauthorized for Kitchen topic");
                    }
                } else if (notifMatcher.matches()) {
                    Long targetRestaurantId = Long.parseLong(notifMatcher.group(1));
                    if (restaurantWebSocketSessionManager != null && restaurantWebSocketSessionManager.isRestaurantDeactivated(targetRestaurantId)) {
                        throw new AccessDeniedException("Access denied: Restaurant is deactivated");
                    }
                    Long targetUserId = Long.parseLong(notifMatcher.group(2));
                    CustomUserDetails userDetails = getAuthenticatedUser(accessor);

                    if (userDetails.getRestaurantId() == null || !userDetails.getRestaurantId().equals(targetRestaurantId)) {
                        throw new AccessDeniedException("Access denied: Tenant notification subscription mismatch");
                    }

                    if (userDetails.getUserId() == null || !userDetails.getUserId().equals(targetUserId)) {
                        throw new AccessDeniedException("Access denied: User notification subscription mismatch");
                    }
                } else {
                    getAuthenticatedUser(accessor);
                    throw new AccessDeniedException("Access denied: Unauthorized subscription destination: " + destination);
                }
            } else {
                throw new AccessDeniedException("Access denied: Destination missing");
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            assertSessionNotRevoked(accessor);

            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic")) {
                throw new AccessDeniedException("Access denied: Client publishing to topic destinations is not allowed");
            }
            throw new AccessDeniedException("Access denied: Client message publishing is not supported");
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            if (restaurantWebSocketSessionManager != null) {
                restaurantWebSocketSessionManager.unregisterSession(accessor.getSessionId());
            }
            if (webSocketSessionRevocationManager != null) {
                webSocketSessionRevocationManager.unregisterSession(accessor.getSessionId());
            }
        }

        return message;
    }

    private void assertSessionNotRevoked(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        if (sessionId != null && webSocketSessionRevocationManager != null) {
            try {
                if (webSocketSessionRevocationManager.isSessionRevoked(sessionId)) {
                    throw new AccessDeniedException("Access denied: Token or WebSocket session has been revoked");
                }
                String sessionJti = webSocketSessionRevocationManager.getJtiForSession(sessionId);
                if (sessionJti != null && tokenRevocationService != null) {
                    if (tokenRevocationService.isRevoked(sessionJti)) {
                        webSocketSessionRevocationManager.invalidateSessionsForJti(sessionJti);
                        throw new AccessDeniedException("Access denied: Token has been revoked");
                    }
                }
            } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | com.restaurant.restaurantpos.exception.RevocationStorageException ex) {
                throw new org.springframework.messaging.MessageDeliveryException("Service temporarily unavailable");
            }
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            String attrJti = (String) sessionAttributes.get(SESSION_ATTR_JTI);
            if (attrJti != null && tokenRevocationService != null) {
                try {
                    if (tokenRevocationService.isRevoked(attrJti)) {
                        if (webSocketSessionRevocationManager != null) {
                            webSocketSessionRevocationManager.invalidateSessionsForJti(attrJti);
                        }
                        throw new AccessDeniedException("Access denied: Token has been revoked");
                    }
                } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | com.restaurant.restaurantpos.exception.RevocationStorageException ex) {
                    throw new org.springframework.messaging.MessageDeliveryException("Service temporarily unavailable");
                }
            }
        }
    }

    private CustomUserDetails getAuthenticatedUser(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null || !(principal instanceof UsernamePasswordAuthenticationToken authToken) || !(authToken.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("Access denied: Unauthenticated subscription attempt");
        }
        return userDetails;
    }

    private boolean isSessionValid(UserDetails userDetails, String jwt) {
        if (waiterSessionManager != null && userDetails instanceof CustomUserDetails cud) {
            if ("WAITER".equalsIgnoreCase(cud.getRoleName())) {
                return waiterSessionManager.isSessionActive(cud.getUserId(), jwt);
            }
        }
        return true;
    }
}
