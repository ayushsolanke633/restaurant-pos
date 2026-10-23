package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.UserRequest;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RevokedTokenRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.service.AuthService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.TokenRevocationService;
import com.restaurant.restaurantpos.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PersistentJwtRevocationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Restaurant restaurant;
    private Role superAdminRole;
    private Role adminRole;
    private Role kitchenRole;
    private Role waiterRole;

    private User superAdminUser;
    private User adminUser;
    private User kitchenUser;
    private User waiterUser;

    @BeforeEach
    void setUp() {
        revokedTokenRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "SUPER_ADMIN")));
        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));
        kitchenRole = roleRepository.findByName("KITCHEN")
                .orElseGet(() -> roleRepository.save(new Role(null, "KITCHEN")));
        waiterRole = roleRepository.findByName("WAITER")
                .orElseGet(() -> roleRepository.save(new Role(null, "WAITER")));

        restaurant = new Restaurant();
        restaurant.setName("Persistent Test Dining");
        restaurant.setIsActive(true);
        restaurant = restaurantRepository.save(restaurant);

        // 1. Super Admin
        superAdminUser = new User();
        superAdminUser.setName("Platform Master");
        superAdminUser.setEmail("superadmin@platform.com");
        superAdminUser.setPasswordHash(passwordEncoder.encode("PlatformPass123!"));
        superAdminUser.setRole(superAdminRole);
        superAdminUser.setRestaurant(null);
        superAdminUser.setIsActive(true);
        superAdminUser.setTokenVersion(1);
        superAdminUser = userRepository.save(superAdminUser);

        // 2. Tenant Admin
        adminUser = new User();
        adminUser.setName("Restaurant Manager");
        adminUser.setEmail("manager@testrestaurant.com");
        adminUser.setPasswordHash(passwordEncoder.encode("ManagerPass123!"));
        adminUser.setRole(adminRole);
        adminUser.setRestaurant(restaurant);
        adminUser.setIsActive(true);
        adminUser.setTokenVersion(1);
        adminUser = userRepository.save(adminUser);

        // 3. Kitchen Staff
        kitchenUser = new User();
        kitchenUser.setName("Head Chef");
        kitchenUser.setEmail("chef@testrestaurant.com");
        kitchenUser.setPasswordHash(passwordEncoder.encode("ChefPass123!"));
        kitchenUser.setRole(kitchenRole);
        kitchenUser.setRestaurant(restaurant);
        kitchenUser.setIsActive(true);
        kitchenUser.setTokenVersion(1);
        kitchenUser = userRepository.save(kitchenUser);

        // 4. Waiter Staff
        waiterUser = new User();
        waiterUser.setName("Floor Waiter");
        waiterUser.setEmail("waiter@testrestaurant.com");
        waiterUser.setPasswordHash(passwordEncoder.encode("WaiterPass123!"));
        waiterUser.setRole(waiterRole);
        waiterUser.setRestaurant(restaurant);
        waiterUser.setIsActive(true);
        waiterUser.setTokenVersion(1);
        waiterUser = userRepository.save(waiterUser);
    }

    @AfterEach
    void tearDown() {
        revokedTokenRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    private String loginAndGetToken(String email, String password) {
        return authService.login(new LoginRequest(email, password)).getToken();
    }

    @Test
    @DisplayName("1. Newly issued token authenticates successfully before logout across all roles")
    void testNewTokenAuthenticatesBeforeLogout() throws Exception {
        // ADMIN access
        String adminToken = loginAndGetToken("manager@testrestaurant.com", "ManagerPass123!");
        assertNotNull(adminToken);
        mockMvc.perform(get("/api/tables")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // SUPER_ADMIN access
        String superAdminToken = loginAndGetToken("superadmin@platform.com", "PlatformPass123!");
        assertNotNull(superAdminToken);
        mockMvc.perform(get("/api/platform/restaurants")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());

        // KITCHEN access
        String kitchenToken = loginAndGetToken("chef@testrestaurant.com", "ChefPass123!");
        assertNotNull(kitchenToken);
        mockMvc.perform(get("/api/kitchen/orders")
                        .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isOk());

        // WAITER access
        String waiterToken = loginAndGetToken("waiter@testrestaurant.com", "WaiterPass123!");
        assertNotNull(waiterToken);
        mockMvc.perform(get("/api/tables")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("2. Calling /api/auth/logout persistently revokes tokens for ADMIN, SUPER_ADMIN, KITCHEN, and WAITER (HTTP 401 on reuse)")
    void testLogoutRevokesTokensForAllRoles() throws Exception {
        // Test ADMIN
        String adminToken = loginAndGetToken("manager@testrestaurant.com", "ManagerPass123!");
        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());

        // Test SUPER_ADMIN
        String superAdminToken = loginAndGetToken("superadmin@platform.com", "PlatformPass123!");
        mockMvc.perform(get("/api/platform/restaurants").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/restaurants").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isUnauthorized());

        // Test KITCHEN
        String kitchenToken = loginAndGetToken("chef@testrestaurant.com", "ChefPass123!");
        mockMvc.perform(get("/api/kitchen/orders").header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/kitchen/orders").header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isUnauthorized());

        // Test WAITER
        String waiterToken = loginAndGetToken("waiter@testrestaurant.com", "WaiterPass123!");
        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. Repeating logout call is safe, idempotent, and does not cause unexpected failure")
    void testRepeatedLogoutIsIdempotent() throws Exception {
        String adminToken = loginAndGetToken("manager@testrestaurant.com", "ManagerPass123!");

        // First logout call
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Second idempotent logout call with same token
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Third idempotent logout call
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    @DisplayName("4. Revoking one token leaves other unrevoked tokens valid")
    void testUnrevokedTokenRemainsValid() throws Exception {
        String token1 = loginAndGetToken("manager@testrestaurant.com", "ManagerPass123!");
        String tokenKitchen = loginAndGetToken("chef@testrestaurant.com", "ChefPass123!");

        // Revoke token1
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk());

        // token1 is revoked
        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + token1))
                .andExpect(status().isUnauthorized());

        // tokenKitchen remains fully valid
        mockMvc.perform(get("/api/kitchen/orders").header("Authorization", "Bearer " + tokenKitchen))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("5. Password change increments tokenVersion and immediately invalidates older tokens")
    void testPasswordChangeInvalidatesOldTokens() throws Exception {
        String oldAdminToken = loginAndGetToken("manager@testrestaurant.com", "ManagerPass123!");
        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + oldAdminToken))
                .andExpect(status().isOk());

        // Update password via MockMvc PUT /api/users/{id}
        UserRequest updateReq = new UserRequest();
        updateReq.setName(adminUser.getName());
        updateReq.setEmail(adminUser.getEmail());
        updateReq.setRoleId(adminRole.getId());
        updateReq.setPassword("NewManagerPassword999!");

        mockMvc.perform(put("/api/users/" + adminUser.getId())
                        .header("Authorization", "Bearer " + oldAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Verify token_version in DB incremented to 2
        User reloadedUser = userRepository.findById(adminUser.getId()).orElseThrow();
        assertEquals(2, reloadedUser.getTokenVersion(), "User token_version must increment to 2 on password change");

        // Old token (which has tokenVersion=1) must now receive HTTP 401
        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + oldAdminToken))
                .andExpect(status().isUnauthorized());

        // Login with new password issues token with tokenVersion=2 and authenticates successfully
        String newAdminToken = loginAndGetToken("manager@testrestaurant.com", "NewManagerPassword999!");
        assertEquals(2, jwtService.extractTokenVersion(newAdminToken));
        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + newAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("6. Tokens missing required jti or tokenVersion claims are rejected after cutover")
    void testMissingRequiredClaimsRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date exp = new Date(now.getTime() + 60000);

        // 1. Token without jti claim
        Map<String, Object> claimsNoJti = new HashMap<>();
        claimsNoJti.put("userId", adminUser.getId());
        claimsNoJti.put("restaurantId", restaurant.getId());
        claimsNoJti.put("role", "ADMIN");
        claimsNoJti.put("tokenVersion", 1);
        String tokenNoJti = Jwts.builder()
                .claims(claimsNoJti)
                .subject(adminUser.getEmail())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + tokenNoJti))
                .andExpect(status().isUnauthorized());

        // 2. Token without tokenVersion claim
        Map<String, Object> claimsNoVer = new HashMap<>();
        claimsNoVer.put("userId", adminUser.getId());
        claimsNoVer.put("restaurantId", restaurant.getId());
        claimsNoVer.put("role", "ADMIN");
        String tokenNoVer = Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .claims(claimsNoVer)
                .subject(adminUser.getEmail())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + tokenNoVer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. Revocation survives service-object recreation against the same database")
    void testRevocationSurvivesServiceObjectRecreation() {
        String token = jwtService.generateToken(adminUser);
        String jti = jwtService.extractJti(token);

        assertFalse(tokenRevocationService.isRevoked(jti));

        // Revoke via service instance 1
        tokenRevocationService.revokeToken(token);
        assertTrue(tokenRevocationService.isRevoked(jti));

        // Create completely new service instance pointing to the same repository
        TokenRevocationService newServiceInstance = new TokenRevocationService(revokedTokenRepository, jwtService);
        assertTrue(newServiceInstance.isRevoked(jti), "Revocation state must be persisted in database and survive service recreation");
    }

    @Test
    @DisplayName("8. Revocation store error fails closed and rejects request")
    void testRevocationStoreErrorFailsClosed() {
        RevokedTokenRepository faultyRepo = mock(RevokedTokenRepository.class);
        when(faultyRepo.existsByJti(anyString())).thenThrow(new RuntimeException("Database connection timeout"));

        TokenRevocationService resilientService = new TokenRevocationService(faultyRepo, jwtService);
        // Fails closed by treating as revoked (true)
        assertTrue(resilientService.isRevoked("any-jti-123"), "Database failure must fail closed by returning true (revoked)");
    }

    @Test
    @DisplayName("9. Waiter single-device concurrency restriction remains strictly enforced alongside persistent revocation")
    void testWaiterSingleDeviceEnforcementPreserved() {
        String waiterToken1 = loginAndGetToken("waiter@testrestaurant.com", "WaiterPass123!");
        assertNotNull(waiterToken1);

        // Second login for same waiter while first is active throws DuplicateSessionException
        assertThrows(com.restaurant.restaurantpos.exception.DuplicateSessionException.class, () ->
                authService.login(new LoginRequest("waiter@testrestaurant.com", "WaiterPass123!"))
        );

        // Log out first waiter device
        authService.logout(waiterToken1);

        // First token is now revoked
        String jti1 = jwtService.extractJti(waiterToken1);
        assertTrue(tokenRevocationService.isRevoked(jti1));

        // Now second device login succeeds
        String waiterToken2 = loginAndGetToken("waiter@testrestaurant.com", "WaiterPass123!");
        assertNotNull(waiterToken2);
        assertNotEquals(waiterToken1, waiterToken2);
    }

    @Test
    @DisplayName("10. SUPER_ADMIN null restaurantId and tenant isolation are preserved")
    void testSuperAdminTenantIsolationPreserved() {
        String superAdminToken = jwtService.generateToken(superAdminUser);
        assertNull(jwtService.extractClaim(superAdminToken, claims -> claims.get("restaurantId")));
        assertEquals(superAdminUser.getEmail(), jwtService.extractUsername(superAdminToken));
        assertEquals("SUPER_ADMIN", jwtService.extractClaim(superAdminToken, claims -> claims.get("role")));
        assertEquals(1, jwtService.extractTokenVersion(superAdminToken));
        assertNotNull(jwtService.extractJti(superAdminToken));

        // Verify claims are present
        assertDoesNotThrow(() -> {
            boolean valid = jwtService.isTokenValid(superAdminToken, new com.restaurant.restaurantpos.security.CustomUserDetails(
                    superAdminUser.getId(),
                    null,
                    "SUPER_ADMIN",
                    superAdminUser.getEmail(),
                    superAdminUser.getPasswordHash(),
                    true,
                    java.util.Collections.emptyList(),
                    1
            ));
            assertTrue(valid);
        });
    }
}
