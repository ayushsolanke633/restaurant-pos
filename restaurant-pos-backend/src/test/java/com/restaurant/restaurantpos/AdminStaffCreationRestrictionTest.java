package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.UserRequest;
import com.restaurant.restaurantpos.dto.UserResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminStaffCreationRestrictionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Restaurant restaurant1;
    private Restaurant restaurant2;

    private Role adminRole;
    private Role waiterRole;
    private Role kitchenRole;
    private Role superAdminRole;

    private User admin1;
    private User admin2;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));
        waiterRole = roleRepository.findByName("WAITER")
                .orElseGet(() -> roleRepository.save(new Role(null, "WAITER")));
        kitchenRole = roleRepository.findByName("KITCHEN")
                .orElseGet(() -> roleRepository.save(new Role(null, "KITCHEN")));
        superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "SUPER_ADMIN")));

        restaurant1 = new Restaurant();
        restaurant1.setName("Spice Garden");
        restaurant1.setIsActive(true);
        restaurant1 = restaurantRepository.save(restaurant1);

        restaurant2 = new Restaurant();
        restaurant2.setName("Urban Tadka");
        restaurant2.setIsActive(true);
        restaurant2 = restaurantRepository.save(restaurant2);

        admin1 = new User();
        admin1.setName("Admin One");
        admin1.setEmail("admin1@spicegarden.com");
        admin1.setPasswordHash(passwordEncoder.encode("adminPass1"));
        admin1.setRole(adminRole);
        admin1.setRestaurant(restaurant1);
        admin1.setIsActive(true);
        admin1 = userRepository.save(admin1);

        admin2 = new User();
        admin2.setName("Admin Two");
        admin2.setEmail("admin2@urbantadka.com");
        admin2.setPasswordHash(passwordEncoder.encode("adminPass2"));
        admin2.setRole(adminRole);
        admin2.setRestaurant(restaurant2);
        admin2.setIsActive(true);
        admin2 = userRepository.save(admin2);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User admin) {
        CustomUserDetails userDetails = new CustomUserDetails(
                admin.getId(),
                admin.getRestaurant().getId(),
                admin.getRole().getName(),
                admin.getEmail(),
                admin.getPasswordHash(),
                admin.getIsActive(),
                List.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole().getName()))
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("1. An Admin can create multiple waiters without artificial limits")
    void testAdminCanCreateMultipleWaiters() {
        authenticateAs(admin1);

        UserRequest w1 = new UserRequest();
        w1.setName("Waiter One");
        w1.setEmail("waiter1@spicegarden.com");
        w1.setPassword("waiterPass1");
        w1.setRoleId(waiterRole.getId());
        UserResponse resp1 = userService.createUser(w1);
        assertNotNull(resp1.getId());
        assertEquals("WAITER", resp1.getRoleName());
        assertEquals(restaurant1.getId(), resp1.getRestaurantId());

        UserRequest w2 = new UserRequest();
        w2.setName("Waiter Two");
        w2.setEmail("waiter2@spicegarden.com");
        w2.setPassword("waiterPass2");
        w2.setRoleId(waiterRole.getId());
        UserResponse resp2 = userService.createUser(w2);
        assertNotNull(resp2.getId());

        UserRequest w3 = new UserRequest();
        w3.setName("Waiter Three");
        w3.setEmail("waiter3@spicegarden.com");
        w3.setPassword("waiterPass3");
        w3.setRoleId(waiterRole.getId());
        UserResponse resp3 = userService.createUser(w3);
        assertNotNull(resp3.getId());

        Long waiterCount = userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurant1.getId(), "WAITER");
        assertEquals(3L, waiterCount);
    }

    @Test
    @DisplayName("2. An Admin can create the first kitchen account")
    void testAdminCanCreateFirstKitchenAccount() {
        authenticateAs(admin1);

        UserRequest req = new UserRequest();
        req.setName("Chef Marco");
        req.setEmail("kitchen@spicegarden.com");
        req.setPassword("kitchenPass1");
        req.setRoleId(kitchenRole.getId());

        UserResponse response = userService.createUser(req);
        assertNotNull(response.getId());
        assertEquals("KITCHEN", response.getRoleName());
        assertEquals(restaurant1.getId(), response.getRestaurantId());

        Long kitchenCount = userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurant1.getId(), "KITCHEN");
        assertEquals(1L, kitchenCount);
    }

    @Test
    @DisplayName("3. An Admin cannot create a second kitchen account")
    void testAdminCannotCreateSecondKitchenAccount() {
        authenticateAs(admin1);

        UserRequest req1 = new UserRequest();
        req1.setName("Chef Marco");
        req1.setEmail("kitchen1@spicegarden.com");
        req1.setPassword("kitchenPass1");
        req1.setRoleId(kitchenRole.getId());
        userService.createUser(req1);

        UserRequest req2 = new UserRequest();
        req2.setName("Chef Luigi");
        req2.setEmail("kitchen2@spicegarden.com");
        req2.setPassword("kitchenPass2");
        req2.setRoleId(kitchenRole.getId());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.createUser(req2));
        assertTrue(ex.getMessage().contains("Only one kitchen staff account is allowed per restaurant"),
                "Expected single-kitchen error message but got: " + ex.getMessage());

        Long kitchenCount = userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurant1.getId(), "KITCHEN");
        assertEquals(1L, kitchenCount);
    }

    @Test
    @DisplayName("4. An inactive kitchen account still prevents another kitchen account")
    void testInactiveKitchenAccountStillPreventsAnotherKitchenAccount() {
        authenticateAs(admin1);

        UserRequest req1 = new UserRequest();
        req1.setName("Chef Marco");
        req1.setEmail("kitchen_old@spicegarden.com");
        req1.setPassword("kitchenPass1");
        req1.setRoleId(kitchenRole.getId());
        UserResponse created = userService.createUser(req1);

        // Deactivate the kitchen account
        userService.deactivateUser(created.getId());

        User inactiveUser = userRepository.findById(created.getId()).orElseThrow();
        assertFalse(inactiveUser.getIsActive(), "User should be inactive");

        // Attempt to create another kitchen account
        UserRequest req2 = new UserRequest();
        req2.setName("Chef New");
        req2.setEmail("kitchen_new@spicegarden.com");
        req2.setPassword("kitchenPass2");
        req2.setRoleId(kitchenRole.getId());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.createUser(req2));
        assertTrue(ex.getMessage().contains("Only one kitchen staff account is allowed per restaurant"),
                "Expected single-kitchen error even when existing account is inactive, but got: " + ex.getMessage());
    }

    @Test
    @DisplayName("5. An Admin cannot create another Admin account")
    void testAdminCannotCreateAdminAccount() {
        authenticateAs(admin1);

        UserRequest req = new UserRequest();
        req.setName("Second Admin");
        req.setEmail("secondadmin@spicegarden.com");
        req.setPassword("adminPass2");
        req.setRoleId(adminRole.getId());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.createUser(req));
        assertTrue(ex.getMessage().contains("Admin cannot create ADMIN accounts") || ex.getMessage().contains("Invalid role"),
                "Expected admin creation rejection but got: " + ex.getMessage());
    }

    @Test
    @DisplayName("6. An Admin cannot create a SUPER_ADMIN account")
    void testAdminCannotCreateSuperAdminAccount() {
        authenticateAs(admin1);

        UserRequest req = new UserRequest();
        req.setName("Rogue Super Admin");
        req.setEmail("rogue@spicegarden.com");
        req.setPassword("superPass123");
        req.setRoleId(superAdminRole.getId());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.createUser(req));
        assertTrue(ex.getMessage().contains("SUPER_ADMIN") || ex.getMessage().contains("Invalid role"),
                "Expected super admin creation rejection but got: " + ex.getMessage());
    }

    @Test
    @DisplayName("7. One restaurant's kitchen account does not prevent another restaurant from creating its own")
    void testKitchenRestrictionIsTenantIsolated() {
        // Restaurant 1 creates its kitchen account
        authenticateAs(admin1);
        UserRequest req1 = new UserRequest();
        req1.setName("Chef R1");
        req1.setEmail("chef@spicegarden.com");
        req1.setPassword("passR1");
        req1.setRoleId(kitchenRole.getId());
        UserResponse resp1 = userService.createUser(req1);
        assertEquals(restaurant1.getId(), resp1.getRestaurantId());

        // Restaurant 2 creates its kitchen account independently
        authenticateAs(admin2);
        UserRequest req2 = new UserRequest();
        req2.setName("Chef R2");
        req2.setEmail("chef@urbantadka.com");
        req2.setPassword("passR2");
        req2.setRoleId(kitchenRole.getId());
        UserResponse resp2 = userService.createUser(req2);
        assertEquals(restaurant2.getId(), resp2.getRestaurantId());

        assertEquals(1L, userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurant1.getId(), "KITCHEN"));
        assertEquals(1L, userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurant2.getId(), "KITCHEN"));
    }

    @Test
    @DisplayName("8. Direct API requests cannot bypass these restrictions")
    void testDirectApiRequestsCannotBypassRestrictions() throws Exception {
        // 1. Log in as admin1 to get JWT
        LoginRequest loginReq = new LoginRequest("admin1@spicegarden.com", "adminPass1");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> loginResp = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String jwtToken = (String) loginResp.get("token");

        // Attempt 1: Create ADMIN role via API -> Rejected with 403 Forbidden
        UserRequest adminAttempt = new UserRequest();
        adminAttempt.setName("API Admin Attempt");
        adminAttempt.setEmail("api_admin@spicegarden.com");
        adminAttempt.setPassword("password123");
        adminAttempt.setRoleId(adminRole.getId());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminAttempt)))
                .andExpect(status().isForbidden());

        // Attempt 2: Create SUPER_ADMIN role via API -> Rejected with 403 Forbidden
        UserRequest superAttempt = new UserRequest();
        superAttempt.setName("API SuperAdmin Attempt");
        superAttempt.setEmail("api_super@spicegarden.com");
        superAttempt.setPassword("password123");
        superAttempt.setRoleId(superAdminRole.getId());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(superAttempt)))
                .andExpect(status().isForbidden());

        // Attempt 3: Create valid KITCHEN via API -> 201 Created
        UserRequest kitchenReq1 = new UserRequest();
        kitchenReq1.setName("First Kitchen via API");
        kitchenReq1.setEmail("kitchen_api1@spicegarden.com");
        kitchenReq1.setPassword("password123");
        kitchenReq1.setRoleId(kitchenRole.getId());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kitchenReq1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleName").value("KITCHEN"));

        // Attempt 4: Create duplicate KITCHEN via API -> 409 Conflict
        UserRequest kitchenReq2 = new UserRequest();
        kitchenReq2.setName("Second Kitchen via API");
        kitchenReq2.setEmail("kitchen_api2@spicegarden.com");
        kitchenReq2.setPassword("password123");
        kitchenReq2.setRoleId(kitchenRole.getId());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kitchenReq2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only one kitchen staff account is allowed per restaurant")));

        // Attempt 5: Supply cross-tenant restaurantId in body -> Rejected with 403 Forbidden
        UserRequest crossTenantAttempt = new UserRequest();
        crossTenantAttempt.setRestaurantId(restaurant2.getId()); // Attacker specifies Restaurant 2
        crossTenantAttempt.setName("Cross Tenant Waiter");
        crossTenantAttempt.setEmail("cross_waiter@spicegarden.com");
        crossTenantAttempt.setPassword("password123");
        crossTenantAttempt.setRoleId(waiterRole.getId());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossTenantAttempt)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Concurrent attempts cannot create duplicate kitchen accounts")
    void testConcurrentAttemptsCannotCreateDuplicateKitchenAccounts() throws Exception {
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Synchronize threads to fire simultaneously

                    authenticateAs(admin2); // Authenticate thread as Admin of Restaurant 2

                    UserRequest req = new UserRequest();
                    req.setName("Concurrent Chef " + index);
                    req.setEmail("concurrent_chef_" + index + "@urbantadka.com");
                    req.setPassword("chefPassword" + index);
                    req.setRoleId(kitchenRole.getId());

                    userService.createUser(req);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                    errors.add(t);
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Trigger all threads simultaneously
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent executions did not finish within timeout");

        assertEquals(1, successCount.get(), "Exactly one concurrent attempt must succeed");
        assertEquals(threadCount - 1, failureCount.get(), "All other concurrent attempts must fail");

        // Verify the database has strictly 1 kitchen account for Restaurant 2
        Long finalKitchenCount = userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurant2.getId(), "KITCHEN");
        assertEquals(1L, finalKitchenCount, "Database must strictly contain exactly 1 kitchen account");

        // Verify failure errors contain the single-kitchen message
        for (Throwable t : errors) {
            assertTrue(t.getMessage().contains("Only one kitchen staff account is allowed per restaurant"),
                    "Expected single-kitchen violation message but got: " + t.getMessage());
        }
    }
}
