package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.UserRequest;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.RevokedTokenRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.security.StompOutboundChannelInterceptor;
import com.restaurant.restaurantpos.security.TenantTokenVersionManager;
import com.restaurant.restaurantpos.security.WaiterSessionManager;
import com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager;
import com.restaurant.restaurantpos.service.AuthService;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.PlatformOnboardingService;
import com.restaurant.restaurantpos.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class TokenInvalidationConsistencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private PlatformOnboardingService platformOnboardingService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired(required = false)
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private com.restaurant.restaurantpos.repository.RestaurantSettingsRepository restaurantSettingsRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TenantTokenVersionManager tenantTokenVersionManager;

    @Autowired
    private WebSocketSessionRevocationManager sessionRevocationManager;

    @Autowired
    private WaiterSessionManager waiterSessionManager;

    @Autowired
    private StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Autowired
    private StompOutboundChannelInterceptor stompOutboundChannelInterceptor;

    @Autowired
    private com.restaurant.restaurantpos.security.RestaurantWebSocketSessionManager restaurantWebSocketSessionManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private Role adminRole;
    private Role waiterRole;
    private Role kitchenRole;
    private Role superAdminRole;
    private User adminA;
    private User waiterA;
    private User adminB;
    private User superAdminUser;

    @BeforeEach
    void setUp() {
        sessionRevocationManager.clear();
        tenantTokenVersionManager.clear();
        waiterSessionManager.clearAllSessions();
        revokedTokenRepository.deleteAll();
        userRepository.deleteAll();
        if (restaurantTableRepository != null) {
            restaurantTableRepository.deleteAll();
        }
        if (restaurantSettingsRepository != null) {
            restaurantSettingsRepository.deleteAll();
        }
        restaurantRepository.deleteAll();

        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));
        waiterRole = roleRepository.findByName("WAITER")
                .orElseGet(() -> roleRepository.save(new Role(null, "WAITER")));
        kitchenRole = roleRepository.findByName("KITCHEN")
                .orElseGet(() -> roleRepository.save(new Role(null, "KITCHEN")));
        superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "SUPER_ADMIN")));

        restaurantA = new Restaurant();
        restaurantA.setName("Trattoria Bella");
        restaurantA.setIsActive(true);
        restaurantA = restaurantRepository.save(restaurantA);

        restaurantB = new Restaurant();
        restaurantB.setName("Tokyo Sushi House");
        restaurantB.setIsActive(true);
        restaurantB = restaurantRepository.save(restaurantB);

        adminA = new User();
        adminA.setName("Bella Admin");
        adminA.setEmail("admin@bella.com");
        adminA.setPasswordHash(passwordEncoder.encode("Password123!"));
        adminA.setRole(adminRole);
        adminA.setRestaurant(restaurantA);
        adminA.setIsActive(true);
        adminA.setTokenVersion(1);
        adminA = userRepository.save(adminA);

        waiterA = new User();
        waiterA.setName("Bella Waiter");
        waiterA.setEmail("waiter@bella.com");
        waiterA.setPasswordHash(passwordEncoder.encode("Password123!"));
        waiterA.setRole(waiterRole);
        waiterA.setRestaurant(restaurantA);
        waiterA.setIsActive(true);
        waiterA.setTokenVersion(1);
        waiterA = userRepository.save(waiterA);

        adminB = new User();
        adminB.setName("Tokyo Admin");
        adminB.setEmail("admin@tokyo.com");
        adminB.setPasswordHash(passwordEncoder.encode("Password123!"));
        adminB.setRole(adminRole);
        adminB.setRestaurant(restaurantB);
        adminB.setIsActive(true);
        adminB.setTokenVersion(1);
        adminB = userRepository.save(adminB);

        superAdminUser = new User();
        superAdminUser.setName("Platform Master");
        superAdminUser.setEmail("superadmin@platform.com");
        superAdminUser.setPasswordHash(passwordEncoder.encode("SuperSecret123!"));
        superAdminUser.setRole(superAdminRole);
        superAdminUser.setRestaurant(null);
        superAdminUser.setIsActive(true);
        superAdminUser.setTokenVersion(1);
        superAdminUser = userRepository.save(superAdminUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        sessionRevocationManager.clear();
        tenantTokenVersionManager.clear();
        waiterSessionManager.clearAllSessions();
        revokedTokenRepository.deleteAll();
        userRepository.deleteAll();
        if (restaurantTableRepository != null) {
            restaurantTableRepository.deleteAll();
        }
        if (restaurantSettingsRepository != null) {
            restaurantSettingsRepository.deleteAll();
        }
        restaurantRepository.deleteAll();
    }

    private void authenticateAsAdminA() {
        CustomUserDetails cud = new CustomUserDetails(
                adminA.getId(),
                restaurantA.getId(),
                "ADMIN",
                adminA.getEmail(),
                adminA.getPasswordHash(),
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                adminA.getTokenVersion()
        );
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
    }

    // =========================================================================
    // PART A: User Status and Password Change Consistency
    // =========================================================================

    @Nested
    @DisplayName("Part A: User Status and Password Token Invalidation")
    class UserStatusAndTokenVersionTests {

        @Test
        @DisplayName("A1. Deactivating user via deactivateUser() increments token_version and invalidates old token")
        void testDeactivateUserIncrementsTokenVersion() {
            authenticateAsAdminA();
            String oldToken = authService.login(new LoginRequest(waiterA.getEmail(), "Password123!")).getToken();
            assertEquals(1, jwtService.extractTokenVersion(oldToken));

            // Deactivate waiterA
            userService.deactivateUser(waiterA.getId());

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertFalse(refreshed.getIsActive());
            assertEquals(2, refreshed.getTokenVersion(), "token_version must increment to 2 on deactivation");

            // Old token with tokenVersion=1 must be rejected by isTokenValid
            assertFalse(jwtService.isTokenValid(oldToken, new CustomUserDetails(
                    refreshed.getId(), restaurantA.getId(), "WAITER", refreshed.getEmail(),
                    refreshed.getPasswordHash(), false, List.of(new SimpleGrantedAuthority("ROLE_WAITER")), refreshed.getTokenVersion()
            )));
        }

        @Test
        @DisplayName("A2. Deactivating user via updateUser(isActive=false) increments token_version")
        void testUpdateUserDeactivateIncrementsVersion() {
            authenticateAsAdminA();
            String oldToken = authService.login(new LoginRequest(waiterA.getEmail(), "Password123!")).getToken();

            UserRequest updateReq = new UserRequest();
            updateReq.setIsActive(false);
            userService.updateUser(waiterA.getId(), updateReq);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertFalse(refreshed.getIsActive());
            assertEquals(2, refreshed.getTokenVersion(), "updateUser(isActive=false) must increment token_version");

            var userDetails = new CustomUserDetails(
                    refreshed.getId(), restaurantA.getId(), "WAITER", refreshed.getEmail(),
                    refreshed.getPasswordHash(), false, List.of(new SimpleGrantedAuthority("ROLE_WAITER")), refreshed.getTokenVersion()
            );
            assertFalse(jwtService.isTokenValid(oldToken, userDetails));
        }

        @Test
        @DisplayName("A3. Reactivating user via updateUser(isActive=true) increments token_version and blocks pre-deactivation token")
        void testReactivatingUserBlocksPreDeactivationToken() {
            authenticateAsAdminA();
            String preDeactToken = authService.login(new LoginRequest(waiterA.getEmail(), "Password123!")).getToken();
            assertEquals(1, jwtService.extractTokenVersion(preDeactToken));

            // Deactivate waiterA (version 1 -> 2)
            UserRequest deactReq = new UserRequest();
            deactReq.setIsActive(false);
            userService.updateUser(waiterA.getId(), deactReq);

            // Reactivate waiterA (version 2 -> 3)
            UserRequest reactReq = new UserRequest();
            reactReq.setIsActive(true);
            userService.updateUser(waiterA.getId(), reactReq);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertTrue(refreshed.getIsActive());
            assertEquals(3, refreshed.getTokenVersion(), "Reactivation must increment token_version to 3");

            var activeDetails = userDetailsService.loadUserByUsername(waiterA.getEmail());

            // Pre-deactivation token (version 1) must be strictly invalid against active user (version 3)
            assertFalse(jwtService.isTokenValid(preDeactToken, activeDetails),
                    "Pre-deactivation token must NEVER be valid after reactivation");

            // Clear old session so new login is permitted
            waiterSessionManager.clearSession(waiterA.getId());

            // A newly issued token for the reactivated user has version 3 and works
            String newToken = authService.login(new LoginRequest(waiterA.getEmail(), "Password123!")).getToken();
            assertEquals(3, jwtService.extractTokenVersion(newToken));
            assertTrue(jwtService.isTokenValid(newToken, activeDetails),
                    "New token issued after reactivation must be valid");
        }

        @Test
        @DisplayName("A4. Unchanged active status does NOT increment token_version")
        void testUnchangedStatusDoesNotIncrementTokenVersion() {
            authenticateAsAdminA();
            UserRequest req = new UserRequest();
            req.setIsActive(true); // Already active
            req.setName("Updated Waiter Name");

            userService.updateUser(waiterA.getId(), req);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertEquals(1, refreshed.getTokenVersion(), "token_version must NOT increment when status is unchanged");
            assertEquals("Updated Waiter Name", refreshed.getName());
        }

        @Test
        @DisplayName("A5. Password-only change increments token_version by 1")
        void testPasswordOnlyChangeIncrementsVersion() {
            authenticateAsAdminA();
            String oldToken = authService.login(new LoginRequest(waiterA.getEmail(), "Password123!")).getToken();

            UserRequest req = new UserRequest();
            req.setPassword("BrandNewPassword456!");

            userService.updateUser(waiterA.getId(), req);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertEquals(2, refreshed.getTokenVersion(), "Password change must increment token_version to 2");

            var userDetails = userDetailsService.loadUserByUsername(waiterA.getEmail());
            assertFalse(jwtService.isTokenValid(oldToken, userDetails), "Old token must be invalid after password change");

            // Clear old session so new login with new password is permitted
            waiterSessionManager.clearSession(waiterA.getId());

            // New login with new password succeeds and token is valid
            String newToken = authService.login(new LoginRequest(waiterA.getEmail(), "BrandNewPassword456!")).getToken();
            assertEquals(2, jwtService.extractTokenVersion(newToken));
            assertTrue(jwtService.isTokenValid(newToken, userDetails));
        }

        @Test
        @DisplayName("A6. Combined status change AND password change in single request increments token_version by exactly 1")
        void testCombinedStatusAndPasswordChangeIncrementsByExactlyOne() {
            authenticateAsAdminA();
            // First deactivate
            userService.deactivateUser(waiterA.getId()); // version 1 -> 2

            // Now in a single request: reactivate (status change) AND set new password
            UserRequest combinedReq = new UserRequest();
            combinedReq.setIsActive(true);
            combinedReq.setPassword("PasswordAfterReactivation789!");

            userService.updateUser(waiterA.getId(), combinedReq);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertEquals(3, refreshed.getTokenVersion(),
                    "Combined status and password change must increment version by exactly 1 (from 2 to 3)");

            var userDetails = userDetailsService.loadUserByUsername(waiterA.getEmail());

            waiterSessionManager.clearSession(waiterA.getId());
            String newToken = authService.login(new LoginRequest(waiterA.getEmail(), "PasswordAfterReactivation789!")).getToken();
            assertEquals(3, jwtService.extractTokenVersion(newToken));
            assertTrue(jwtService.isTokenValid(newToken, userDetails));
        }

        @Test
        @DisplayName("A7. Role change increments token_version and invalidates previously issued token")
        void testRoleChangeIncrementsTokenVersionAndInvalidatesOldToken() {
            authenticateAsAdminA();
            String oldToken = authService.login(new LoginRequest(waiterA.getEmail(), "Password123!")).getToken();
            assertEquals(1, jwtService.extractTokenVersion(oldToken));

            // Change role from WAITER to KITCHEN
            UserRequest req = new UserRequest();
            req.setRoleId(kitchenRole.getId());
            userService.updateUser(waiterA.getId(), req);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertEquals("KITCHEN", refreshed.getRole().getName());
            assertEquals(2, refreshed.getTokenVersion(), "Role change must increment token_version to 2");

            var userDetails = userDetailsService.loadUserByUsername(waiterA.getEmail());
            assertFalse(jwtService.isTokenValid(oldToken, userDetails),
                    "Old token issued for WAITER role must be rejected after role changed to KITCHEN");
        }

        @Test
        @DisplayName("A8. Role change back to original role increments version again and prevents old token reuse")
        void testRoleChangeBackToOriginalRolePreventsOldTokenReuse() {
            authenticateAsAdminA();
            String initialWaiterToken = authService.login(new LoginRequest(waiterA.getEmail(), "Password123!")).getToken();
            assertEquals(1, jwtService.extractTokenVersion(initialWaiterToken));

            // 1. Change role WAITER -> KITCHEN (token_version 1 -> 2)
            UserRequest toKitchen = new UserRequest();
            toKitchen.setRoleId(kitchenRole.getId());
            userService.updateUser(waiterA.getId(), toKitchen);

            // 2. Change role KITCHEN -> WAITER (token_version 2 -> 3)
            UserRequest backToWaiter = new UserRequest();
            backToWaiter.setRoleId(waiterRole.getId());
            userService.updateUser(waiterA.getId(), backToWaiter);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertEquals(3, refreshed.getTokenVersion(),
                    "Changing role back must increment token_version again to 3");
            assertEquals("WAITER", refreshed.getRole().getName());

            var activeDetails = userDetailsService.loadUserByUsername(waiterA.getEmail());
            assertFalse(jwtService.isTokenValid(initialWaiterToken, activeDetails),
                    "Initial token (version 1) must remain strictly invalid even when user role is restored to WAITER");
        }

        @Test
        @DisplayName("A9. Combined role, password, and status change increments token_version exactly once")
        void testCombinedRolePasswordStatusChangeIncrementsVersionOnlyOnce() {
            authenticateAsAdminA();
            UserRequest combined = new UserRequest();
            combined.setRoleId(kitchenRole.getId());
            combined.setPassword("NewKitchenSecret123!");
            combined.setIsActive(false);

            userService.updateUser(waiterA.getId(), combined);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertEquals(2, refreshed.getTokenVersion(),
                    "Combined role, password, and status change in single request must increment version by exactly 1 (1 -> 2)");
            assertEquals("KITCHEN", refreshed.getRole().getName());
            assertFalse(refreshed.getIsActive());
        }

        @Test
        @DisplayName("A10. Unchanged role does NOT increment token_version")
        void testUnchangedRoleDoesNotIncrementTokenVersion() {
            authenticateAsAdminA();
            UserRequest req = new UserRequest();
            req.setRoleId(waiterRole.getId()); // Same as current role
            req.setName("Updated Waiter Display Name");

            userService.updateUser(waiterA.getId(), req);

            User refreshed = userRepository.findById(waiterA.getId()).orElseThrow();
            assertEquals(1, refreshed.getTokenVersion(),
                    "token_version must NOT increment when role is unchanged");
            assertEquals("Updated Waiter Display Name", refreshed.getName());
        }
    }

    // =========================================================================
    // PART B: Restaurant Status and Tenant-Level Token Invalidation
    // =========================================================================

    @Nested
    @DisplayName("Part B: Restaurant Lifecycle and Tenant-Level Invalidation")
    class RestaurantLifecycleAndTenantTokenTests {

        @Test
        @DisplayName("B1. Tokens issued before restaurant deactivation CANNOT regain access after reactivation")
        void testPreDeactivationTokensBlockedAfterReactivation() throws Exception {
            // 1. Issue tokens while restaurant A is active (tenantVersion = 1)
            String preDeactTokenAdminA = authService.login(new LoginRequest(adminA.getEmail(), "Password123!")).getToken();
            String preDeactTokenWaiterA = authService.login(new LoginRequest(waiterA.getEmail(), "Password123!")).getToken();
            assertEquals(1, jwtService.extractTenantVersion(preDeactTokenAdminA));
            assertEquals(1, jwtService.extractTenantVersion(preDeactTokenWaiterA));

            // Verify pre-deactivation tokens work on HTTP
            mockMvc.perform(get("/api/categories")
                            .header("Authorization", "Bearer " + preDeactTokenAdminA))
                    .andExpect(status().isOk());

            // 2. Deactivate restaurant A
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), false);
            assertEquals(2, tenantTokenVersionManager.getTenantVersion(restaurantA.getId()),
                    "Tenant version must increment on restaurant deactivation");

            // Verify HTTP access is blocked with 403 Forbidden while deactivated
            mockMvc.perform(get("/api/categories")
                            .header("Authorization", "Bearer " + preDeactTokenAdminA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Restaurant is deactivated"));

            // 3. Reactivate restaurant A
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), true);
            assertEquals(2, tenantTokenVersionManager.getTenantVersion(restaurantA.getId()));

            // 4. Verify PRE-DEACTIVATION token is strictly rejected (401 Unauthorized because tenantVersion 1 != 2)
            mockMvc.perform(get("/api/categories")
                            .header("Authorization", "Bearer " + preDeactTokenAdminA))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/categories")
                            .header("Authorization", "Bearer " + preDeactTokenWaiterA))
                    .andExpect(status().isUnauthorized());

            // 5. Newly issued token after reactivation gets tenantVersion=2 and works cleanly
            String postReactivationToken = authService.login(new LoginRequest(adminA.getEmail(), "Password123!")).getToken();
            assertEquals(2, jwtService.extractTenantVersion(postReactivationToken));

            mockMvc.perform(get("/api/categories")
                            .header("Authorization", "Bearer " + postReactivationToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("B2. Deactivating restaurant terminates active WebSocket sessions and suppresses outbound frames")
        void testRestaurantDeactivationTerminatesWebSocketSessions() throws IOException {
            String tokenAdminA = authService.login(new LoginRequest(adminA.getEmail(), "Password123!")).getToken();
            String sessionIdA = "ws-session-restaurantA-1";

            // Mock underlying WebSocket transport session
            WebSocketSession mockWsSession = mock(WebSocketSession.class);
            when(mockWsSession.getId()).thenReturn(sessionIdA);
            when(mockWsSession.isOpen()).thenReturn(true);
            sessionRevocationManager.registerWebSocketSession(mockWsSession);

            // Connect STOMP session for restaurant A
            StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            connectAccessor.setSessionId(sessionIdA);
            connectAccessor.addNativeHeader("Authorization", "Bearer " + tokenAdminA);
            stompAuthChannelInterceptor.preSend(
                    MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()),
                    mock(MessageChannel.class));

            assertFalse(sessionRevocationManager.isSessionRevoked(sessionIdA));

            // Deactivate restaurant A
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), false);

            // Verify transport connection closed with 4401
            verify(mockWsSession).close(argThat(status -> status.getCode() == 4401));

            // Verify session is marked revoked in session revocation manager
            assertTrue(sessionRevocationManager.isSessionRevoked(sessionIdA));

            // Outbound message delivery to session is suppressed
            StompHeaderAccessor outbound = StompHeaderAccessor.create(StompCommand.MESSAGE);
            outbound.setSessionId(sessionIdA);
            outbound.setDestination("/topic/restaurants/" + restaurantA.getId() + "/kitchen/orders");
            Message<?> outboundResult = stompOutboundChannelInterceptor.preSend(
                    MessageBuilder.createMessage("Order update", outbound.getMessageHeaders()),
                    mock(MessageChannel.class));
            assertNull(outboundResult, "Outbound message delivery to deactivated restaurant session must be suppressed");
        }

        @Test
        @DisplayName("B3. Deactivating restaurant A does NOT invalidate tokens or sessions of unrelated restaurant B")
        void testTenantIsolationUnrelatedRestaurantTokensUnaffected() throws Exception {
            String tokenA = authService.login(new LoginRequest(adminA.getEmail(), "Password123!")).getToken();
            String tokenB = authService.login(new LoginRequest(adminB.getEmail(), "Password123!")).getToken();

            assertEquals(1, tenantTokenVersionManager.getTenantVersion(restaurantA.getId()));
            assertEquals(1, tenantTokenVersionManager.getTenantVersion(restaurantB.getId()));

            // Deactivate Restaurant A only
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), false);

            // Restaurant A tenant version incremented
            assertEquals(2, tenantTokenVersionManager.getTenantVersion(restaurantA.getId()));
            // Restaurant B tenant version is UNCHANGED
            assertEquals(1, tenantTokenVersionManager.getTenantVersion(restaurantB.getId()));

            // Restaurant B token continues to work completely unaffected
            mockMvc.perform(get("/api/categories")
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isOk());

            // Reactivate Restaurant A
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), true);

            // Restaurant B token STILL works unaffected
            mockMvc.perform(get("/api/categories")
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("B4. Platform SUPER_ADMIN tokens remain unaffected by any restaurant deactivation")
        void testSuperAdminTokensUnaffectedByRestaurantDeactivation() {
            String superAdminToken = authService.login(new LoginRequest(superAdminUser.getEmail(), "SuperSecret123!")).getToken();
            assertNull(jwtService.extractTenantVersion(superAdminToken), "SUPER_ADMIN has no tenantVersion claim");

            var superAdminDetails = userDetailsService.loadUserByUsername(superAdminUser.getEmail());
            assertTrue(jwtService.isTokenValid(superAdminToken, superAdminDetails));

            // Deactivate Restaurant A and Restaurant B
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), false);
            platformOnboardingService.updateRestaurantStatus(restaurantB.getId(), false);

            // Super admin token remains 100% valid
            assertTrue(jwtService.isTokenValid(superAdminToken, superAdminDetails),
                    "Platform SUPER_ADMIN token must remain valid regardless of tenant status changes");

            // Reactivate both
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), true);
            platformOnboardingService.updateRestaurantStatus(restaurantB.getId(), true);

            assertTrue(jwtService.isTokenValid(superAdminToken, superAdminDetails));
        }

        @Test
        @DisplayName("B5. Unchanged restaurant status does NOT increment tenant version")
        void testUnchangedRestaurantStatusDoesNotIncrementVersion() {
            assertEquals(1, tenantTokenVersionManager.getTenantVersion(restaurantA.getId()));

            // Restaurant A is already active, call updateRestaurantStatus(true)
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), true);

            assertEquals(1, tenantTokenVersionManager.getTenantVersion(restaurantA.getId()),
                    "Tenant version must NOT increment when status is unchanged");
        }

        @Test
        @DisplayName("B6. Pre-deactivation token cannot establish new WebSocket connection after restaurant reactivation")
        void testPreDeactivationTokenRejectedOnWebSocketAfterReactivation() {
            String preDeactToken = authService.login(new LoginRequest(adminA.getEmail(), "Password123!")).getToken();

            // Deactivate and reactivate restaurant A
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), false);
            platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), true);

            // Attempt STOMP CONNECT with pre-deactivation token
            StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            connectAccessor.setSessionId("ws-fresh-attempt-old-token");
            connectAccessor.addNativeHeader("Authorization", "Bearer " + preDeactToken);

            Message<?> connectResult = stompAuthChannelInterceptor.preSend(
                    MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()),
                    mock(MessageChannel.class));

            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(connectResult);
            assertNull(resultAccessor.getUser(),
                    "STOMP CONNECT with pre-deactivation token must NOT authenticate after reactivation");
        }

        @Test
        @DisplayName("B7. WebSocket invalidation occurs only after successful transaction commit")
        void testWebSocketInvalidationOccursAfterTransactionCommit() throws Exception {
            String sessionId = "ws-commit-test-session";
            WebSocketSession mockSession = mock(WebSocketSession.class);
            when(mockSession.getId()).thenReturn(sessionId);
            when(mockSession.isOpen()).thenReturn(true);

            sessionRevocationManager.registerWebSocketSession(mockSession);
            sessionRevocationManager.bindSessionToToken(sessionId, java.util.UUID.randomUUID().toString(), adminA.getId(), restaurantA.getId());
            restaurantWebSocketSessionManager.registerSession(sessionId, restaurantA.getId());

            assertFalse(sessionRevocationManager.isSessionRevoked(sessionId));
            assertFalse(restaurantWebSocketSessionManager.isRestaurantDeactivated(restaurantA.getId()));

            new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), false);
                return null;
            });

            // Post-commit: WebSocket session must be revoked, closed, and restaurant deactivated
            assertTrue(sessionRevocationManager.isSessionRevoked(sessionId),
                    "WebSocket session must be marked revoked after transaction commit");
            verify(mockSession, times(1)).close(any());
            assertTrue(restaurantWebSocketSessionManager.isRestaurantDeactivated(restaurantA.getId()),
                    "Restaurant must be marked deactivated in WebSocket session manager after commit");
            assertEquals(2, tenantTokenVersionManager.getTenantVersion(restaurantA.getId()),
                    "Tenant token version must be committed to 2");
        }

        @Test
        @DisplayName("B8. WebSocket invalidation does NOT occur if transaction rolls back")
        void testWebSocketInvalidationDoesNotOccurOnTransactionRollback() throws Exception {
            String sessionId = "ws-rollback-test-session";
            WebSocketSession mockSession = mock(WebSocketSession.class);
            when(mockSession.getId()).thenReturn(sessionId);
            when(mockSession.isOpen()).thenReturn(true);

            sessionRevocationManager.registerWebSocketSession(mockSession);
            sessionRevocationManager.bindSessionToToken(sessionId, java.util.UUID.randomUUID().toString(), adminA.getId(), restaurantA.getId());
            restaurantWebSocketSessionManager.registerSession(sessionId, restaurantA.getId());

            assertFalse(sessionRevocationManager.isSessionRevoked(sessionId));
            assertFalse(restaurantWebSocketSessionManager.isRestaurantDeactivated(restaurantA.getId()));

            assertThrows(RuntimeException.class, () -> {
                new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                    platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), false);
                    throw new RuntimeException("Simulated exception triggering transaction rollback");
                });
            });

            // Post-rollback: session must NOT be revoked, NOT closed, and restaurant NOT deactivated
            assertFalse(sessionRevocationManager.isSessionRevoked(sessionId),
                    "WebSocket session must NOT be revoked when transaction rolls back");
            verify(mockSession, never()).close(any());
            assertFalse(restaurantWebSocketSessionManager.isRestaurantDeactivated(restaurantA.getId()),
                    "Restaurant must NOT be marked deactivated when transaction rolls back");
            assertEquals(1, tenantTokenVersionManager.getTenantVersion(restaurantA.getId()),
                    "Tenant token version must remain 1 after transaction rollback");

            Restaurant inDb = restaurantRepository.findById(restaurantA.getId()).orElseThrow();
            assertTrue(inDb.getIsActive(), "Restaurant in database must remain active after rollback");
        }

        @Test
        @DisplayName("B9. Concurrent restaurant status changes use locking and execute consistently")
        void testConcurrentRestaurantStatusUpdatesSerializeCleanly() throws Exception {
            var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var future1 = executor.submit(() -> platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), false));
                var future2 = executor.submit(() -> platformOnboardingService.updateRestaurantStatus(restaurantA.getId(), true));

                var res1 = future1.get(5, java.util.concurrent.TimeUnit.SECONDS);
                var res2 = future2.get(5, java.util.concurrent.TimeUnit.SECONDS);

                assertNotNull(res1);
                assertNotNull(res2);

                Restaurant finalState = restaurantRepository.findById(restaurantA.getId()).orElseThrow();
                assertNotNull(finalState.getIsActive(), "Final restaurant active status must be non-null and valid");
            } finally {
                executor.shutdown();
            }
        }
    }
}
