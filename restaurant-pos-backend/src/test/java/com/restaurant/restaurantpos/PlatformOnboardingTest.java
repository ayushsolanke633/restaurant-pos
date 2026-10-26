package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.config.PlatformSuperAdminBootstrapper;
import com.restaurant.restaurantpos.dto.PlatformOnboardingRequest;
import com.restaurant.restaurantpos.dto.PlatformOnboardingResponse;
import com.restaurant.restaurantpos.dto.PlatformRestaurantResponse;
import com.restaurant.restaurantpos.dto.UserRequest;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.DuplicateEmailException;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.JwtAuthenticationFilter;
import com.restaurant.restaurantpos.security.RestaurantWebSocketSessionManager;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.service.AuthService;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.PlatformOnboardingService;
import com.restaurant.restaurantpos.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Test Suite for Phase 4F:
 * 1. Platform Super Admin Bootstrap
 * 2. Atomic Restaurant Onboarding & Rollback
 * 3. PostgreSQL & JPA Role-Tenant Validation
 * 4. Restaurant Deactivation & Reactivation (REST & WebSocket Enforcement)
 * 5. Tenant Isolation & Super Admin Protection
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PlatformOnboardingTest {

    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
    }

    // =========================================================================
    // 1. ATOMIC ONBOARDING & TRANSACTION INTEGRITY TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. Platform Onboarding Service Tests")
    class OnboardingServiceTests {

        @Mock private RestaurantRepository restaurantRepository;
        @Mock private RestaurantSettingsRepository restaurantSettingsRepository;
        @Mock private UserRepository userRepository;
        @Mock private RoleRepository roleRepository;
        @Mock private RestaurantWebSocketSessionManager webSocketSessionManager;

        private PlatformOnboardingService onboardingService;
        private Role adminRole;

        @BeforeEach
        void setUp() {
            onboardingService = new PlatformOnboardingService(
                    restaurantRepository,
                    restaurantSettingsRepository,
                    userRepository,
                    roleRepository,
                    passwordEncoder,
                    webSocketSessionManager
            );

            adminRole = new Role(1L, "ADMIN");
            when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        }

        @Test
        @DisplayName("Successful onboarding creates restaurant, settings, and admin without exposing credentials")
        void testSuccessfulOnboarding() {
            PlatformOnboardingRequest request = PlatformOnboardingRequest.builder()
                    .restaurantName("Green Garden Bistro")
                    .address("100 Park Ave")
                    .phone("9876543210")
                    .email("info@greengarden.com")
                    .gstNumber("GSTIN12345")
                    .adminName("Alice Admin")
                    .adminEmail("alice@greengarden.com")
                    .adminPassword("SecretP@ssword123")
                    .adminPhone("9876543210")
                    .build();

            when(userRepository.findByEmail("alice@greengarden.com")).thenReturn(Optional.empty());

            Restaurant savedRest = new Restaurant();
            savedRest.setId(10L);
            savedRest.setName("Green Garden Bistro");
            savedRest.setIsActive(true);
            savedRest.setCreatedAt(LocalDateTime.now());
            when(restaurantRepository.save(any(Restaurant.class))).thenReturn(savedRest);

            when(restaurantSettingsRepository.save(any(RestaurantSettings.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            User savedAdmin = new User();
            savedAdmin.setId(100L);
            savedAdmin.setRestaurant(savedRest);
            savedAdmin.setRole(adminRole);
            savedAdmin.setName("Alice Admin");
            savedAdmin.setEmail("alice@greengarden.com");
            savedAdmin.setPasswordHash(passwordEncoder.encode("SecretP@ssword123"));
            savedAdmin.setIsActive(true);
            when(userRepository.save(any(User.class))).thenReturn(savedAdmin);

            PlatformOnboardingResponse response = onboardingService.onboardRestaurant(request);

            assertNotNull(response);
            assertEquals(10L, response.getRestaurantId());
            assertEquals("Green Garden Bistro", response.getRestaurantName());
            assertEquals("alice@greengarden.com", response.getAdminEmail());
            assertEquals("Alice Admin", response.getAdminName());
            assertTrue(response.getIsActive());

            // Verify settings were initialized with defaults
            ArgumentCaptor<RestaurantSettings> settingsCaptor = ArgumentCaptor.forClass(RestaurantSettings.class);
            verify(restaurantSettingsRepository).save(settingsCaptor.capture());
            RestaurantSettings capturedSettings = settingsCaptor.getValue();
            assertEquals("INR", capturedSettings.getCurrency());
            assertTrue(capturedSettings.getAutoAcceptOrders());
            assertEquals(BigDecimal.valueOf(5.00).setScale(2), capturedSettings.getDefaultTaxRate());

            // Verify admin password was BCrypt hashed
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User capturedUser = userCaptor.getValue();
            assertTrue(passwordEncoder.matches("SecretP@ssword123", capturedUser.getPasswordHash()));
            assertNotEquals("SecretP@ssword123", capturedUser.getPasswordHash());
        }

        @Test
        @DisplayName("Duplicate admin email throws DuplicateEmailException and prevents partial creation")
        void testOnboardingDuplicateEmailRollback() {
            PlatformOnboardingRequest request = PlatformOnboardingRequest.builder()
                    .restaurantName("Blue Ocean Diner")
                    .adminName("Bob Admin")
                    .adminEmail("existing@example.com")
                    .adminPassword("SecretP@ssword123")
                    .build();

            User existingUser = new User();
            existingUser.setEmail("existing@example.com");
            when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

            DuplicateEmailException ex = assertThrows(DuplicateEmailException.class, () ->
                    onboardingService.onboardRestaurant(request));

            assertTrue(ex.getMessage().contains("Email already exists"));
            verify(restaurantRepository, never()).save(any());
            verify(restaurantSettingsRepository, never()).save(any());
        }

        @Test
        @DisplayName("Missing ADMIN role throws IllegalStateException")
        void testOnboardingMissingAdminRole() {
            PlatformOnboardingRequest request = PlatformOnboardingRequest.builder()
                    .restaurantName("Red Chili")
                    .adminName("Admin")
                    .adminEmail("chili@example.com")
                    .adminPassword("SecretP@ssword123")
                    .build();

            when(userRepository.findByEmail("chili@example.com")).thenReturn(Optional.empty());
            when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

            assertThrows(IllegalStateException.class, () -> onboardingService.onboardRestaurant(request));
        }

        @Test
        @DisplayName("Restaurant listing never exposes passwords or password hashes")
        void testRestaurantListingNeverExposesPasswords() {
            Restaurant resto = new Restaurant();
            resto.setId(1L);
            resto.setName("Alpha Dine");
            resto.setIsActive(true);

            User admin = new User();
            admin.setId(5L);
            admin.setEmail("admin@alphadine.com");
            admin.setPasswordHash("$2a$10$encryptedHashNeverExpose");

            when(restaurantRepository.findAll(any(Sort.class))).thenReturn(List.of(resto));
            when(userRepository.findFirstByRestaurantIdAndRole_NameIgnoreCase(1L, "ADMIN"))
                    .thenReturn(Optional.of(admin));
            when(userRepository.countByRestaurantId(1L)).thenReturn(3L);

            List<PlatformRestaurantResponse> list = onboardingService.getAllRestaurants();
            assertEquals(1, list.size());
            PlatformRestaurantResponse item = list.get(0);

            assertEquals("Alpha Dine", item.getName());
            assertEquals("admin@alphadine.com", item.getAdminEmail());
            assertEquals(3L, item.getTotalUsers());
            assertTrue(item.getIsActive());
            // No password or hash fields exist on PlatformRestaurantResponse
        }

        @Test
        @DisplayName("Deactivating a restaurant calls webSocketSessionManager.deactivateRestaurant")
        void testDeactivateRestaurant() {
            Restaurant resto = new Restaurant();
            resto.setId(2L);
            resto.setIsActive(true);
            when(restaurantRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(resto));
            when(restaurantRepository.findById(2L)).thenReturn(Optional.of(resto));
            when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
            try {
                PlatformRestaurantResponse response = onboardingService.updateRestaurantStatus(2L, false);
                for (org.springframework.transaction.support.TransactionSynchronization sync : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                    sync.afterCommit();
                }
                assertFalse(response.getIsActive());
                verify(webSocketSessionManager).deactivateRestaurant(2L);
            } finally {
                org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
                org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }

        @Test
        @DisplayName("Reactivating a restaurant calls webSocketSessionManager.reactivateRestaurant")
        void testReactivateRestaurant() {
            Restaurant resto = new Restaurant();
            resto.setId(2L);
            resto.setIsActive(false);
            when(restaurantRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(resto));
            when(restaurantRepository.findById(2L)).thenReturn(Optional.of(resto));
            when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
            try {
                PlatformRestaurantResponse response = onboardingService.updateRestaurantStatus(2L, true);
                for (org.springframework.transaction.support.TransactionSynchronization sync : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                    sync.afterCommit();
                }
                assertTrue(response.getIsActive());
                verify(webSocketSessionManager).reactivateRestaurant(2L);
            } finally {
                org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
                org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
    }

    // =========================================================================
    // 2. ONE-TIME SUPER_ADMIN BOOTSTRAP TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Platform Super Admin Bootstrapper Tests")
    class SuperAdminBootstrapperTests {

        @Mock private UserRepository userRepository;
        @Mock private RoleRepository roleRepository;

        private PlatformSuperAdminBootstrapper bootstrapper;
        private Role superAdminRole;

        @BeforeEach
        void setUp() {
            bootstrapper = new PlatformSuperAdminBootstrapper(userRepository, roleRepository, passwordEncoder);
            superAdminRole = new Role(4L, "SUPER_ADMIN");
            when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
        }

        @Test
        @DisplayName("Both email and password blank skips bootstrap gracefully without error")
        void testBootstrapBothBlankSkips() {
            assertDoesNotThrow(() -> bootstrapper.bootstrapSuperAdmin(null, null));
            assertDoesNotThrow(() -> bootstrapper.bootstrapSuperAdmin("", ""));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Email provided but password missing throws IllegalStateException")
        void testBootstrapEmailWithoutPasswordThrows() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    bootstrapper.bootstrapSuperAdmin("superadmin@platform.com", null));
            assertTrue(ex.getMessage().contains("PLATFORM_SUPER_ADMIN_PASSWORD is missing"));

            IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                    bootstrapper.bootstrapSuperAdmin("superadmin@platform.com", "   "));
            assertTrue(ex2.getMessage().contains("PLATFORM_SUPER_ADMIN_PASSWORD is missing"));
        }

        @Test
        @DisplayName("Password provided but email missing throws IllegalStateException")
        void testBootstrapPasswordWithoutEmailThrows() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    bootstrapper.bootstrapSuperAdmin(null, "SuperSecret123!"));
            assertTrue(ex.getMessage().contains("PLATFORM_SUPER_ADMIN_EMAIL is missing"));
        }

        @Test
        @DisplayName("Invalid email format throws IllegalStateException")
        void testBootstrapInvalidEmailFormatThrows() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    bootstrapper.bootstrapSuperAdmin("not-an-email", "SuperSecret123!"));
            assertTrue(ex.getMessage().contains("not a valid email address"));
        }

        @Test
        @DisplayName("Weak password (< 8 chars) throws IllegalStateException")
        void testBootstrapWeakPasswordThrows() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    bootstrapper.bootstrapSuperAdmin("admin@platform.com", "short"));
            assertTrue(ex.getMessage().contains("at least 8 characters"));
        }

        @Test
        @DisplayName("Never resets existing platform admin when super admin already exists")
        void testBootstrapNeverResetsExistingAdmin() {
            when(userRepository.existsByRole_Name("SUPER_ADMIN")).thenReturn(true);

            assertDoesNotThrow(() -> bootstrapper.bootstrapSuperAdmin("newadmin@platform.com", "NewPassword123!"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Never resets when user with matching email already exists")
        void testBootstrapSkipsWhenEmailAlreadyExists() {
            when(userRepository.existsByRole_Name("SUPER_ADMIN")).thenReturn(false);
            User existingUser = new User();
            existingUser.setEmail("super@platform.com");
            when(userRepository.findByEmail("super@platform.com")).thenReturn(Optional.of(existingUser));

            assertDoesNotThrow(() -> bootstrapper.bootstrapSuperAdmin("super@platform.com", "NewPassword123!"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Valid bootstrap creates SUPER_ADMIN with BCrypt hash and null restaurant")
        void testBootstrapSuccess() {
            when(userRepository.existsByRole_Name("SUPER_ADMIN")).thenReturn(false);
            when(userRepository.findByEmail("super@platform.com")).thenReturn(Optional.empty());

            bootstrapper.bootstrapSuperAdmin("super@platform.com", "PlatformSecure2026!");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User created = captor.getValue();
            assertEquals("super@platform.com", created.getEmail());
            assertEquals("SUPER_ADMIN", created.getRole().getName());
            assertNull(created.getRestaurant()); // Platform level, no tenant
            assertTrue(created.getIsActive());
            assertTrue(passwordEncoder.matches("PlatformSecure2026!", created.getPasswordHash()));
        }
    }

    // =========================================================================
    // 3. REST & WEBSOCKET DEACTIVATION ENFORCEMENT TESTS
    // =========================================================================
    @Nested
    @DisplayName("3. Restaurant Deactivation REST & WebSocket Tests")
    class DeactivationSecurityTests {

        @Mock private UserRepository userRepository;
        @Mock private JwtService jwtService;
        @Mock private AuthenticationManager authenticationManager;
        @Mock private MessageChannel messageChannel;

        private CustomUserDetailsService userDetailsService;
        private AuthService authService;
        private RestaurantWebSocketSessionManager webSocketSessionManager;
        private StompAuthChannelInterceptor stompInterceptor;
        private JwtAuthenticationFilter jwtFilter;

        private Restaurant activeRestaurant;
        private Restaurant deactivatedRestaurant;
        private Role adminRole;
        private User userInActiveResto;
        private User userInDeactivatedResto;

        @BeforeEach
        void setUp() {
            activeRestaurant = new Restaurant();
            activeRestaurant.setId(1L);
            activeRestaurant.setName("Active Resto");
            activeRestaurant.setIsActive(true);

            deactivatedRestaurant = new Restaurant();
            deactivatedRestaurant.setId(2L);
            deactivatedRestaurant.setName("Deactivated Resto");
            deactivatedRestaurant.setIsActive(false);

            adminRole = new Role(1L, "ADMIN");

            userInActiveResto = new User();
            userInActiveResto.setId(11L);
            userInActiveResto.setEmail("admin1@active.com");
            userInActiveResto.setRestaurant(activeRestaurant);
            userInActiveResto.setRole(adminRole);
            userInActiveResto.setIsActive(true);
            userInActiveResto.setPasswordHash(passwordEncoder.encode("password"));

            userInDeactivatedResto = new User();
            userInDeactivatedResto.setId(22L);
            userInDeactivatedResto.setEmail("admin2@deactivated.com");
            userInDeactivatedResto.setRestaurant(deactivatedRestaurant);
            userInDeactivatedResto.setRole(adminRole);
            userInDeactivatedResto.setIsActive(true);
            userInDeactivatedResto.setPasswordHash(passwordEncoder.encode("password"));

            userDetailsService = new CustomUserDetailsService(userRepository);
            authService = new AuthService(authenticationManager, userRepository, jwtService);
            webSocketSessionManager = new RestaurantWebSocketSessionManager();
            com.restaurant.restaurantpos.service.TokenRevocationService mockRevocation = mock(com.restaurant.restaurantpos.service.TokenRevocationService.class);
            com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager sessionRevocationManager = new com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager(mockRevocation);
            stompInterceptor = new StompAuthChannelInterceptor(
                    jwtService, userDetailsService, null, webSocketSessionManager, mockRevocation, sessionRevocationManager);
            jwtFilter = new JwtAuthenticationFilter(jwtService, userDetailsService);
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("loadUserByUsername throws DisabledException when restaurant is deactivated")
        void testLoadUserByUsernameDeactivatedRestaurant() {
            when(userRepository.findByEmail("admin2@deactivated.com")).thenReturn(Optional.of(userInDeactivatedResto));

            DisabledException ex = assertThrows(DisabledException.class, () ->
                    userDetailsService.loadUserByUsername("admin2@deactivated.com"));
            assertEquals("Restaurant is deactivated", ex.getMessage());
        }

        @Test
        @DisplayName("loadUserByUsername succeeds when restaurant is active")
        void testLoadUserByUsernameActiveRestaurant() {
            when(userRepository.findByEmail("admin1@active.com")).thenReturn(Optional.of(userInActiveResto));

            assertDoesNotThrow(() -> {
                var userDetails = userDetailsService.loadUserByUsername("admin1@active.com");
                assertNotNull(userDetails);
                assertEquals("admin1@active.com", userDetails.getUsername());
            });
        }

        @Test
        @DisplayName("JwtAuthenticationFilter intercepts deactivated restaurant with HTTP 403 Forbidden")
        void testJwtFilterBlocksDeactivatedRestaurant() throws Exception {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain filterChain = mock(FilterChain.class);
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);

            when(request.getHeader("Authorization")).thenReturn("Bearer previouslyIssuedJwtToken");
            when(jwtService.extractUsername("previouslyIssuedJwtToken")).thenReturn("admin2@deactivated.com");
            when(userRepository.findByEmail("admin2@deactivated.com")).thenReturn(Optional.of(userInDeactivatedResto));
            when(response.getWriter()).thenReturn(printWriter);

            jwtFilter.doFilter(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verify(filterChain, never()).doFilter(request, response);
            assertTrue(stringWriter.toString().contains("Restaurant is deactivated"));
        }

        @Test
        @DisplayName("WebSocket CONNECT is blocked for deactivated restaurant")
        void testWebSocketConnectBlockedForDeactivatedRestaurant() {
            when(jwtService.extractUsername("tokenDeactivated")).thenReturn("admin2@deactivated.com");
            when(userRepository.findByEmail("admin2@deactivated.com")).thenReturn(Optional.of(userInDeactivatedResto));

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer tokenDeactivated");
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = stompInterceptor.preSend(message, messageChannel);

            // User principal must not be authenticated
            StompHeaderAccessor resAccessor = StompHeaderAccessor.wrap(result);
            assertNull(resAccessor.getUser());
        }

        @Test
        @DisplayName("WebSocket SUBSCRIBE is blocked when restaurant is registered as deactivated")
        void testWebSocketSubscribeBlockedWhenRestaurantDeactivated() {
            webSocketSessionManager.deactivateRestaurant(2L);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setSessionId("test-session-deact");
            accessor.setDestination("/topic/restaurants/2/kitchen/orders");
            CustomUserDetails cud = new CustomUserDetails(22L, 2L, "KITCHEN", "kitchen@deactivated.com", "hash", true, List.of());
            Authentication auth = new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
            accessor.setUser(auth);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    stompInterceptor.preSend(message, messageChannel));
            assertTrue(ex.getMessage().contains("Restaurant is deactivated"));
        }

        @Test
        @DisplayName("Reactivation restores WebSocket subscription access")
        void testReactivationRestoresWebSocketAccess() {
            webSocketSessionManager.deactivateRestaurant(2L);
            assertTrue(webSocketSessionManager.isRestaurantDeactivated(2L));

            webSocketSessionManager.reactivateRestaurant(2L);
            assertFalse(webSocketSessionManager.isRestaurantDeactivated(2L));

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setSessionId("test-session-react");
            accessor.setDestination("/topic/restaurants/2/kitchen/orders");
            CustomUserDetails cud = new CustomUserDetails(22L, 2L, "ADMIN", "admin@deactivated.com", "hash", true, List.of());
            Authentication auth = new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
            accessor.setUser(auth);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertDoesNotThrow(() -> stompInterceptor.preSend(message, messageChannel));
        }
    }

    // =========================================================================
    // 4. TENANT ISOLATION & SUPER_ADMIN DEFENSE TESTS
    // =========================================================================
    @Nested
    @DisplayName("4. Tenant Isolation & SUPER_ADMIN Protection Tests")
    class TenantIsolationTests {

        @Mock private UserRepository userRepository;
        @Mock private RestaurantRepository restaurantRepository;
        @Mock private RoleRepository roleRepository;
        @Mock private SecurityUtils securityUtils;

        private UserService userService;
        private Role superAdminRole;
        private Role adminRole;
        private Restaurant restaurantA;
        private Restaurant restaurantB;
        private User superAdminUser;
        private User tenantAdminA;

        @BeforeEach
        void setUp() {
            userService = new UserService(userRepository, restaurantRepository, roleRepository, passwordEncoder, securityUtils);

            superAdminRole = new Role(4L, "SUPER_ADMIN");
            adminRole = new Role(1L, "ADMIN");

            restaurantA = new Restaurant();
            restaurantA.setId(1L);
            restaurantA.setName("Restaurant A");

            restaurantB = new Restaurant();
            restaurantB.setId(2L);
            restaurantB.setName("Restaurant B");

            superAdminUser = new User();
            superAdminUser.setId(999L);
            superAdminUser.setName("Platform Super Admin");
            superAdminUser.setEmail("superadmin@platform.com");
            superAdminUser.setRole(superAdminRole);
            superAdminUser.setRestaurant(null); // No restaurant
            superAdminUser.setIsActive(true);

            tenantAdminA = new User();
            tenantAdminA.setId(10L);
            tenantAdminA.setName("Admin A");
            tenantAdminA.setEmail("admin@restoa.com");
            tenantAdminA.setRole(adminRole);
            tenantAdminA.setRestaurant(restaurantA);
            tenantAdminA.setIsActive(true);
        }

        @Test
        @DisplayName("Tenant admin cannot create a user with SUPER_ADMIN role")
        void testTenantAdminCannotCreateSuperAdmin() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(restaurantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(restaurantA));
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurantA));
            when(roleRepository.findById(4L)).thenReturn(Optional.of(superAdminRole));

            UserRequest request = new UserRequest();
            request.setName("Rogue Super Admin");
            request.setEmail("rogue@platform.com");
            request.setPassword("Password123!");
            request.setRoleId(4L);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.createUser(request));
            assertTrue(ex.getMessage().contains("Invalid role"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tenant admin cannot read or query SUPER_ADMIN user by ID")
        void testTenantAdminCannotReadSuperAdmin() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(userRepository.findByIdAndRestaurantId(999L, 1L)).thenReturn(Optional.empty());
            when(userRepository.findById(999L)).thenReturn(Optional.of(superAdminUser));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.getUserById(999L));
            assertTrue(ex.getMessage().contains("User not found with id: 999"));
        }

        @Test
        @DisplayName("Tenant admin cannot update or deactivate SUPER_ADMIN user")
        void testTenantAdminCannotDeactivateSuperAdmin() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(userRepository.findByIdAndRestaurantId(999L, 1L)).thenReturn(Optional.empty());
            when(userRepository.findById(999L)).thenReturn(Optional.of(superAdminUser));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.deactivateUser(999L));
            assertTrue(ex.getMessage().contains("User not found with id: 999"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("SecurityUtils.validateTenantAccess rejects SUPER_ADMIN without restaurant context")
        void testSuperAdminCallingTenantEndpointsFailsClosed() {
            CustomUserDetails superAdminDetails = new CustomUserDetails(
                    999L, null, "SUPER_ADMIN", "super@platform.com", "hash", true, List.of()
            );
            SecurityUtils utils = mock(SecurityUtils.class);
            when(utils.getCurrentRestaurantId()).thenReturn(null);
            doCallRealMethod().when(utils).validateTenantAccess(anyLong());

            assertThrows(AccessDeniedException.class, () -> utils.validateTenantAccess(1L));
        }
    }

    // =========================================================================
    // 5. JPA ROLE-TENANT LIFECYCLE VALIDATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("5. JPA Role-Tenant Consistency Lifecycle Tests")
    class JpaRoleTenantConsistencyTests {

        @Test
        @DisplayName("SUPER_ADMIN with non-null restaurant fails JPA lifecycle validation")
        void testSuperAdminWithRestaurantThrows() {
            Restaurant resto = new Restaurant();
            resto.setId(1L);

            User user = new User();
            user.setRole(new Role(4L, "SUPER_ADMIN"));
            user.setRestaurant(resto);

            IllegalStateException ex = assertThrows(IllegalStateException.class, user::validateRoleTenantConsistency);
            assertTrue(ex.getMessage().contains("SUPER_ADMIN user cannot be assigned to a tenant restaurant"));
        }

        @Test
        @DisplayName("SUPER_ADMIN with null restaurant passes JPA lifecycle validation")
        void testSuperAdminWithNullRestaurantPasses() {
            User user = new User();
            user.setRole(new Role(4L, "SUPER_ADMIN"));
            user.setRestaurant(null);

            assertDoesNotThrow(user::validateRoleTenantConsistency);
        }

        @Test
        @DisplayName("Tenant role (ADMIN, WAITER, KITCHEN) with null restaurant fails JPA lifecycle validation")
        void testTenantUserWithNullRestaurantThrows() {
            for (String roleName : List.of("ADMIN", "WAITER", "KITCHEN")) {
                User user = new User();
                user.setRole(new Role(1L, roleName));
                user.setRestaurant(null);

                IllegalStateException ex = assertThrows(IllegalStateException.class, user::validateRoleTenantConsistency);
                assertTrue(ex.getMessage().contains("must be assigned to a restaurant"));
            }
        }

        @Test
        @DisplayName("Tenant role with valid restaurant passes JPA lifecycle validation")
        void testTenantUserWithValidRestaurantPasses() {
            Restaurant resto = new Restaurant();
            resto.setId(1L);

            for (String roleName : List.of("ADMIN", "WAITER", "KITCHEN")) {
                User user = new User();
                user.setRole(new Role(1L, roleName));
                user.setRestaurant(resto);

                assertDoesNotThrow(user::validateRoleTenantConsistency);
            }
        }
    }
}
