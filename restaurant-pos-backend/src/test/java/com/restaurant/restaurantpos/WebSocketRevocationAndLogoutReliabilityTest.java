package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.LoginResponse;
import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderEvent;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RevokedToken;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.RevocationStorageException;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RevokedTokenRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.security.StompOutboundChannelInterceptor;
import com.restaurant.restaurantpos.security.WebSocketDisconnectListener;
import com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager;
import com.restaurant.restaurantpos.service.AuthService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.RevokedTokenPersistenceHelper;
import com.restaurant.restaurantpos.service.TokenRevocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class WebSocketRevocationAndLogoutReliabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebSocketSessionRevocationManager sessionRevocationManager;

    @Autowired
    private StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Autowired
    private StompOutboundChannelInterceptor stompOutboundChannelInterceptor;

    @Autowired
    private WebSocketDisconnectListener disconnectListener;

    @SpyBean
    private RevokedTokenPersistenceHelper persistenceHelper;

    private Restaurant restaurant;
    private Role kitchenRole;
    private Role adminRole;
    private User chefUser;
    private User managerUser;

    @BeforeEach
    void setUp() {
        sessionRevocationManager.clear();
        revokedTokenRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        kitchenRole = roleRepository.findByName("KITCHEN")
                .orElseGet(() -> roleRepository.save(new Role(null, "KITCHEN")));
        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));

        restaurant = new Restaurant();
        restaurant.setName("WebSocket Security Grill");
        restaurant.setIsActive(true);
        restaurant = restaurantRepository.save(restaurant);

        chefUser = new User();
        chefUser.setName("Head Chef");
        chefUser.setEmail("chef@wssecurity.com");
        chefUser.setPasswordHash(passwordEncoder.encode("ChefPassword123!"));
        chefUser.setRole(kitchenRole);
        chefUser.setRestaurant(restaurant);
        chefUser.setIsActive(true);
        chefUser.setTokenVersion(1);
        chefUser = userRepository.save(chefUser);

        managerUser = new User();
        managerUser.setName("Floor Manager");
        managerUser.setEmail("manager@wssecurity.com");
        managerUser.setPasswordHash(passwordEncoder.encode("ManagerPassword123!"));
        managerUser.setRole(adminRole);
        managerUser.setRestaurant(restaurant);
        managerUser.setIsActive(true);
        managerUser.setTokenVersion(1);
        managerUser = userRepository.save(managerUser);
    }

    @AfterEach
    void tearDown() {
        sessionRevocationManager.clear();
        revokedTokenRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
        Mockito.reset(persistenceHelper);
    }

    private String loginUser(String email, String password) {
        LoginResponse resp = authService.login(new LoginRequest(email, password));
        return resp.getToken();
    }

    // =========================================================================
    // PART A: WebSocket Revocation Tests
    // =========================================================================

    @Test
    @DisplayName("A1. A WebSocket session receives messages before logout")
    void testWebSocketSessionReceivesMessagesBeforeLogout() {
        String token1 = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String jti1 = jwtService.extractJti(token1);
        String sessionId1 = "ws-session-1";

        // Connect STOMP session 1
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setSessionId(sessionId1);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token1);
        Message<byte[]> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        Message<?> authenticatedMsg = stompAuthChannelInterceptor.preSend(connectMsg, mock(MessageChannel.class));
        assertNotNull(authenticatedMsg);
        StompHeaderAccessor authResult = StompHeaderAccessor.wrap(authenticatedMsg);
        assertNotNull(authResult.getUser());
        assertEquals("chef@wssecurity.com", authResult.getUser().getName());

        // Verify session bound to jti1 in manager
        assertFalse(sessionRevocationManager.isSessionRevoked(sessionId1));
        assertEquals(jti1, sessionRevocationManager.getJtiForSession(sessionId1));

        // Outbound STOMP MESSAGE frame towards session 1 before logout
        StompHeaderAccessor outboundAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        outboundAccessor.setSessionId(sessionId1);
        outboundAccessor.setDestination("/topic/restaurants/" + restaurant.getId() + "/kitchen/orders");
        KitchenOrderEvent payload = KitchenOrderEvent.builder().orderId(101L).restaurantId(restaurant.getId()).build();
        Message<KitchenOrderEvent> outboundMsg = MessageBuilder.createMessage(payload, outboundAccessor.getMessageHeaders());

        Message<?> result = stompOutboundChannelInterceptor.preSend(outboundMsg, mock(MessageChannel.class));
        assertNotNull(result, "Delivery must be permitted before token logout");
        assertEquals(payload, result.getPayload());
    }

    @Test
    @DisplayName("A2. Logging out revokes that session's access to subsequent protected messages")
    void testLogoutRevokesWebSocketSessionMessageDelivery() throws IOException {
        String token1 = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String jti1 = jwtService.extractJti(token1);
        String sessionId1 = "ws-session-revoked-1";

        // Register underlying WebSocketSession mock
        WebSocketSession mockWsSession = mock(WebSocketSession.class);
        when(mockWsSession.getId()).thenReturn(sessionId1);
        when(mockWsSession.isOpen()).thenReturn(true);
        sessionRevocationManager.registerWebSocketSession(mockWsSession);

        // Connect STOMP session
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setSessionId(sessionId1);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token1);
        stompAuthChannelInterceptor.preSend(
                MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()),
                mock(MessageChannel.class));

        // Log out user with token1
        authService.logout(token1);

        // Verify revocation persisted in DB
        assertTrue(tokenRevocationService.isRevoked(jti1));

        // Verify session transport was closed with policy violation status
        verify(mockWsSession).close(WebSocketSessionRevocationManager.TOKEN_REVOKED_CLOSE_STATUS);

        // Verify session is marked revoked in session revocation manager
        assertTrue(sessionRevocationManager.isSessionRevoked(sessionId1));

        // Attempt outbound message delivery to session 1
        StompHeaderAccessor outboundAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        outboundAccessor.setSessionId(sessionId1);
        outboundAccessor.setDestination("/topic/restaurants/" + restaurant.getId() + "/kitchen/orders");
        KitchenOrderEvent payload = KitchenOrderEvent.builder().orderId(102L).restaurantId(restaurant.getId()).build();
        Message<KitchenOrderEvent> outboundMsg = MessageBuilder.createMessage(payload, outboundAccessor.getMessageHeaders());

        Message<?> deliveryResult = stompOutboundChannelInterceptor.preSend(outboundMsg, mock(MessageChannel.class));
        assertNull(deliveryResult, "Delivery to revoked WebSocket session must be strictly suppressed (return null)");
    }

    @Test
    @DisplayName("A3. A different, non-revoked token's WebSocket session remains unaffected")
    void testNonRevokedWebSocketSessionRemainsUnaffected() {
        String tokenChef = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String tokenManager = loginUser("manager@wssecurity.com", "ManagerPassword123!");
        String sessionChef = "ws-chef-session";
        String sessionManager = "ws-manager-session";

        // Connect Chef session
        StompHeaderAccessor chefConnect = StompHeaderAccessor.create(StompCommand.CONNECT);
        chefConnect.setSessionId(sessionChef);
        chefConnect.addNativeHeader("Authorization", "Bearer " + tokenChef);
        stompAuthChannelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], chefConnect.getMessageHeaders()), mock(MessageChannel.class));

        // Connect Manager session
        StompHeaderAccessor mgrConnect = StompHeaderAccessor.create(StompCommand.CONNECT);
        mgrConnect.setSessionId(sessionManager);
        mgrConnect.addNativeHeader("Authorization", "Bearer " + tokenManager);
        stompAuthChannelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], mgrConnect.getMessageHeaders()), mock(MessageChannel.class));

        // Log out Chef token only
        authService.logout(tokenChef);

        // Verify Chef session is revoked
        assertTrue(sessionRevocationManager.isSessionRevoked(sessionChef));

        // Verify Manager session is NOT revoked and unaffected
        assertFalse(sessionRevocationManager.isSessionRevoked(sessionManager));

        // Outbound message to Manager session passes through cleanly
        StompHeaderAccessor mgrOutbound = StompHeaderAccessor.create(StompCommand.MESSAGE);
        mgrOutbound.setSessionId(sessionManager);
        mgrOutbound.setDestination("/topic/restaurants/" + restaurant.getId() + "/kitchen/orders");
        Message<String> mgrMsg = MessageBuilder.createMessage("Order #200", mgrOutbound.getMessageHeaders());

        Message<?> mgrResult = stompOutboundChannelInterceptor.preSend(mgrMsg, mock(MessageChannel.class));
        assertNotNull(mgrResult, "Non-revoked session must continue receiving messages");

        // Outbound message to Chef session is blocked
        StompHeaderAccessor chefOutbound = StompHeaderAccessor.create(StompCommand.MESSAGE);
        chefOutbound.setSessionId(sessionChef);
        chefOutbound.setDestination("/topic/restaurants/" + restaurant.getId() + "/kitchen/orders");
        Message<String> chefMsg = MessageBuilder.createMessage("Order #200", chefOutbound.getMessageHeaders());

        Message<?> chefResult = stompOutboundChannelInterceptor.preSend(chefMsg, mock(MessageChannel.class));
        assertNull(chefResult, "Revoked session must have messages suppressed");
    }

    @Test
    @DisplayName("A4. A revoked token cannot create a new protected subscription or send messages")
    void testRevokedTokenCannotSubscribeOrSend() {
        String token = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String sessionId = "ws-session-attempt";

        // Connect session
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setSessionId(sessionId);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<?> connectRes = stompAuthChannelInterceptor.preSend(
                MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()),
                mock(MessageChannel.class));
        assertNotNull(connectRes);

        // Now revoke token via logout
        authService.logout(token);

        // Attempt SUBSCRIBE with the revoked session
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId(sessionId);
        subAccessor.setDestination("/topic/restaurants/" + restaurant.getId() + "/kitchen/orders");
        subAccessor.setUser(StompHeaderAccessor.wrap(connectRes).getUser());

        Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                stompAuthChannelInterceptor.preSend(subMsg, mock(MessageChannel.class)),
                "SUBSCRIBE on revoked session/token must throw AccessDeniedException");

        // Attempt SEND with the revoked session
        StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        sendAccessor.setSessionId(sessionId);
        sendAccessor.setDestination("/app/kitchen/orders");
        sendAccessor.setUser(StompHeaderAccessor.wrap(connectRes).getUser());

        Message<byte[]> sendMsg = MessageBuilder.createMessage(new byte[0], sendAccessor.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                stompAuthChannelInterceptor.preSend(sendMsg, mock(MessageChannel.class)),
                "SEND on revoked session/token must throw AccessDeniedException");

        // Attempt new CONNECT with the revoked token on a fresh session
        StompHeaderAccessor freshConnect = StompHeaderAccessor.create(StompCommand.CONNECT);
        freshConnect.setSessionId("fresh-session-new");
        freshConnect.addNativeHeader("Authorization", "Bearer " + token);
        Message<?> freshResult = stompAuthChannelInterceptor.preSend(
                MessageBuilder.createMessage(new byte[0], freshConnect.getMessageHeaders()),
                mock(MessageChannel.class));

        StompHeaderAccessor freshResultAccessor = StompHeaderAccessor.wrap(freshResult);
        assertNull(freshResultAccessor.getUser(), "Fresh CONNECT with revoked token must not authenticate");
    }

    @Test
    @DisplayName("A5. WebSocket disconnect and cleanup are idempotent and concurrent-safe")
    void testWebSocketDisconnectAndCleanupIdempotency() {
        String token = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String sessionId = "ws-session-disconnect-test";

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setSessionId(sessionId);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);
        stompAuthChannelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()), mock(MessageChannel.class));

        assertEquals(1, sessionRevocationManager.getActiveSessionsCount());

        // 1. STOMP DISCONNECT frame
        StompHeaderAccessor disconnectAccessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        disconnectAccessor.setSessionId(sessionId);
        assertDoesNotThrow(() ->
                stompAuthChannelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], disconnectAccessor.getMessageHeaders()), mock(MessageChannel.class)));
        assertEquals(0, sessionRevocationManager.getActiveSessionsCount());

        // 2. Transport SessionDisconnectEvent (idempotent repeated cleanup)
        SessionDisconnectEvent disconnectEvent = new SessionDisconnectEvent(this, mock(Message.class), sessionId, CloseStatus.NORMAL);
        assertDoesNotThrow(() -> disconnectListener.handleSessionDisconnect(disconnectEvent));
        assertDoesNotThrow(() -> disconnectListener.handleSessionDisconnect(disconnectEvent));

        // 3. Concurrent disconnect and logout calls are safe
        assertDoesNotThrow(() -> sessionRevocationManager.invalidateSessionsForJti("non-existent-jti"));
        assertDoesNotThrow(() -> sessionRevocationManager.unregisterSession(sessionId));
    }

    // =========================================================================
    // PART B: Reliable Logout Persistence Tests
    // =========================================================================

    @Test
    @DisplayName("B1. A successful logout persists revocation in database")
    void testSuccessfulLogoutPersistsRevocation() throws Exception {
        String token = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String jti = jwtService.extractJti(token);

        assertFalse(revokedTokenRepository.existsByJti(jti));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        assertTrue(revokedTokenRepository.existsByJti(jti), "Revocation record must be persisted in database");
        assertTrue(tokenRevocationService.isRevoked(jti));

        // Subsequent API call with this token is unauthorized
        mockMvc.perform(get("/api/kitchen/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("B2. A repeated logout succeeds safely and idempotently if token is already revoked")
    void testRepeatedLogoutIdempotent() throws Exception {
        String token = loginUser("chef@wssecurity.com", "ChefPassword123!");

        // First logout
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Second repeated logout
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Third repeated logout
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    @DisplayName("B3. A genuine database write failure produces HTTP 503 and never reports success")
    void testDatabaseWriteFailureProduces503() throws Exception {
        String token = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String jti = jwtService.extractJti(token);

        // Simulate database outage during saveAndFlushRevocation
        doThrow(new DataAccessResourceFailureException("PostgreSQL connection timeout"))
                .when(persistenceHelper).saveAndFlushRevocation(any(RevokedToken.class));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Revocation store temporarily unavailable. Please try again."));

        // Verify revocation was NOT persisted
        assertFalse(revokedTokenRepository.existsByJti(jti), "Failed revocation must not exist in repository");
    }

    @Test
    @DisplayName("B4. Concurrent duplicate insert is handled successfully after verifying record exists")
    void testConcurrentDuplicateInsertHandledWhenRecordExists() throws Exception {
        String token = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String jti = jwtService.extractJti(token);

        // Simulate concurrent duplicate-key constraint violation:
        // Our thread hits DataIntegrityViolationException because concurrent thread already inserted
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint 'revoked_tokens_pkey'"))
                .when(persistenceHelper).saveAndFlushRevocation(any(RevokedToken.class));

        // When persistenceHelper.existsByJti is called to verify, it confirms the record exists
        doReturn(true).when(persistenceHelper).existsByJti(jti);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Verifies that revocation existence was confirmed before reporting success
        verify(persistenceHelper).existsByJti(jti);
    }

    @Test
    @DisplayName("B5. Concurrent duplicate error without verified record throws 503 and fails closed")
    void testConcurrentDuplicateErrorWithoutRecordThrows503() throws Exception {
        String token = loginUser("chef@wssecurity.com", "ChefPassword123!");
        String jti = jwtService.extractJti(token);

        // Simulate DataIntegrityViolationException
        doThrow(new DataIntegrityViolationException("foreign key or check constraint failure"))
                .when(persistenceHelper).saveAndFlushRevocation(any(RevokedToken.class));

        // When verifying existence, record does NOT exist
        doReturn(false).when(persistenceHelper).existsByJti(anyString());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Revocation store temporarily unavailable. Please try again."));

        assertFalse(revokedTokenRepository.existsByJti(jti));
    }
}
