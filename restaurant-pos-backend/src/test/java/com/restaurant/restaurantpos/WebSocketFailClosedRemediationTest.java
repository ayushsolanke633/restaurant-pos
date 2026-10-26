package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.exception.RevocationStorageException;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.security.StompOutboundChannelInterceptor;
import com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AnnaDhara P0-3 Stage 3Q — Focused Fail-Closed WebSocket Remediation Tests
 *
 * Verifies strict fail-closed suppression on outbound delivery, mandatory dependency enforcement,
 * rejection on inbound CONNECT/SUBSCRIBE without leaks, and transient failure recovery without permanent revocation.
 */
public class WebSocketFailClosedRemediationTest {

    private TokenRevocationService mockRevocationService;
    private WebSocketSessionRevocationManager sessionRevocationManager;
    private StompOutboundChannelInterceptor outboundInterceptor;

    private JwtService mockJwtService;
    private CustomUserDetailsService mockUserDetailsService;
    private StompAuthChannelInterceptor authInterceptor;

    private final MessageChannel dummyChannel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        mockRevocationService = mock(TokenRevocationService.class);
        sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);
        outboundInterceptor = new StompOutboundChannelInterceptor(sessionRevocationManager, mockRevocationService);

        mockJwtService = mock(JwtService.class);
        mockUserDetailsService = mock(CustomUserDetailsService.class);
        authInterceptor = new StompAuthChannelInterceptor(
                mockJwtService, mockUserDetailsService, mockRevocationService, sessionRevocationManager
        );
    }

    // =========================================================================
    // 1. CONSTRUCTOR AND DEPENDENCY ENFORCEMENT TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. Constructor and Mandatory Dependency Rejection Tests")
    class ConstructorEnforcementTests {

        @Test
        @DisplayName("StompOutboundChannelInterceptor rejects null dependencies at construction")
        void testStompOutboundRejectsNullDependencies() {
            assertThrows(NullPointerException.class, () ->
                    new StompOutboundChannelInterceptor(null, mockRevocationService),
                    "Must reject null sessionRevocationManager");

            assertThrows(NullPointerException.class, () ->
                    new StompOutboundChannelInterceptor(sessionRevocationManager, null),
                    "Must reject null tokenRevocationService");
        }

        @Test
        @DisplayName("StompAuthChannelInterceptor rejects null dependencies at construction")
        void testStompAuthRejectsNullDependencies() {
            assertThrows(NullPointerException.class, () ->
                    new StompAuthChannelInterceptor(null, mockUserDetailsService, mockRevocationService, sessionRevocationManager),
                    "Must reject null JwtService");

            assertThrows(NullPointerException.class, () ->
                    new StompAuthChannelInterceptor(mockJwtService, null, mockRevocationService, sessionRevocationManager),
                    "Must reject null CustomUserDetailsService");

            assertThrows(NullPointerException.class, () ->
                    new StompAuthChannelInterceptor(mockJwtService, mockUserDetailsService, null, sessionRevocationManager),
                    "Must reject null TokenRevocationService");

            assertThrows(NullPointerException.class, () ->
                    new StompAuthChannelInterceptor(mockJwtService, mockUserDetailsService, mockRevocationService, null),
                    "Must reject null WebSocketSessionRevocationManager");
        }

        @Test
        @DisplayName("WebSocketSessionRevocationManager rejects null TokenRevocationService at construction")
        void testSessionRevocationManagerRejectsNullDependency() {
            assertThrows(NullPointerException.class, () ->
                    new WebSocketSessionRevocationManager(null),
                    "Must reject null TokenRevocationService");
        }
    }

    // =========================================================================
    // 2. OUTBOUND FAIL-CLOSED MESSAGE SUPPRESSION TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Outbound STOMP Fail-Closed Message Suppression Tests")
    class OutboundFailClosedTests {

        @Test
        @DisplayName("Null or missing session ID on outbound subscription message suppresses delivery")
        void testNullOrMissingSessionIdSuppressesDelivery() {
            // Case A: null sessionId
            StompHeaderAccessor accessorNull = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessorNull.setSessionId(null);
            accessorNull.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msgNull = MessageBuilder.createMessage(new byte[0], accessorNull.getMessageHeaders());

            Message<?> resultNull = outboundInterceptor.preSend(msgNull, dummyChannel);
            assertNull(resultNull, "Outbound message with null sessionId must be suppressed");

            // Case B: empty/blank sessionId
            StompHeaderAccessor accessorBlank = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessorBlank.setSessionId("   ");
            accessorBlank.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msgBlank = MessageBuilder.createMessage(new byte[0], accessorBlank.getMessageHeaders());

            Message<?> resultBlank = outboundInterceptor.preSend(msgBlank, dummyChannel);
            assertNull(resultBlank, "Outbound message with blank sessionId must be suppressed");
        }

        @Test
        @DisplayName("Outbound message to unknown session without registered token jti suppresses delivery")
        void testUnboundSessionMissingJtiSuppressesDelivery() {
            String unboundSession = "ws-unbound-session-123";
            // Note: Session is NOT registered in sessionRevocationManager

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(unboundSession);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = outboundInterceptor.preSend(msg, dummyChannel);
            assertNull(result, "Outbound delivery to session without bound jti/principal must be suppressed fail-closed");
        }

        @Test
        @DisplayName("Outbound message missing authenticated principal suppresses delivery")
        void testMissingPrincipalSuppressesDelivery() {
            String sessionId = "ws-no-principal-session";
            String jti = UUID.randomUUID().toString();
            // Bind with null userId and no principal on accessor
            sessionRevocationManager.bindSessionToToken(sessionId, jti, null, 1L);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            accessor.setUser(null);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = outboundInterceptor.preSend(msg, dummyChannel);
            assertNull(result, "Outbound delivery without authenticated principal must be suppressed");
        }

        @Test
        @DisplayName("Outbound message with malformed non-UUID jti suppresses delivery")
        void testMalformedJtiSuppressesDelivery() {
            String sessionId = "ws-malformed-jti-session";
            String malformedJti = "not-a-valid-uuid-string-12345";
            sessionRevocationManager.bindSessionToToken(sessionId, malformedJti, 10L, 1L);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = outboundInterceptor.preSend(msg, dummyChannel);
            assertNull(result, "Outbound message with malformed non-UUID jti must be suppressed");
            verify(mockRevocationService, never()).isRevoked(anyString());
        }

        @Test
        @DisplayName("Transient database outage suppresses delivery but preserves session state")
        void testTransientDatabaseOutageSuppressesDeliveryPreservesSession() {
            String sessionId = "ws-transient-outage-session";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 1L);

            when(mockRevocationService.isRevoked(jti))
                    .thenThrow(new DataAccessResourceFailureException("PostgreSQL connection timeout"));

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = outboundInterceptor.preSend(msg, dummyChannel);
            assertNull(result, "Delivery must be suppressed fail-closed during database outage");

            // Session state verification: Must NOT be permanently marked revoked in memory!
            assertFalse(sessionRevocationManager.isSessionExplicitlyRevoked(sessionId),
                    "Session must not be permanently marked revoked due to a transient database outage");

            // When database recovers, subsequent check evaluates cleanly without error
            doReturn(false).when(mockRevocationService).isRevoked(jti);
            assertFalse(sessionRevocationManager.isSessionRevoked(sessionId),
                    "Session must be active once database connectivity is restored");
        }

        @Test
        @DisplayName("Unexpected runtime exception during revocation validation suppresses delivery fail-closed")
        void testUnexpectedExceptionSuppressesDeliveryFailClosed() {
            String sessionId = "ws-unexpected-error-session";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 1L);

            when(mockRevocationService.isRevoked(jti))
                    .thenThrow(new IllegalStateException("Unexpected state in revocation cache"));

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = outboundInterceptor.preSend(msg, dummyChannel);
            assertNull(result, "Unexpected exception must fail closed and suppress delivery");

            // Session state verification: Must NOT be permanently revoked in memory!
            assertFalse(sessionRevocationManager.isSessionExplicitlyRevoked(sessionId),
                    "Session must not be permanently revoked on unexpected exception");

            // When error condition clears, subsequent check evaluates cleanly
            doReturn(false).when(mockRevocationService).isRevoked(jti);
            assertFalse(sessionRevocationManager.isSessionRevoked(sessionId),
                    "Session must be active once error condition clears");
        }

        @Test
        @DisplayName("Client-settable headers cannot bypass fail-closed outbound checks")
        void testClientSettableHeadersCannotBypassRevocation() {
            String sessionId = "ws-spoof-attempt-session";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 1L);

            // Token is REVOKED
            when(mockRevocationService.isRevoked(jti)).thenReturn(true);

            // Attacker attempts client header spoofing
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            accessor.addNativeHeader("X-Trusted-System-Message", "true");
            accessor.addNativeHeader("X-System-Originated", "true");
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = outboundInterceptor.preSend(msg, dummyChannel);
            assertNull(result, "Client-settable headers must NOT bypass revocation checks; delivery must be suppressed");
        }

        @Test
        @DisplayName("Normal valid active session delivers outbound message successfully")
        void testNormalActiveSessionDeliversSuccessfully() {
            String sessionId = "ws-normal-active-session";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 1L);

            when(mockRevocationService.isRevoked(jti)).thenReturn(false);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = outboundInterceptor.preSend(msg, dummyChannel);
            assertNotNull(result, "Normal outbound message with active token must be delivered");
        }
    }

    // =========================================================================
    // 3. INBOUND CONNECT AND SUBSCRIBE REJECTION TESTS
    // =========================================================================
    @Nested
    @DisplayName("3. Inbound CONNECT and SUBSCRIBE Rejection Tests")
    class InboundRejectionTests {

        @Test
        @DisplayName("CONNECT with revoked token fails authentication (user principal is null)")
        void testConnectWithRevokedTokenRejected() {
            String token = "revoked.jwt.token";
            String jti = UUID.randomUUID().toString();
            when(mockJwtService.extractJti(token)).thenReturn(jti);
            when(mockRevocationService.isRevoked(jti)).thenReturn(true);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.addNativeHeader("Authorization", "Bearer " + token);
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = authInterceptor.preSend(msg, dummyChannel);
            assertNotNull(result, "CONNECT frame must be returned");
            StompHeaderAccessor resAccessor = StompHeaderAccessor.wrap(result);
            assertNull(resAccessor.getUser(), "CONNECT with revoked token must not authenticate user");
        }

        @Test
        @DisplayName("CONNECT with store outage throws MessageDeliveryException with safe message")
        void testConnectWithStoreOutageThrowsSafeMessage() {
            String token = "outage.jwt.token";
            String jti = UUID.randomUUID().toString();
            when(mockJwtService.extractJti(token)).thenReturn(jti);
            when(mockRevocationService.isRevoked(jti))
                    .thenThrow(new DataAccessResourceFailureException("Postgres connection refused"));

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.addNativeHeader("Authorization", "Bearer " + token);
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                    authInterceptor.preSend(msg, dummyChannel));
            assertEquals("Service temporarily unavailable", ex.getMessage(),
                    "Must return generic safe error message to client without leaking database details");
        }

        @Test
        @DisplayName("SUBSCRIBE without session ID throws AccessDeniedException")
        void testSubscribeWithoutSessionIdRejected() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setSessionId(null);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    authInterceptor.preSend(msg, dummyChannel));
            assertTrue(ex.getMessage().contains("Missing WebSocket session identifier"));
        }

        @Test
        @DisplayName("SUBSCRIBE on revoked session throws AccessDeniedException")
        void testSubscribeOnRevokedSessionRejected() {
            String sessionId = "ws-revoked-subscriber";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 1L);
            sessionRevocationManager.invalidateSessionsForRestaurant(1L);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setSessionId(sessionId);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    authInterceptor.preSend(msg, dummyChannel));
            assertTrue(ex.getMessage().contains("revoked"));
        }

        @Test
        @DisplayName("SUBSCRIBE with store outage throws MessageDeliveryException")
        void testSubscribeWithStoreOutageThrowsDeliveryException() {
            String sessionId = "ws-sub-db-outage";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 1L);

            when(mockRevocationService.isRevoked(jti))
                    .thenThrow(new RevocationStorageException("Storage down", new RuntimeException()));

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setSessionId(sessionId);
            accessor.setDestination("/topic/restaurants/1/kitchen/orders");
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () ->
                    authInterceptor.preSend(msg, dummyChannel));
            assertEquals("Service temporarily unavailable", ex.getMessage());
        }
    }
}
