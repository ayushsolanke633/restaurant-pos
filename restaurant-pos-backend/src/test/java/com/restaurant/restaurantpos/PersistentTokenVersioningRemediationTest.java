package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.RevocationStorageException;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RevokedTokenRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.JwtAuthenticationFilter;
import com.restaurant.restaurantpos.security.RestaurantWebSocketSessionManager;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.security.StompOutboundChannelInterceptor;
import com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.PlatformOnboardingService;
import com.restaurant.restaurantpos.service.TokenRevocationService;
import com.restaurant.restaurantpos.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import com.restaurant.restaurantpos.util.MigrationApprovalGate;
import com.restaurant.restaurantpos.util.MigrationApprovalGate.DatabaseState;
import com.restaurant.restaurantpos.util.MigrationApprovalGate.GateResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AnnaDhara P0-3 Stage 3K: Comprehensive Unit & Mock Verification Suite
 * Tests persistent token versioning remediation, strict claim parsing, locking semantics,
 * transaction boundaries, and HTTP/STOMP failure mode classification.
 */
public class PersistentTokenVersioningRemediationTest {

    private static final String TEST_SECRET = "super-secret-key-that-is-at-least-32-bytes-long-for-hmac-sha256";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        jwtService.setSecret(TEST_SECRET);
    }

    private String createRawToken(Map<String, Object> claims, String subject, String jti) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + 3600000L);

        return Jwts.builder()
                .claims(claims)
                .id(jti)
                .subject(subject)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // =========================================================================
    // 1. JWT CLAIM VALIDATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. Strict JWT Claim Validation Tests")
    class ClaimValidationTests {

        private CustomUserDetails tenantUser;
        private CustomUserDetails superAdminUser;

        @BeforeEach
        void initUsers() {
            tenantUser = new CustomUserDetails(
                    10L, 5L, "ADMIN", "admin@tenant.com", "hash", true,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), 1, 1
            );

            superAdminUser = new CustomUserDetails(
                    1L, null, "SUPER_ADMIN", "superadmin@platform.com", "hash", true,
                    List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")), 1, null
            );
        }

        @Test
        @DisplayName("Valid tenant token passes validation")
        void testValidTenantToken() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", 1);
            claims.put("tenantVersion", 1);

            String token = createRawToken(claims, "admin@tenant.com", UUID.randomUUID().toString());
            assertTrue(jwtService.isTokenValid(token, tenantUser));
        }

        @Test
        @DisplayName("Valid SUPER_ADMIN token passes validation without tenantVersion")
        void testValidSuperAdminToken() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 1L);
            claims.put("role", "SUPER_ADMIN");
            claims.put("tokenVersion", 1);

            String token = createRawToken(claims, "superadmin@platform.com", UUID.randomUUID().toString());
            assertTrue(jwtService.isTokenValid(token, superAdminUser));
        }

        @Test
        @DisplayName("Missing jti fails closed")
        void testMissingJtiFailsClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", 1);
            claims.put("tenantVersion", 1);

            String token = createRawToken(claims, "admin@tenant.com", null);
            assertFalse(jwtService.isTokenValid(token, tenantUser));
        }

        @Test
        @DisplayName("Malformed/non-UUID jti fails closed")
        void testMalformedJtiFailsClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", 1);
            claims.put("tenantVersion", 1);

            String token = createRawToken(claims, "admin@tenant.com", "not-a-valid-uuid-string");
            assertFalse(jwtService.isTokenValid(token, tenantUser));
        }

        @Test
        @DisplayName("Missing tenantVersion in tenant token fails closed (never defaults to 1)")
        void testMissingTenantVersionFailsClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", 1);
            // tenantVersion intentionally omitted!

            String token = createRawToken(claims, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(token, tenantUser),
                    "Token missing tenantVersion must strictly fail closed, even if tenant current version is 1");
        }

        @Test
        @DisplayName("Decimal/fractional tokenVersion fails closed (prevents truncation bypass)")
        void testDecimalTokenVersionFailsClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", 1.5);
            claims.put("tenantVersion", 1);

            String token = createRawToken(claims, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(token, tenantUser));
        }

        @Test
        @DisplayName("Decimal/fractional tenantVersion fails closed")
        void testDecimalTenantVersionFailsClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", 1);
            claims.put("tenantVersion", 1.9);

            String token = createRawToken(claims, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(token, tenantUser));
        }

        @Test
        @DisplayName("Negative or zero version claims fail closed")
        void testNegativeOrZeroClaimsFailClosed() {
            Map<String, Object> claimsZero = new HashMap<>();
            claimsZero.put("userId", 10L);
            claimsZero.put("restaurantId", 5L);
            claimsZero.put("role", "ADMIN");
            claimsZero.put("tokenVersion", 0);
            claimsZero.put("tenantVersion", 1);

            String tokenZero = createRawToken(claimsZero, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(tokenZero, tenantUser));

            Map<String, Object> claimsNeg = new HashMap<>();
            claimsNeg.put("userId", 10L);
            claimsNeg.put("restaurantId", 5L);
            claimsNeg.put("role", "ADMIN");
            claimsNeg.put("tokenVersion", 1);
            claimsNeg.put("tenantVersion", -1);

            String tokenNeg = createRawToken(claimsNeg, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(tokenNeg, tenantUser));
        }

        @Test
        @DisplayName("String-valued numeric claims fail closed")
        void testStringNumericClaimsFailClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", "1");
            claims.put("tenantVersion", "1");

            String token = createRawToken(claims, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(token, tenantUser));
        }

        @Test
        @DisplayName("SUPER_ADMIN token with injected tenantVersion fails closed")
        void testSuperAdminWithTenantVersionFailsClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 1L);
            claims.put("role", "SUPER_ADMIN");
            claims.put("tokenVersion", 1);
            claims.put("tenantVersion", 1); // Injected!

            String token = createRawToken(claims, "superadmin@platform.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(token, superAdminUser),
                    "SUPER_ADMIN token must NOT contain tenantVersion");
        }

        @Test
        @DisplayName("SUPER_ADMIN token with injected restaurantId fails closed")
        void testSuperAdminWithRestaurantIdFailsClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 1L);
            claims.put("restaurantId", 5L); // Injected!
            claims.put("role", "SUPER_ADMIN");
            claims.put("tokenVersion", 1);

            String token = createRawToken(claims, "superadmin@platform.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(token, superAdminUser),
                    "SUPER_ADMIN token must NOT contain restaurantId");
        }

        @Test
        @DisplayName("Tenant user token with mismatched tenantVersion fails closed")
        void testTenantVersionMismatchFailsClosed() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", 1);
            claims.put("tenantVersion", 1); // Token is version 1, but restaurant is now version 2

            CustomUserDetails updatedTenant = new CustomUserDetails(
                    10L, 5L, "ADMIN", "admin@tenant.com", "hash", true,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), 1, 2
            );

            String token = createRawToken(claims, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(token, updatedTenant),
                    "Token with pre-deactivation tenantVersion must be rejected");
        }

        @Test
        @DisplayName("Non-CustomUserDetails principal fails closed (SEC-3L-02)")
        void testUnexpectedPrincipalTypeRejected() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 10L);
            claims.put("restaurantId", 5L);
            claims.put("role", "ADMIN");
            claims.put("tokenVersion", 1);
            claims.put("tenantVersion", 1);

            String token = createRawToken(claims, "admin@tenant.com", UUID.randomUUID().toString());

            org.springframework.security.core.userdetails.User genericUser =
                    new org.springframework.security.core.userdetails.User(
                            "admin@tenant.com", "hash",
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    );

            assertFalse(jwtService.isTokenValid(token, genericUser),
                    "Any principal that is not CustomUserDetails must be rejected fail-closed");
        }

        @Test
        @DisplayName("Fractional or decimal userId and restaurantId fail closed (SEC-3L-03)")
        void testFractionalIdentityClaimsFailClosed() {
            // Fractional userId (10.5)
            Map<String, Object> claimsDecUser = new HashMap<>();
            claimsDecUser.put("userId", 10.5);
            claimsDecUser.put("restaurantId", 5L);
            claimsDecUser.put("role", "ADMIN");
            claimsDecUser.put("tokenVersion", 1);
            claimsDecUser.put("tenantVersion", 1);
            String tokenDecUser = createRawToken(claimsDecUser, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(tokenDecUser, tenantUser), "Fractional userId must fail closed");

            // Fractional restaurantId (5.7)
            Map<String, Object> claimsDecRest = new HashMap<>();
            claimsDecRest.put("userId", 10L);
            claimsDecRest.put("restaurantId", 5.7);
            claimsDecRest.put("role", "ADMIN");
            claimsDecRest.put("tokenVersion", 1);
            claimsDecRest.put("tenantVersion", 1);
            String tokenDecRest = createRawToken(claimsDecRest, "admin@tenant.com", UUID.randomUUID().toString());
            assertFalse(jwtService.isTokenValid(tokenDecRest, tenantUser), "Fractional restaurantId must fail closed");
        }

        @Test
        @DisplayName("Zero, negative, or string identity claims fail closed (SEC-3L-03)")
        void testInvalidRangeIdentityClaimsFailClosed() {
            // Zero userId
            Map<String, Object> claimsZeroUser = new HashMap<>();
            claimsZeroUser.put("userId", 0L);
            claimsZeroUser.put("restaurantId", 5L);
            claimsZeroUser.put("role", "ADMIN");
            claimsZeroUser.put("tokenVersion", 1);
            claimsZeroUser.put("tenantVersion", 1);
            assertFalse(jwtService.isTokenValid(createRawToken(claimsZeroUser, "admin@tenant.com", UUID.randomUUID().toString()), tenantUser));

            // Negative restaurantId
            Map<String, Object> claimsNegRest = new HashMap<>();
            claimsNegRest.put("userId", 10L);
            claimsNegRest.put("restaurantId", -5L);
            claimsNegRest.put("role", "ADMIN");
            claimsNegRest.put("tokenVersion", 1);
            claimsNegRest.put("tenantVersion", 1);
            assertFalse(jwtService.isTokenValid(createRawToken(claimsNegRest, "admin@tenant.com", UUID.randomUUID().toString()), tenantUser));

            // String userId ("10")
            Map<String, Object> claimsStrUser = new HashMap<>();
            claimsStrUser.put("userId", "10");
            claimsStrUser.put("restaurantId", 5L);
            claimsStrUser.put("role", "ADMIN");
            claimsStrUser.put("tokenVersion", 1);
            claimsStrUser.put("tenantVersion", 1);
            assertFalse(jwtService.isTokenValid(createRawToken(claimsStrUser, "admin@tenant.com", UUID.randomUUID().toString()), tenantUser));
        }
    }

    // =========================================================================
    // 2. LOCKING AND STATUS CHANGE TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Pessimistic Locking & Transaction Guarantees Tests")
    class LockingAndStatusTests {

        @Test
        @DisplayName("updateRestaurantStatus without active transaction throws IllegalStateException")
        void testUpdateRestaurantStatusRequiresTransaction() {
            RestaurantRepository restRepo = mock(RestaurantRepository.class);
            UserRepository userRepo = mock(UserRepository.class);
            PlatformOnboardingService service = new PlatformOnboardingService(
                    restRepo, null, userRepo, null, null, null, null, null
            );

            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());

            assertThrows(IllegalStateException.class, () -> service.updateRestaurantStatus(1L, false),
                    "updateRestaurantStatus must fail closed when called without an active transaction");
        }

        @Test
        @DisplayName("findByIdForUpdate throws EntityNotFoundException directly when row does not exist (no fallback query)")
        void testNoUnlockedFallbackOnMissingRow() {
            RestaurantRepository restRepo = mock(RestaurantRepository.class);
            UserRepository userRepo = mock(UserRepository.class);
            when(restRepo.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            // Notice: findById should NEVER be called as a fallback
            PlatformOnboardingService service = new PlatformOnboardingService(
                    restRepo, null, userRepo, null, null, null, null, null
            );

            // Simulate active transaction
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertThrows(EntityNotFoundException.class, () -> service.updateRestaurantStatus(999L, false));
                verify(restRepo, times(1)).findByIdForUpdate(999L);
                verify(restRepo, never()).findById(999L); // Confirm NO unlocked fallback!
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }

        @Test
        @DisplayName("UserService.createUser removes unlocked fallback and propagates EntityNotFoundException")
        void testUserServiceCreateUserNoFallback() {
            RestaurantRepository restRepo = mock(RestaurantRepository.class);
            when(restRepo.findByIdForUpdate(10L)).thenReturn(Optional.empty());

            com.restaurant.restaurantpos.security.SecurityUtils securityUtils = mock(com.restaurant.restaurantpos.security.SecurityUtils.class);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(10L);

            UserService userService = new UserService(
                    mock(UserRepository.class), restRepo, mock(com.restaurant.restaurantpos.repository.RoleRepository.class),
                    null, securityUtils
            );

            com.restaurant.restaurantpos.dto.UserRequest req = new com.restaurant.restaurantpos.dto.UserRequest();
            req.setRestaurantId(10L);

            assertThrows(EntityNotFoundException.class, () -> userService.createUser(req));
            verify(restRepo, times(1)).findByIdForUpdate(10L);
            verify(restRepo, never()).findById(10L); // Confirm NO unlocked fallback!
        }

        @Test
        @DisplayName("Deactivation increments persistent token_version; Reactivation preserves version")
        void testDeactivationIncrementsVersionReactivationPreserves() {
            RestaurantRepository restRepo = mock(RestaurantRepository.class);
            UserRepository userRepo = mock(UserRepository.class);
            when(userRepo.findFirstByRestaurantIdAndRole_NameIgnoreCase(anyLong(), anyString())).thenReturn(Optional.empty());
            when(userRepo.countByRestaurantId(anyLong())).thenReturn(0L);

            Restaurant restaurant = new Restaurant();
            restaurant.setId(5L);
            restaurant.setIsActive(true);
            restaurant.setTokenVersion(1);

            when(restRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(restaurant));
            when(restRepo.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PlatformOnboardingService service = new PlatformOnboardingService(
                    restRepo, null, userRepo, null, null, null, null, null
            );

            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();
            try {
                // 1. Deactivation
                service.updateRestaurantStatus(5L, false);
                assertFalse(restaurant.getIsActive());
                assertEquals(2, restaurant.getTokenVersion(), "Deactivation must increment persistent token_version to 2");

                // 2. Reactivation
                service.updateRestaurantStatus(5L, true);
                assertTrue(restaurant.getIsActive());
                assertEquals(2, restaurant.getTokenVersion(), "Reactivation must NOT decrement or change token_version");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }

        @Test
        @DisplayName("CustomUserDetailsService loads and hydrates persisted restaurant token_version")
        void testCustomUserDetailsServiceHydratesRestaurantTokenVersion() {
            UserRepository userRepo = mock(UserRepository.class);
            Restaurant restaurant = new Restaurant();
            restaurant.setId(42L);
            restaurant.setIsActive(true);
            restaurant.setTokenVersion(7); // Persisted restaurant version

            Role role = new Role();
            role.setName("ADMIN");

            User user = new User();
            user.setId(101L);
            user.setEmail("manager@bistrot.com");
            user.setPasswordHash("hash");
            user.setIsActive(true);
            user.setTokenVersion(3);
            user.setRestaurant(restaurant);
            user.setRole(role);

            when(userRepo.findByEmail("manager@bistrot.com")).thenReturn(Optional.of(user));

            CustomUserDetailsService service = new CustomUserDetailsService(userRepo);
            org.springframework.security.core.userdetails.UserDetails userDetails =
                    service.loadUserByUsername("manager@bistrot.com");

            assertTrue(userDetails instanceof CustomUserDetails);
            CustomUserDetails cud = (CustomUserDetails) userDetails;
            assertEquals(7, cud.getRestaurantTokenVersion(), "Persisted restaurant token_version must be hydrated");
            assertEquals(3, cud.getTokenVersion(), "User token_version must be hydrated");
            assertEquals(42L, cud.getRestaurantId(), "RestaurantId must be hydrated");
        }

        @Test
        @DisplayName("UserService.updateUser to KITCHEN role requires locked restaurant lookup without unlocked fallback")
        void testUserServiceUpdateUserKitchenRoleLockedRestaurantNoFallback() {
            UserRepository userRepo = mock(UserRepository.class);
            RestaurantRepository restRepo = mock(RestaurantRepository.class);
            com.restaurant.restaurantpos.repository.RoleRepository roleRepo = mock(com.restaurant.restaurantpos.repository.RoleRepository.class);
            com.restaurant.restaurantpos.security.SecurityUtils securityUtils = mock(com.restaurant.restaurantpos.security.SecurityUtils.class);

            Long restaurantId = 15L;
            when(securityUtils.getCurrentRestaurantId()).thenReturn(restaurantId);

            Restaurant rest = new Restaurant();
            rest.setId(restaurantId);
            rest.setIsActive(true);

            Role waiterRole = new Role();
            waiterRole.setId(2L);
            waiterRole.setName("WAITER");

            Role kitchenRole = new Role();
            kitchenRole.setId(3L);
            kitchenRole.setName("KITCHEN");

            User user = new User();
            user.setId(201L);
            user.setEmail("staff@restaurant.com");
            user.setRestaurant(rest);
            user.setRole(waiterRole);

            when(userRepo.findById(201L)).thenReturn(Optional.of(user));
            when(roleRepo.findById(3L)).thenReturn(Optional.of(kitchenRole));

            // findByIdForUpdate returns empty to test lock failure / missing row
            when(restRepo.findByIdForUpdate(restaurantId)).thenReturn(Optional.empty());

            UserService userService = new UserService(userRepo, restRepo, roleRepo, null, securityUtils);

            com.restaurant.restaurantpos.dto.UserRequest req = new com.restaurant.restaurantpos.dto.UserRequest();
            req.setRoleId(3L); // Change to KITCHEN

            assertThrows(EntityNotFoundException.class, () -> userService.updateUser(201L, req));
            verify(restRepo, times(1)).findByIdForUpdate(restaurantId);
            verify(restRepo, never()).findById(restaurantId); // Confirm NO unlocked fallback!
        }
    }

    // =========================================================================
    // 3. HTTP & STOMP INFRASTRUCTURE OUTAGE TESTS
    // =========================================================================
    @Nested
    @DisplayName("3. HTTP and STOMP Infrastructure Failure Classification Tests")
    class InfrastructureOutageTests {

        @Test
        @DisplayName("TokenRevocationService propagates DataAccessException on database outage (does NOT report revoked)")
        void testTokenRevocationServicePropagatesDatabaseOutage() {
            RevokedTokenRepository repo = mock(RevokedTokenRepository.class);
            String validJti = UUID.randomUUID().toString();
            when(repo.existsByJti(validJti)).thenThrow(new DataAccessResourceFailureException("PostgreSQL connection refused"));

            TokenRevocationService revocationService = new TokenRevocationService(repo, jwtService);

            assertThrows(DataAccessResourceFailureException.class, () -> revocationService.isRevoked(validJti),
                    "TokenRevocationService must propagate database errors instead of treating tokens as revoked");
        }

        @Test
        @DisplayName("JwtAuthenticationFilter returns HTTP 503 on database failure")
        void testJwtAuthenticationFilterReturns503OnDatabaseOutage() throws Exception {
            JwtService mockJwtService = mock(JwtService.class);
            CustomUserDetailsService mockUserDetailsService = mock(CustomUserDetailsService.class);
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);

            String testJwt = "header.payload.signature";
            when(mockJwtService.extractJti(testJwt)).thenReturn(UUID.randomUUID().toString());
            when(mockRevocationService.isRevoked(anyString())).thenThrow(new DataAccessResourceFailureException("Database outage"));

            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                    mockJwtService, mockUserDetailsService, null, mockRevocationService
            );

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testJwt);

            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter responseBody = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

            filter.doFilter(request, response, mock(FilterChain.class));

            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertTrue(responseBody.toString().contains("Service Unavailable"), "Response must indicate 503 Service Unavailable");
            assertFalse(responseBody.toString().contains("PostgreSQL"), "No internal SQL or driver details must be leaked");
        }

        @Test
        @DisplayName("JwtAuthenticationFilter returns HTTP 403 on deactivated account")
        void testJwtAuthenticationFilterReturns403OnDeactivation() throws Exception {
            JwtService mockJwtService = mock(JwtService.class);
            CustomUserDetailsService mockUserDetailsService = mock(CustomUserDetailsService.class);
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);

            String testJwt = "header.payload.signature";
            when(mockJwtService.extractJti(testJwt)).thenReturn(UUID.randomUUID().toString());
            when(mockRevocationService.isRevoked(anyString())).thenReturn(false);
            when(mockJwtService.extractUsername(testJwt)).thenReturn("deactivated@tenant.com");
            when(mockUserDetailsService.loadUserByUsername("deactivated@tenant.com"))
                    .thenThrow(new DisabledException("Restaurant is deactivated"));

            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                    mockJwtService, mockUserDetailsService, null, mockRevocationService
            );

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + testJwt);

            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter responseBody = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

            filter.doFilter(request, response, mock(FilterChain.class));

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            assertTrue(responseBody.toString().contains("Restaurant is deactivated"));
        }

        @Test
        @DisplayName("STOMP CONNECT throws MessageDeliveryException on database outage (clean error frame)")
        void testStompConnectDatabaseOutageRejection() {
            JwtService mockJwtService = mock(JwtService.class);
            CustomUserDetailsService mockUserDetailsService = mock(CustomUserDetailsService.class);
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);

            String testToken = "valid.jwt.token";
            when(mockJwtService.extractJti(testToken)).thenReturn(UUID.randomUUID().toString());
            when(mockRevocationService.isRevoked(anyString())).thenThrow(new DataAccessResourceFailureException("DB connection pool exhausted"));

            WebSocketSessionRevocationManager mockSessionManager = mock(WebSocketSessionRevocationManager.class);
            StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
                    mockJwtService, mockUserDetailsService, null, null, mockRevocationService, mockSessionManager
            );

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.addNativeHeader("Authorization", "Bearer " + testToken);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            MessageDeliveryException ex = assertThrows(MessageDeliveryException.class,
                    () -> interceptor.preSend(message, mock(MessageChannel.class)));
            assertEquals("Service temporarily unavailable", ex.getMessage(),
                    "STOMP CONNECT must be rejected with clean generic message on DB outage");
        }

        @Test
        @DisplayName("STOMP SUBSCRIBE on database outage does NOT falsely revoke session in memory")
        void testStompSubscribeDatabaseOutagePreservesSession() {
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);
            WebSocketSessionRevocationManager sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);

            String sessionId = "ws-active-kitchen-session";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 5L);

            when(mockRevocationService.isRevoked(jti))
                    .thenThrow(new DataAccessResourceFailureException("Temporary network blip to DB"));

            StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
                    jwtService, mock(CustomUserDetailsService.class), null, null,
                    mockRevocationService, sessionRevocationManager
            );

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setSessionId(sessionId);
            accessor.setDestination("/topic/restaurants/5/kitchen/orders");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));

            // CRITICAL: Ensure the session was NOT permanently marked as revoked in memory!
            assertFalse(sessionRevocationManager.isSessionExplicitlyRevoked(sessionId),
                    "Active session must NOT be falsely marked revoked due to a transient database outage");
            doReturn(false).when(mockRevocationService).isRevoked(jti);
            assertFalse(sessionRevocationManager.isSessionRevoked(sessionId));
        }

        @Test
        @DisplayName("STOMP outbound delivers normally when session and token are active and valid")
        void testStompOutboundNormalDelivery() {
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);
            WebSocketSessionRevocationManager sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);

            String sessionId = "ws-normal-outbound";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 5L);

            when(mockRevocationService.isRevoked(jti)).thenReturn(false);

            StompOutboundChannelInterceptor interceptor =
                    new StompOutboundChannelInterceptor(sessionRevocationManager, mockRevocationService);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            assertNotNull(result, "Normal outbound message must be delivered");
        }

        @Test
        @DisplayName("STOMP outbound suppresses delivery when session is revoked")
        void testStompOutboundRevokedSessionSuppressed() {
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);
            WebSocketSessionRevocationManager sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);
            String sessionId = "ws-revoked-session";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 5L);
            sessionRevocationManager.invalidateSessionsForRestaurant(5L); // Revoke session

            StompOutboundChannelInterceptor interceptor =
                    new StompOutboundChannelInterceptor(sessionRevocationManager, mockRevocationService);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            assertNull(result, "Outbound message must be suppressed for revoked session");
        }

        @Test
        @DisplayName("STOMP outbound suppresses delivery when token jti is revoked")
        void testStompOutboundRevokedTokenSuppressed() {
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);
            WebSocketSessionRevocationManager sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);

            String sessionId = "ws-revoked-token-session";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 5L);

            when(mockRevocationService.isRevoked(jti)).thenReturn(true);

            StompOutboundChannelInterceptor interceptor =
                    new StompOutboundChannelInterceptor(sessionRevocationManager, mockRevocationService);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            assertNull(result, "Outbound message must be suppressed when token is revoked");
        }

        @Test
        @DisplayName("STOMP outbound suppresses delivery fail-closed on database outage (SEC-3L-01)")
        void testStompOutboundDatabaseFailureSuppressesDeliveryFailClosed() {
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);
            WebSocketSessionRevocationManager sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);

            String sessionId = "ws-db-outage-outbound";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 5L);

            when(mockRevocationService.isRevoked(jti))
                    .thenThrow(new DataAccessResourceFailureException("PostgreSQL connection lost during outbound check"));

            StompOutboundChannelInterceptor interceptor =
                    new StompOutboundChannelInterceptor(sessionRevocationManager, mockRevocationService);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            assertNull(result, "Outbound message must be suppressed fail-closed when database is down");

            // CRITICAL: Ensure the session was NOT permanently marked revoked!
            assertFalse(sessionRevocationManager.isSessionExplicitlyRevoked(sessionId),
                    "Session must NOT be permanently marked revoked due to a transient database outage");
            doReturn(false).when(mockRevocationService).isRevoked(jti);
            assertFalse(sessionRevocationManager.isSessionRevoked(sessionId));
        }

        @Test
        @DisplayName("STOMP outbound sanitizes log output: never logs SQL error details or database credentials")
        void testStompOutboundSanitizesDatabaseErrorLogs() {
            Logger interceptorLogger = (Logger) LoggerFactory.getLogger(StompOutboundChannelInterceptor.class);
            ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
            listAppender.start();
            interceptorLogger.addAppender(listAppender);

            try {
                TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);
                WebSocketSessionRevocationManager sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);

                String sessionId = "ws-sensitive-log-check";
                String jti = UUID.randomUUID().toString();
                sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 5L);

                String sensitiveSqlMessage = "FATAL: password authentication failed for user 'postgres' at host 10.0.0.1:5432; SELECT * FROM secret_table WHERE jti = 'secret-jti'";
                when(mockRevocationService.isRevoked(jti))
                        .thenThrow(new DataAccessResourceFailureException(sensitiveSqlMessage));

                StompOutboundChannelInterceptor interceptor =
                        new StompOutboundChannelInterceptor(sessionRevocationManager, mockRevocationService);

                StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
                accessor.setSessionId(sessionId);
                Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

                Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
                assertNull(result, "Outbound message must be suppressed fail-closed");
                assertFalse(sessionRevocationManager.isSessionExplicitlyRevoked(sessionId), "Session must not be permanently revoked");
                doReturn(false).when(mockRevocationService).isRevoked(jti);
                assertFalse(sessionRevocationManager.isSessionRevoked(sessionId));

                // Verify logs are completely sanitized
                assertFalse(listAppender.list.isEmpty(), "At least one log event must be generated");
                for (ILoggingEvent event : listAppender.list) {
                    String formatted = event.getFormattedMessage();
                    assertFalse(formatted.contains("password"), "Must not leak 'password'");
                    assertFalse(formatted.contains("postgres"), "Must not leak database username");
                    assertFalse(formatted.contains("10.0.0.1"), "Must not leak database host/ip");
                    assertFalse(formatted.contains("secret_table"), "Must not leak table name or SQL fragments");
                    assertFalse(formatted.contains("secret-jti"), "Must not leak sensitive tokens or query parameters");
                }

                // Verify safe structured fields are logged
                boolean hasSafeFields = listAppender.list.stream().anyMatch(e ->
                        e.getFormattedMessage().contains("operation=checkOutboundRevocation") &&
                        e.getFormattedMessage().contains("errorClass=DataAccessResourceFailureException")
                );
                assertTrue(hasSafeFields, "Log output must include safe operation and errorClass structured fields");
            } finally {
                interceptorLogger.detachAppender(listAppender);
                listAppender.stop();
            }
        }

        @Test
        @DisplayName("WebSocketSessionRevocationManager sanitizes log output: never logs SQL error details")
        void testWebSocketSessionRevocationManagerSanitizesLogs() {
            Logger managerLogger = (Logger) LoggerFactory.getLogger(WebSocketSessionRevocationManager.class);
            ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
            listAppender.start();
            managerLogger.addAppender(listAppender);

            try {
                TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);
                WebSocketSessionRevocationManager sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);

                String sessionId = "ws-mgr-log-check";
                String jti = UUID.randomUUID().toString();
                sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 5L);

                String sensitiveDbMessage = "Connection refused to jdbc:postgresql://secure-db:5432/pos; password=adminsecret";
                when(mockRevocationService.isRevoked(jti))
                        .thenThrow(new DataAccessResourceFailureException(sensitiveDbMessage));

                WebSocketSessionRevocationManager mgrWithService =
                        new WebSocketSessionRevocationManager(mockRevocationService);
                mgrWithService.bindSessionToToken(sessionId, jti, 10L, 5L);

                assertThrows(DataAccessResourceFailureException.class, () ->
                        mgrWithService.isSessionRevoked(sessionId)
                );

                assertFalse(listAppender.list.isEmpty(), "Log event must be generated on storage failure");
                for (ILoggingEvent event : listAppender.list) {
                    String formatted = event.getFormattedMessage();
                    assertFalse(formatted.contains("adminsecret"), "Must not leak password");
                    assertFalse(formatted.contains("secure-db"), "Must not leak host");
                    assertFalse(formatted.contains("jdbc:postgresql"), "Must not leak JDBC URL");
                }

                boolean hasSafeFields = listAppender.list.stream().anyMatch(e ->
                        e.getFormattedMessage().contains("operation=checkSessionRevocation") &&
                        e.getFormattedMessage().contains("errorClass=DataAccessResourceFailureException")
                );
                assertTrue(hasSafeFields, "Log output must include safe operation and errorClass");
            } finally {
                managerLogger.detachAppender(listAppender);
                listAppender.stop();
            }
        }

        @Test
        @DisplayName("TokenRevocationService sanitizes log output: never logs SQL messages or credentials")
        void testTokenRevocationServiceSanitizesLogs() {
            Logger serviceLogger = (Logger) LoggerFactory.getLogger(TokenRevocationService.class);
            ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
            listAppender.start();
            serviceLogger.addAppender(listAppender);

            try {
                RevokedTokenRepository repo = mock(RevokedTokenRepository.class);
                String validJti = UUID.randomUUID().toString();
                String sensitiveMessage = "PSQLException: Connection reset by peer at 172.28.0.2:5432 (user: restaurant_app, pass: db_secret_123)";
                when(repo.existsByJti(validJti))
                        .thenThrow(new DataAccessResourceFailureException(sensitiveMessage));

                TokenRevocationService service = new TokenRevocationService(repo, jwtService);

                assertThrows(DataAccessResourceFailureException.class, () -> service.isRevoked(validJti));

                assertFalse(listAppender.list.isEmpty(), "Log event must be emitted");
                for (ILoggingEvent event : listAppender.list) {
                    String formatted = event.getFormattedMessage();
                    assertFalse(formatted.contains("db_secret_123"), "Must not leak password");
                    assertFalse(formatted.contains("172.28.0.2"), "Must not leak internal IP");
                    assertFalse(formatted.contains("PSQLException"), "Must not leak driver internal string");
                }

                boolean hasSafeFields = listAppender.list.stream().anyMatch(e ->
                        e.getFormattedMessage().contains("operation=checkRevocation") &&
                        e.getFormattedMessage().contains("errorClass=DataAccessResourceFailureException")
                );
                assertTrue(hasSafeFields, "Log output must include safe operation and errorClass");
            } finally {
                serviceLogger.detachAppender(listAppender);
                listAppender.stop();
            }
        }

        @Test
        @DisplayName("STOMP outbound catches unexpected exceptions fail-closed without delivery bypass")
        void testStompOutboundCatchesUnexpectedExceptionFailClosed() {
            TokenRevocationService mockRevocationService = mock(TokenRevocationService.class);
            WebSocketSessionRevocationManager sessionRevocationManager = new WebSocketSessionRevocationManager(mockRevocationService);

            String sessionId = "ws-unexpected-error-session";
            String jti = UUID.randomUUID().toString();
            sessionRevocationManager.bindSessionToToken(sessionId, jti, 10L, 5L);

            when(mockRevocationService.isRevoked(jti))
                    .thenThrow(new NullPointerException("Simulated unexpected null pointer in revocation layer"));

            StompOutboundChannelInterceptor interceptor =
                    new StompOutboundChannelInterceptor(sessionRevocationManager, mockRevocationService);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            accessor.setSessionId(sessionId);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
            assertNull(result, "Unexpected exception must fail-closed and suppress outbound delivery");
            assertFalse(sessionRevocationManager.isSessionExplicitlyRevoked(sessionId),
                    "Session must not be permanently marked revoked on transient unexpected exception");
            doReturn(false).when(mockRevocationService).isRevoked(jti);
            assertFalse(sessionRevocationManager.isSessionRevoked(sessionId));
        }
    }

    // =========================================================================
    // 4. MIGRATION DRAFT CONTRACT TESTS
    // =========================================================================
    @Nested
    @DisplayName("4. Migration Draft Contract & Safety Verification Tests")
    class MigrationDraftContractTests {

        private static final Path MIGRATION_DIR = Paths.get("src", "main", "resources", "db", "migration");
        private static final String V1_7_0_FILENAME = "V1_7_0__add_token_version_to_restaurants.sql";

        @Test
        @DisplayName("V1_7_0 migration file exists and adheres to naming convention")
        void testV1_7_0FileConventionAndExpectedSchema() throws Exception {
            Path v1_7_0Path = MIGRATION_DIR.resolve(V1_7_0_FILENAME);
            assertTrue(Files.exists(v1_7_0Path), "V1_7_0 migration file must exist at " + v1_7_0Path);

            assertTrue(V1_7_0_FILENAME.matches("^V1_7_0__[a-z0-9_]+\\.sql$"),
                    "Filename must strictly match Flyway versioning pattern V1_7_0__description.sql");

            String sql = Files.readString(v1_7_0Path, StandardCharsets.UTF_8);
            assertNotNull(sql);
            assertFalse(sql.isBlank(), "Migration SQL must not be blank");

            // Verify table and column specifications
            assertTrue(sql.contains("ALTER TABLE restaurants"), "SQL must alter restaurants table");
            assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS token_version"), "SQL must add token_version column idempotently");
            assertTrue(sql.contains("INTEGER NOT NULL DEFAULT 1"), "token_version must be INTEGER NOT NULL DEFAULT 1");
        }

        @Test
        @DisplayName("Unapproved migration version 1.9.0 is strictly rejected by Gate allowlist")
        void testUnapprovedMigrationVersionRejectedByGateAllowlist() {
            // Confirm intended policy: 1.6.0, 1.7.0, and 1.8.0 are approved; draft/future versions are unapproved
            assertTrue(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.6.0"),
                    "Version 1.6.0 must be in approved allowlist");
            assertTrue(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.7.0"),
                    "Version 1.7.0 must be in approved allowlist");
            assertTrue(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.8.0"),
                    "Version 1.8.0 must be in approved allowlist");
            assertFalse(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.1.0"),
                    "Draft version 1.1.0 must remain unapproved in allowlist");
            assertFalse(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.9.0"),
                    "Future version 1.9.0 must remain unapproved in allowlist");

            // Illustrative test double fixture simulating pending unapproved version 1.9.0
            String pendingOutputWithUnapprovedV1_9_0 =
                    "+-----------+---------+-----------------------------------+------+---------------------+---------+\n" +
                    "| Category  | Version | Description                       | Type | Installed On        | State   |\n" +
                    "+-----------+---------+-----------------------------------+------+---------------------+---------+\n" +
                    "| Versioned | 1.0.0   | baseline schema                   | SQL  |                     | Success |\n" +
                    "| Versioned | 1.7.0   | add token version to restaurants  | SQL  |                     | Success |\n" +
                    "| Versioned | 1.9.0   | unapproved future migration       | SQL  |                     | Pending |\n" +
                    "+-----------+---------+-----------------------------------+------+---------------------+---------+\n";

            GateResult result = MigrationApprovalGate.evaluate(
                    "jdbc:postgresql://localhost:5432/restaurant_pos",
                    "restaurant_app",
                    "valid_pass",
                    DatabaseState.VERSIONED_WITH_HISTORY,
                    pendingOutputWithUnapprovedV1_9_0,
                    "1.9.0",
                    MigrationApprovalGate.GATE_AUTHORIZATION_TOKEN
            );

            assertFalse(result.isAuthorized(), "Gate must strictly reject pending migration 1.9.0");
            assertTrue(result.getMessage().contains("1.9.0 is not in the approved allowlist"),
                    "Rejection message must cite unapproved version 1.9.0");
        }

        @Test
        @DisplayName("V1_7_0 ordering and compatibility with preceding migrations")
        void testV1_7_0OrderingAndCompatibilityWithPrecedingMigrations() throws Exception {
            // Verify V1_0_0 exists and defines restaurants table
            Path v1_0_0Path = MIGRATION_DIR.resolve("V1_0_0__existing_schema.sql");
            assertTrue(Files.exists(v1_0_0Path), "V1_0_0 must exist as baseline schema");
            String v1_0_0Sql = Files.readString(v1_0_0Path, StandardCharsets.UTF_8);
            assertTrue(v1_0_0Sql.contains("CREATE TABLE") && v1_0_0Sql.contains("restaurants"),
                    "V1_0_0 must define restaurants table");

            // Verify version ordering
            assertTrue(MigrationApprovalGate.compareVersions("1.0.0", "1.7.0") < 0);
            assertTrue(MigrationApprovalGate.compareVersions("1.5.0", "1.7.0") < 0);
            assertTrue(MigrationApprovalGate.compareVersions("1.6.0", "1.7.0") < 0);

            // Read V1_7_0 content to verify no external table coupling
            Path v1_7_0Path = MIGRATION_DIR.resolve(V1_7_0_FILENAME);
            String v1_7_0Sql = Files.readString(v1_7_0Path, StandardCharsets.UTF_8);

            // Must NOT depend on revoked_tokens or users.token_version
            assertFalse(v1_7_0Sql.contains("revoked_tokens"), "V1_7_0 must not reference revoked_tokens");
            assertFalse(v1_7_0Sql.contains("users"), "V1_7_0 must not reference users table");
        }
    }
}
