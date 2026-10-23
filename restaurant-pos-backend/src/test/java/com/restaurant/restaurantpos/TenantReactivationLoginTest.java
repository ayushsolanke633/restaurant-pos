package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.LoginResponse;
import com.restaurant.restaurantpos.dto.RestaurantStatusUpdateRequest;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.GlobalExceptionHandler;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.AuthService;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantReactivationLoginTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JwtService jwtService;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;
    private CustomUserDetailsService userDetailsService;
    private GlobalExceptionHandler exceptionHandler;
    private ObjectMapper objectMapper;

    private Restaurant restaurant;
    private Role adminRole;
    private Role superAdminRole;
    private User adminUser;
    private User superAdminUser;
    private User independentlyDisabledUser;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(authenticationManager, userRepository, jwtService);
        userDetailsService = new CustomUserDetailsService(userRepository);
        exceptionHandler = new GlobalExceptionHandler();
        objectMapper = new ObjectMapper();

        restaurant = new Restaurant();
        restaurant.setId(10L);
        restaurant.setName("Spice Garden");
        restaurant.setIsActive(true);

        adminRole = new Role(1L, "ADMIN");
        superAdminRole = new Role(2L, "SUPER_ADMIN");

        adminUser = new User();
        adminUser.setId(101L);
        adminUser.setName("Spice Admin");
        adminUser.setEmail("admin@spicegarden.com");
        adminUser.setPasswordHash(passwordEncoder.encode("originalPassword123"));
        adminUser.setRestaurant(restaurant);
        adminUser.setRole(adminRole);
        adminUser.setIsActive(true);

        independentlyDisabledUser = new User();
        independentlyDisabledUser.setId(102L);
        independentlyDisabledUser.setName("Disabled Staff");
        independentlyDisabledUser.setEmail("disabled@spicegarden.com");
        independentlyDisabledUser.setPasswordHash(passwordEncoder.encode("originalPassword123"));
        independentlyDisabledUser.setRestaurant(restaurant);
        independentlyDisabledUser.setRole(adminRole);
        independentlyDisabledUser.setIsActive(false); // independently disabled

        superAdminUser = new User();
        superAdminUser.setId(1L);
        superAdminUser.setName("Super Admin");
        superAdminUser.setEmail("superadmin@platform.com");
        superAdminUser.setPasswordHash(passwordEncoder.encode("superSecret123"));
        superAdminUser.setRestaurant(null);
        superAdminUser.setRole(superAdminRole);
        superAdminUser.setIsActive(true);
    }

    @Test
    @DisplayName("1. Active restaurant admin logs in successfully")
    void testActiveRestaurantAdminLoginSuccess() {
        when(userRepository.findByEmail("admin@spicegarden.com")).thenReturn(Optional.of(adminUser));
        when(jwtService.generateToken(adminUser)).thenReturn("valid.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginRequest req = new LoginRequest("admin@spicegarden.com", "originalPassword123");
        LoginResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("valid.jwt.token", resp.getToken());
        assertEquals("admin@spicegarden.com", resp.getEmail());
        assertEquals(10L, resp.getRestaurantId());
        assertEquals("ADMIN", resp.getRole());
    }

    @Test
    @DisplayName("2. Deactivated restaurant blocks tenant admin in CustomUserDetailsService with explicit message")
    void testDeactivatedRestaurantBlocksInUserDetailsService() {
        restaurant.setIsActive(false);
        when(userRepository.findByEmail("admin@spicegarden.com")).thenReturn(Optional.of(adminUser));

        DisabledException ex = assertThrows(DisabledException.class, () ->
                userDetailsService.loadUserByUsername("admin@spicegarden.com"));
        assertEquals("Restaurant is deactivated", ex.getMessage());
    }

    @Test
    @DisplayName("3. Deactivated restaurant blocks tenant admin in AuthService")
    void testDeactivatedRestaurantBlocksInAuthService() {
        restaurant.setIsActive(false);
        when(userRepository.findByEmail("admin@spicegarden.com")).thenReturn(Optional.of(adminUser));

        LoginRequest req = new LoginRequest("admin@spicegarden.com", "originalPassword123");
        DisabledException ex = assertThrows(DisabledException.class, () -> authService.login(req));
        assertEquals("Restaurant is deactivated", ex.getMessage());
    }

    @Test
    @DisplayName("4. Reactivation restores login eligibility for tenant admin with original password")
    void testReactivationRestoresLoginEligibility() {
        String originalPasswordHash = adminUser.getPasswordHash();

        // Step 1: Deactivate restaurant
        restaurant.setIsActive(false);
        when(userRepository.findByEmail("admin@spicegarden.com")).thenReturn(Optional.of(adminUser));

        DisabledException deactEx = assertThrows(DisabledException.class, () ->
                userDetailsService.loadUserByUsername("admin@spicegarden.com"));
        assertEquals("Restaurant is deactivated", deactEx.getMessage());

        // Step 2: Reactivate restaurant
        restaurant.setIsActive(true);

        // Step 3: Verify UserDetailsService loads user cleanly
        var userDetails = userDetailsService.loadUserByUsername("admin@spicegarden.com");
        assertNotNull(userDetails);
        assertEquals("admin@spicegarden.com", userDetails.getUsername());
        assertTrue(userDetails.isEnabled());

        // Step 4: Verify AuthService logs in successfully with original credentials
        when(jwtService.generateToken(adminUser)).thenReturn("new.valid.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        LoginRequest req = new LoginRequest("admin@spicegarden.com", "originalPassword123");
        LoginResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("new.valid.jwt.token", resp.getToken());
        assertEquals("Spice Admin", resp.getName());
        assertEquals(10L, resp.getRestaurantId());

        // Confirm passwords, roles, restaurant assignments were untouched
        assertEquals(originalPasswordHash, adminUser.getPasswordHash());
        assertEquals(10L, adminUser.getRestaurant().getId());
        assertEquals("ADMIN", adminUser.getRole().getName());
        assertTrue(passwordEncoder.matches("originalPassword123", adminUser.getPasswordHash()));
    }

    @Test
    @DisplayName("5. Independently deactivated user remains blocked after restaurant reactivation")
    void testIndependentlyDeactivatedUserRemainsBlockedAfterRestaurantReactivation() {
        restaurant.setIsActive(true);
        when(userRepository.findByEmail("disabled@spicegarden.com")).thenReturn(Optional.of(independentlyDisabledUser));

        // Blocked in UserDetailsService
        DisabledException exUds = assertThrows(DisabledException.class, () ->
                userDetailsService.loadUserByUsername("disabled@spicegarden.com"));
        assertEquals("User is deactivated", exUds.getMessage());

        // Blocked in AuthService
        LoginRequest req = new LoginRequest("disabled@spicegarden.com", "originalPassword123");
        DisabledException exAuth = assertThrows(DisabledException.class, () -> authService.login(req));
        assertEquals("User is deactivated", exAuth.getMessage());
    }

    @Test
    @DisplayName("6. Platform SUPER_ADMIN login unaffected by any restaurant deactivation")
    void testSuperAdminLoginUnaffected() {
        when(userRepository.findByEmail("superadmin@platform.com")).thenReturn(Optional.of(superAdminUser));
        when(jwtService.generateToken(superAdminUser)).thenReturn("superadmin.jwt");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        var userDetails = userDetailsService.loadUserByUsername("superadmin@platform.com");
        assertNotNull(userDetails);
        assertNull(((CustomUserDetails) userDetails).getRestaurantId());

        LoginRequest req = new LoginRequest("superadmin@platform.com", "superSecret123");
        LoginResponse resp = authService.login(req);
        assertNotNull(resp);
        assertEquals("SUPER_ADMIN", resp.getRole());
        assertNull(resp.getRestaurantId());
    }

    @Test
    @DisplayName("7. GlobalExceptionHandler returns HTTP 403 with distinct message for DisabledException")
    void testGlobalExceptionHandlerDisabledException() {
        // Direct DisabledException
        DisabledException directEx = new DisabledException("Restaurant is deactivated");
        ResponseEntity<Map<String, String>> directResp = exceptionHandler.handleDisabledException(directEx);
        assertEquals(HttpStatus.FORBIDDEN, directResp.getStatusCode());
        assertEquals("Restaurant is deactivated", directResp.getBody().get("message"));

        // Wrapped in InternalAuthenticationServiceException (DaoAuthenticationProvider flow)
        InternalAuthenticationServiceException wrappedEx =
                new InternalAuthenticationServiceException("Internal error", directEx);
        ResponseEntity<Map<String, String>> wrappedResp = exceptionHandler.handleAuthenticationException(wrappedEx);
        assertEquals(HttpStatus.FORBIDDEN, wrappedResp.getStatusCode());
        assertEquals("Restaurant is deactivated", wrappedResp.getBody().get("message"));

        // User is deactivated
        DisabledException userDisabledEx = new DisabledException("User is deactivated");
        ResponseEntity<Map<String, String>> userDisabledResp = exceptionHandler.handleDisabledException(userDisabledEx);
        assertEquals(HttpStatus.FORBIDDEN, userDisabledResp.getStatusCode());
        assertEquals("User is deactivated", userDisabledResp.getBody().get("message"));
    }

    @Test
    @DisplayName("8. GlobalExceptionHandler returns HTTP 401 for true BadCredentialsException")
    void testGlobalExceptionHandlerBadCredentials() {
        BadCredentialsException badCredEx = new BadCredentialsException("Bad credentials");
        ResponseEntity<Map<String, String>> resp = exceptionHandler.handleAuthenticationException(badCredEx);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals("Invalid email or password", resp.getBody().get("message"));
    }

    @Test
    @DisplayName("9. Tenant isolation blocks cross-tenant access between reactivated and other restaurants")
    void testTenantIsolationEnforced() {
        SecurityUtils securityUtils = new SecurityUtils();

        CustomUserDetails userDetails = new CustomUserDetails(
                101L, 10L, "ADMIN", "admin@spicegarden.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        try {
            // Authorized for own restaurant (10)
            assertDoesNotThrow(() -> securityUtils.validateTenantAccess(10L));

            // Blocked from accessing other restaurant (99)
            assertThrows(AccessDeniedException.class, () -> securityUtils.validateTenantAccess(99L));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("10. RestaurantStatusUpdateRequest supports both isActive and active JSON property names")
    void testRestaurantStatusUpdateRequestJacksonBinding() throws Exception {
        // {"isActive": true}
        RestaurantStatusUpdateRequest req1 =
                objectMapper.readValue("{\"isActive\": true}", RestaurantStatusUpdateRequest.class);
        assertTrue(req1.getIsActive());

        // {"isActive": false}
        RestaurantStatusUpdateRequest req2 =
                objectMapper.readValue("{\"isActive\": false}", RestaurantStatusUpdateRequest.class);
        assertFalse(req2.getIsActive());

        // {"active": true}
        RestaurantStatusUpdateRequest req3 =
                objectMapper.readValue("{\"active\": true}", RestaurantStatusUpdateRequest.class);
        assertTrue(req3.getIsActive());

        // {"status": false}
        RestaurantStatusUpdateRequest req4 =
                objectMapper.readValue("{\"status\": false}", RestaurantStatusUpdateRequest.class);
        assertFalse(req4.getIsActive());
    }
}
