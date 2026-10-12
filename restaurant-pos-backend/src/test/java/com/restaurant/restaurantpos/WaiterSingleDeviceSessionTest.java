package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.LoginResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.DuplicateSessionException;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.JwtAuthenticationFilter;
import com.restaurant.restaurantpos.security.WaiterSessionManager;
import com.restaurant.restaurantpos.service.AuthService;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WaiterSingleDeviceSessionTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    private WaiterSessionManager sessionManager;
    private AuthService authService;
    private JwtAuthenticationFilter jwtFilter;

    private User waiter1;
    private User waiter2;
    private User adminUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        sessionManager = new WaiterSessionManager();
        authService = new AuthService(authenticationManager, userRepository, jwtService, sessionManager);
        jwtFilter = new JwtAuthenticationFilter(jwtService, userDetailsService, sessionManager);

        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        Role waiterRole = new Role(3L, "WAITER");
        Role adminRole = new Role(1L, "ADMIN");

        waiter1 = new User();
        waiter1.setId(101L);
        waiter1.setEmail("waiter1@restaurant.com");
        waiter1.setName("Waiter One");
        waiter1.setIsActive(true);
        waiter1.setRole(waiterRole);
        waiter1.setRestaurant(restaurant);

        waiter2 = new User();
        waiter2.setId(102L);
        waiter2.setEmail("waiter2@restaurant.com");
        waiter2.setName("Waiter Two");
        waiter2.setIsActive(true);
        waiter2.setRole(waiterRole);
        waiter2.setRestaurant(restaurant);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@restaurant.com");
        adminUser.setName("Admin User");
        adminUser.setIsActive(true);
        adminUser.setRole(adminRole);
        adminUser.setRestaurant(restaurant);
    }

    @Test
    @DisplayName("1. First waiter login succeeds and establishes active session")
    void testFirstWaiterLoginSucceeds() {
        when(userRepository.findByEmail("waiter1@restaurant.com")).thenReturn(Optional.of(waiter1));
        when(jwtService.generateToken(waiter1)).thenReturn("jwt-token-device-1");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginRequest req = new LoginRequest("waiter1@restaurant.com", "password");
        LoginResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("jwt-token-device-1", resp.getToken());
        assertEquals("WAITER", resp.getRole());
        assertTrue(sessionManager.isSessionActive(101L, "jwt-token-device-1"));
    }

    @Test
    @DisplayName("2. Second login for same waiter rejected with exact message")
    void testSecondLoginSameWaiterRejected() {
        when(userRepository.findByEmail("waiter1@restaurant.com")).thenReturn(Optional.of(waiter1));
        when(jwtService.generateToken(waiter1)).thenReturn("jwt-token-device-1", "jwt-token-device-2");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginRequest req = new LoginRequest("waiter1@restaurant.com", "password");
        LoginResponse resp1 = authService.login(req);
        assertNotNull(resp1);

        DuplicateSessionException ex = assertThrows(DuplicateSessionException.class, () -> authService.login(req));
        assertEquals(
                "This waiter account is already logged in on another device. Please log out from the other device first.",
                ex.getMessage()
        );
    }

    @Test
    @DisplayName("3. First session remains active when second login is rejected")
    void testFirstSessionRemainsActiveAfterRejectedSecondLogin() {
        when(userRepository.findByEmail("waiter1@restaurant.com")).thenReturn(Optional.of(waiter1));
        when(jwtService.generateToken(waiter1)).thenReturn("jwt-token-device-1", "jwt-token-device-2");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginRequest req = new LoginRequest("waiter1@restaurant.com", "password");
        authService.login(req);

        assertThrows(DuplicateSessionException.class, () -> authService.login(req));
        assertTrue(sessionManager.isSessionActive(101L, "jwt-token-device-1"), "First session must stay valid");
        assertFalse(sessionManager.isSessionActive(101L, "jwt-token-device-2"), "Second session must not be active");
    }

    @Test
    @DisplayName("4. Logout releases active waiter session")
    void testLogoutReleasesSession() {
        when(userRepository.findByEmail("waiter1@restaurant.com")).thenReturn(Optional.of(waiter1));
        when(jwtService.generateToken(waiter1)).thenReturn("jwt-token-device-1");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);
        when(jwtService.extractUsername("jwt-token-device-1")).thenReturn("waiter1@restaurant.com");

        LoginRequest req = new LoginRequest("waiter1@restaurant.com", "password");
        authService.login(req);
        assertTrue(sessionManager.isSessionActive(101L, "jwt-token-device-1"));

        authService.logout("jwt-token-device-1");
        assertFalse(sessionManager.isSessionActive(101L, "jwt-token-device-1"), "Session should be released after logout");
    }

    @Test
    @DisplayName("5. Login from another device succeeds after logout")
    void testLoginFromAnotherDeviceSucceedsAfterLogout() {
        when(userRepository.findByEmail("waiter1@restaurant.com")).thenReturn(Optional.of(waiter1));
        when(jwtService.generateToken(waiter1)).thenReturn("jwt-token-device-1", "jwt-token-device-2");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);
        when(jwtService.extractUsername("jwt-token-device-1")).thenReturn("waiter1@restaurant.com");

        LoginRequest req = new LoginRequest("waiter1@restaurant.com", "password");
        authService.login(req);
        authService.logout("jwt-token-device-1");

        // Second device login now succeeds
        LoginResponse resp2 = authService.login(req);
        assertNotNull(resp2);
        assertEquals("jwt-token-device-2", resp2.getToken());
        assertTrue(sessionManager.isSessionActive(101L, "jwt-token-device-2"));
        assertFalse(sessionManager.isSessionActive(101L, "jwt-token-device-1"));
    }

    private CustomUserDetails createCustomUserDetails(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : "USER";
        org.springframework.security.core.authority.SimpleGrantedAuthority authority =
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + roleName);
        Long restaurantId = user.getRestaurant() != null ? user.getRestaurant().getId() : null;
        return new CustomUserDetails(
                user.getId(),
                restaurantId,
                roleName,
                user.getEmail(),
                "dummyHash",
                Boolean.TRUE.equals(user.getIsActive()),
                java.util.Collections.singletonList(authority)
        );
    }

    @Test
    @DisplayName("6. Logged-out token is rejected on protected endpoints by JwtAuthenticationFilter")
    void testLoggedOutTokenRejectedByFilter() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer logged-out-token");
        when(jwtService.extractUsername("logged-out-token")).thenReturn("waiter1@restaurant.com");

        CustomUserDetails cud = createCustomUserDetails(waiter1);
        when(userDetailsService.loadUserByUsername("waiter1@restaurant.com")).thenReturn(cud);
        when(jwtService.isTokenValid("logged-out-token", cud)).thenReturn(true);

        // Session manager has NO active session for waiter1 with "logged-out-token"
        assertFalse(sessionManager.isSessionActive(101L, "logged-out-token"));

        jwtFilter.doFilter(request, response, filterChain);

        // Security context should NOT be populated with authentication
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "Filter must not authenticate revoked/logged-out waiter token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("7. Active waiter token is authenticated by JwtAuthenticationFilter")
    void testActiveWaiterTokenAuthenticatedByFilter() throws Exception {
        sessionManager.registerSession(101L, "active-valid-token");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer active-valid-token");
        when(jwtService.extractUsername("active-valid-token")).thenReturn("waiter1@restaurant.com");

        CustomUserDetails cud = createCustomUserDetails(waiter1);
        when(userDetailsService.loadUserByUsername("waiter1@restaurant.com")).thenReturn(cud);
        when(jwtService.isTokenValid("active-valid-token", cud)).thenReturn(true);

        jwtFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication(), "Filter must authenticate active waiter token");
        assertEquals("waiter1@restaurant.com", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("8. Simultaneous logins result in exactly one active session")
    void testConcurrentLoginsResultInExactlyOneSession() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String token = "token-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    sessionManager.registerSession(101L, token);
                    successCount.incrementAndGet();
                } catch (DuplicateSessionException dse) {
                    duplicateCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one login attempt must succeed concurrently");
        assertEquals(threads - 1, duplicateCount.get(), "All other concurrent login attempts must receive DuplicateSessionException");
    }

    @Test
    @DisplayName("9. Different waiters log in normally without blocking each other")
    void testDifferentWaitersLogInNormally() {
        when(userRepository.findByEmail("waiter1@restaurant.com")).thenReturn(Optional.of(waiter1));
        when(userRepository.findByEmail("waiter2@restaurant.com")).thenReturn(Optional.of(waiter2));
        when(jwtService.generateToken(waiter1)).thenReturn("jwt-w1");
        when(jwtService.generateToken(waiter2)).thenReturn("jwt-w2");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginResponse resp1 = authService.login(new LoginRequest("waiter1@restaurant.com", "pw"));
        LoginResponse resp2 = authService.login(new LoginRequest("waiter2@restaurant.com", "pw"));

        assertNotNull(resp1);
        assertNotNull(resp2);
        assertTrue(sessionManager.isSessionActive(101L, "jwt-w1"));
        assertTrue(sessionManager.isSessionActive(102L, "jwt-w2"));
    }

    @Test
    @DisplayName("10. Admin login unaffected by waiter session manager")
    void testAdminLoginUnaffected() {
        when(userRepository.findByEmail("admin@restaurant.com")).thenReturn(Optional.of(adminUser));
        when(jwtService.generateToken(adminUser)).thenReturn("jwt-admin-1", "jwt-admin-2");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginRequest req = new LoginRequest("admin@restaurant.com", "pw");
        LoginResponse resp1 = authService.login(req);
        LoginResponse resp2 = authService.login(req);

        assertNotNull(resp1);
        assertNotNull(resp2);
        assertFalse(sessionManager.hasActiveSession(1L), "Admin user must not be tracked in WaiterSessionManager");
    }

    @Test
    @DisplayName("11. Expired session allows new login after timeout")
    void testExpiredSessionAllowsNewLogin() {
        sessionManager.setIdleTimeoutMs(100L); // 100ms timeout
        sessionManager.registerSession(101L, "old-token");

        // Wait for idle timeout
        try {
            Thread.sleep(150L);
        } catch (InterruptedException ignored) {}

        // New registration should now succeed without throwing DuplicateSessionException
        assertDoesNotThrow(() -> sessionManager.registerSession(101L, "new-token"));
        assertTrue(sessionManager.isSessionActive(101L, "new-token"));
        assertFalse(sessionManager.isSessionActive(101L, "old-token"));
    }
}
