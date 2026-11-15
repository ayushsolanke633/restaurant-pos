package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.RestaurantWebSocketSessionManager;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.security.StompOutboundChannelInterceptor;
import com.restaurant.restaurantpos.security.WaiterSessionManager;
import com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AnnaDhara POS — Minor Update 3: Focused WebSocket Security Hardening Tests
 *
 * Verifies:
 * 1. CONNECT without token rejected (unauthenticated).
 * 2. CONNECT with invalid/malformed token rejected.
 * 3. CONNECT with expired token rejected.
 * 4. CONNECT with revoked token rejected.
 * 5. CONNECT with valid token succeeds (ADMIN, KITCHEN, WAITER).
 * 6. Subscription to same restaurant succeeds for authorized roles (ADMIN/KITCHEN for kitchen, any for own notifications).
 * 7. Cross-restaurant subscription rejected.
 * 8. Unauthorized role subscription rejected (WAITER rejected for kitchen topic).
 * 9. Outbound message delivery suppressed for revoked token.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class WebSocketSecurityHardeningTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private WaiterSessionManager waiterSessionManager;

    @Mock
    private RestaurantWebSocketSessionManager restaurantWebSocketSessionManager;

    @Mock
    private MessageChannel dummyChannel;

    private WebSocketSessionRevocationManager sessionRevocationManager;
    private StompAuthChannelInterceptor authInterceptor;
    private StompOutboundChannelInterceptor outboundInterceptor;

    private CustomUserDetails adminUserR1;
    private CustomUserDetails kitchenUserR1;
    private CustomUserDetails waiterUserR1;
    private CustomUserDetails kitchenUserR2;

    @BeforeEach
    void setUp() {
        sessionRevocationManager = new WebSocketSessionRevocationManager(tokenRevocationService);
        outboundInterceptor = new StompOutboundChannelInterceptor(sessionRevocationManager, tokenRevocationService);

        authInterceptor = new StompAuthChannelInterceptor(
                jwtService,
                userDetailsService,
                waiterSessionManager,
                restaurantWebSocketSessionManager,
                tokenRevocationService,
                sessionRevocationManager
        );

        adminUserR1 = new CustomUserDetails(1L, 10L, "ADMIN", "admin@r1.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        kitchenUserR1 = new CustomUserDetails(2L, 10L, "KITCHEN", "kitchen@r1.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_KITCHEN")));
        waiterUserR1 = new CustomUserDetails(3L, 10L, "WAITER", "waiter@r1.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_WAITER")));
        kitchenUserR2 = new CustomUserDetails(4L, 20L, "KITCHEN", "kitchen@r2.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_KITCHEN")));
    }

    // -------------------------------------------------------------------------
    // 1. CONNECT AUTHENTICATION TESTS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CONNECT without token leaves accessor unauthenticated")
    void testConnectWithoutTokenLeavesUnauthenticated() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("sess-no-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser(), "User must be null when connecting without authentication token");
    }

    @Test
    @DisplayName("CONNECT with invalid or malformed token is rejected / unauthenticated")
    void testConnectWithInvalidTokenRejected() {
        String invalidToken = "not.a.valid.jwt";
        when(jwtService.extractJti(invalidToken)).thenThrow(new RuntimeException("Malformed JWT"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("sess-invalid-token");
        accessor.addNativeHeader("Authorization", "Bearer " + invalidToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser(), "User must be null when token is malformed or invalid");
    }

    @Test
    @DisplayName("CONNECT with expired token is rejected / unauthenticated")
    void testConnectWithExpiredTokenRejected() {
        String expiredToken = "expired.jwt.token";
        String jti = UUID.randomUUID().toString();
        when(jwtService.extractJti(expiredToken)).thenReturn(jti);
        when(tokenRevocationService.isRevoked(jti)).thenReturn(false);
        when(jwtService.extractUsername(expiredToken)).thenReturn("admin@r1.com");
        when(userDetailsService.loadUserByUsername("admin@r1.com")).thenReturn(adminUserR1);
        when(jwtService.isTokenValid(expiredToken, adminUserR1)).thenReturn(false); // Expired!

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("sess-expired-token");
        accessor.addNativeHeader("Authorization", "Bearer " + expiredToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser(), "User must be null when token is expired");
    }

    @Test
    @DisplayName("CONNECT with revoked token is rejected / unauthenticated")
    void testConnectWithRevokedTokenRejected() {
        String revokedToken = "revoked.jwt.token";
        String jti = UUID.randomUUID().toString();
        when(jwtService.extractJti(revokedToken)).thenReturn(jti);
        when(tokenRevocationService.isRevoked(jti)).thenReturn(true); // Revoked in store!

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("sess-revoked-token");
        accessor.addNativeHeader("Authorization", "Bearer " + revokedToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser(), "User must be null when token jti is revoked");
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("CONNECT with valid token succeeds for ADMIN")
    void testConnectWithValidTokenSucceedsAdmin() {
        String validToken = "valid.admin.jwt";
        String jti = UUID.randomUUID().toString();
        when(jwtService.extractJti(validToken)).thenReturn(jti);
        when(tokenRevocationService.isRevoked(jti)).thenReturn(false);
        when(jwtService.extractUsername(validToken)).thenReturn("admin@r1.com");
        when(userDetailsService.loadUserByUsername("admin@r1.com")).thenReturn(adminUserR1);
        when(jwtService.isTokenValid(validToken, adminUserR1)).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("sess-valid-admin");
        accessor.addNativeHeader("Authorization", "Bearer " + validToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser(), "User must be authenticated for valid ADMIN token");
        assertEquals("admin@r1.com", resultAccessor.getUser().getName());
        assertTrue(resultAccessor.getUser() instanceof UsernamePasswordAuthenticationToken);
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) resultAccessor.getUser();
        CustomUserDetails details = (CustomUserDetails) auth.getPrincipal();
        assertEquals(1L, details.getUserId());
        assertEquals(10L, details.getRestaurantId());
        assertEquals("ADMIN", details.getRoleName());
    }

    @Test
    @DisplayName("CONNECT with valid token succeeds for KITCHEN")
    void testConnectWithValidTokenSucceedsKitchen() {
        String validToken = "valid.kitchen.jwt";
        String jti = UUID.randomUUID().toString();
        when(jwtService.extractJti(validToken)).thenReturn(jti);
        when(tokenRevocationService.isRevoked(jti)).thenReturn(false);
        when(jwtService.extractUsername(validToken)).thenReturn("kitchen@r1.com");
        when(userDetailsService.loadUserByUsername("kitchen@r1.com")).thenReturn(kitchenUserR1);
        when(jwtService.isTokenValid(validToken, kitchenUserR1)).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("sess-valid-kitchen");
        accessor.addNativeHeader("Authorization", "Bearer " + validToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser(), "User must be authenticated for valid KITCHEN token");
        assertEquals("kitchen@r1.com", resultAccessor.getUser().getName());
    }

    @Test
    @DisplayName("CONNECT with valid token succeeds for WAITER")
    void testConnectWithValidTokenSucceedsWaiter() {
        String validToken = "valid.waiter.jwt";
        String jti = UUID.randomUUID().toString();
        when(jwtService.extractJti(validToken)).thenReturn(jti);
        when(tokenRevocationService.isRevoked(jti)).thenReturn(false);
        when(jwtService.extractUsername(validToken)).thenReturn("waiter@r1.com");
        when(userDetailsService.loadUserByUsername("waiter@r1.com")).thenReturn(waiterUserR1);
        when(jwtService.isTokenValid(validToken, waiterUserR1)).thenReturn(true);
        when(waiterSessionManager.isSessionActive(3L, validToken)).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("sess-valid-waiter");
        accessor.addNativeHeader("Authorization", "Bearer " + validToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser(), "User must be authenticated for valid WAITER token");
        assertEquals("waiter@r1.com", resultAccessor.getUser().getName());
    }

    // -------------------------------------------------------------------------
    // 2. SUBSCRIPTION AUTHORIZATION TESTS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ADMIN can subscribe to own restaurant Kitchen topic")
    void testAdminCanSubscribeOwnKitchenTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-sub-admin");
        accessor.setDestination("/topic/restaurants/10/kitchen/orders");
        accessor.setUser(new UsernamePasswordAuthenticationToken(adminUserR1, null, adminUserR1.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result, "ADMIN must be permitted to subscribe to own restaurant kitchen topic");
    }

    @Test
    @DisplayName("KITCHEN can subscribe to own restaurant Kitchen topic")
    void testKitchenCanSubscribeOwnKitchenTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-sub-kitchen");
        accessor.setDestination("/topic/restaurants/10/kitchen/orders");
        accessor.setUser(new UsernamePasswordAuthenticationToken(kitchenUserR1, null, kitchenUserR1.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result, "KITCHEN must be permitted to subscribe to own restaurant kitchen topic");
    }

    @Test
    @DisplayName("WAITER cannot subscribe to Kitchen topic (unauthorized role)")
    void testWaiterCannotSubscribeKitchenTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-sub-waiter-kitchen");
        accessor.setDestination("/topic/restaurants/10/kitchen/orders");
        accessor.setUser(new UsernamePasswordAuthenticationToken(waiterUserR1, null, waiterUserR1.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authInterceptor.preSend(message, dummyChannel));
        assertTrue(ex.getMessage().contains("Role unauthorized for Kitchen topic"));
    }

    @Test
    @DisplayName("Cross-restaurant subscription to Kitchen topic is rejected")
    void testCrossRestaurantKitchenSubscriptionRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-cross-kitchen");
        // User is from Restaurant 20, attempting to subscribe to Restaurant 10
        accessor.setDestination("/topic/restaurants/10/kitchen/orders");
        accessor.setUser(new UsernamePasswordAuthenticationToken(kitchenUserR2, null, kitchenUserR2.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authInterceptor.preSend(message, dummyChannel));
        assertTrue(ex.getMessage().contains("Tenant subscription mismatch"));
    }

    @Test
    @DisplayName("Authorized user can subscribe to own notification topic")
    void testUserCanSubscribeOwnNotificationTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-sub-notif-own");
        // Waiter (userId=3, restaurantId=10)
        accessor.setDestination("/topic/restaurants/10/notifications/3");
        accessor.setUser(new UsernamePasswordAuthenticationToken(waiterUserR1, null, waiterUserR1.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = authInterceptor.preSend(message, dummyChannel);
        assertNotNull(result, "User must be permitted to subscribe to own notification destination");
    }

    @Test
    @DisplayName("Cross-user notification subscription is rejected")
    void testCrossUserNotificationSubscriptionRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-cross-user-notif");
        // Waiter (userId=3, restaurantId=10) attempting to subscribe to userId=99
        accessor.setDestination("/topic/restaurants/10/notifications/99");
        accessor.setUser(new UsernamePasswordAuthenticationToken(waiterUserR1, null, waiterUserR1.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authInterceptor.preSend(message, dummyChannel));
        assertTrue(ex.getMessage().contains("User notification subscription mismatch"));
    }

    @Test
    @DisplayName("Cross-restaurant notification subscription is rejected")
    void testCrossRestaurantNotificationSubscriptionRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-cross-rest-notif");
        // Waiter (userId=3, restaurantId=10) attempting to subscribe to restaurantId=99
        accessor.setDestination("/topic/restaurants/99/notifications/3");
        accessor.setUser(new UsernamePasswordAuthenticationToken(waiterUserR1, null, waiterUserR1.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authInterceptor.preSend(message, dummyChannel));
        assertTrue(ex.getMessage().contains("Tenant notification subscription mismatch"));
    }

    @Test
    @DisplayName("Unauthorized topic paths are rejected")
    void testUnauthorizedTopicPathRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-bad-topic");
        accessor.setDestination("/topic/general/orders");
        accessor.setUser(new UsernamePasswordAuthenticationToken(adminUserR1, null, adminUserR1.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authInterceptor.preSend(message, dummyChannel));
        assertTrue(ex.getMessage().contains("Unauthorized subscription destination"));
    }

    @Test
    @DisplayName("Unauthenticated client cannot subscribe")
    void testUnauthenticatedSubscriptionRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sess-anon-sub");
        accessor.setDestination("/topic/restaurants/10/kitchen/orders");
        // No user set

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                authInterceptor.preSend(message, dummyChannel));
        assertTrue(ex.getMessage().contains("Unauthenticated subscription attempt"));
    }

    // -------------------------------------------------------------------------
    // 3. OUTBOUND MESSAGE DELIVERY & TOKEN REVOCATION TESTS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Outbound message delivery suppressed for session with revoked token")
    void testOutboundDeliverySuppressedForRevokedToken() {
        String sessionId = "sess-outbound-revoked";
        String jti = UUID.randomUUID().toString();
        sessionRevocationManager.bindSessionToToken(sessionId, jti, 1L, 10L);

        when(tokenRevocationService.isRevoked(jti)).thenReturn(true); // Revoked!

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/topic/restaurants/10/kitchen/orders");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = outboundInterceptor.preSend(message, dummyChannel);
        assertNull(result, "Outbound message delivery must be suppressed when token is revoked");
    }

    @Test
    @DisplayName("Outbound message delivered successfully for active unrevoked session")
    void testOutboundDeliverySucceedsForActiveToken() {
        String sessionId = "sess-outbound-active";
        String jti = UUID.randomUUID().toString();
        sessionRevocationManager.bindSessionToToken(sessionId, jti, 1L, 10L);

        when(tokenRevocationService.isRevoked(jti)).thenReturn(false); // Active!

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/topic/restaurants/10/kitchen/orders");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = outboundInterceptor.preSend(message, dummyChannel);
        assertNotNull(result, "Outbound message must be delivered to active session with valid token");
    }

    // -------------------------------------------------------------------------
    // 4. CLIENT PUBLISHING DISALLOWED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Client cannot publish messages via STOMP SEND")
    void testClientPublishingDisallowed() {
        String sessionId = "sess-send-test";
        String jti = UUID.randomUUID().toString();
        sessionRevocationManager.bindSessionToToken(sessionId, jti, 1L, 10L);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/topic/restaurants/10/kitchen/orders");
        accessor.setUser(new UsernamePasswordAuthenticationToken(adminUserR1, null, adminUserR1.getAuthorities()));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                authInterceptor.preSend(message, dummyChannel));
    }
}
