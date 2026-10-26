package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.restaurantpos.dto.PlatformOnboardingRequest;
import com.restaurant.restaurantpos.dto.RestaurantRequest;
import com.restaurant.restaurantpos.dto.RestaurantStatusUpdateRequest;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.RestaurantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class TenantRestaurantManagementAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private com.restaurant.restaurantpos.repository.RestaurantSettingsRepository restaurantSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.restaurant.restaurantpos.security.WaiterSessionManager waiterSessionManager;

    @Autowired
    private RestaurantService restaurantService;

    private Restaurant restaurant1;
    private Restaurant restaurant2;

    private Role superAdminRole;
    private Role adminRole;
    private Role waiterRole;
    private Role kitchenRole;

    private User superAdminUser;
    private User admin1User;
    private User admin2User;
    private User waiter1User;
    private User kitchen1User;

    private String superAdminToken;
    private String admin1Token;
    private String admin2Token;
    private String waiter1Token;
    private String kitchen1Token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        restaurantSettingsRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "SUPER_ADMIN")));
        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));
        waiterRole = roleRepository.findByName("WAITER")
                .orElseGet(() -> roleRepository.save(new Role(null, "WAITER")));
        kitchenRole = roleRepository.findByName("KITCHEN")
                .orElseGet(() -> roleRepository.save(new Role(null, "KITCHEN")));

        restaurant1 = new Restaurant();
        restaurant1.setName("Spice Garden");
        restaurant1.setAddress("123 Spice Street");
        restaurant1.setPhone("9876543210");
        restaurant1.setEmail("contact@spicegarden.com");
        restaurant1.setGstNumber("GSTIN11111");
        restaurant1.setIsActive(true);
        restaurant1 = restaurantRepository.save(restaurant1);

        restaurant2 = new Restaurant();
        restaurant2.setName("Urban Tadka");
        restaurant2.setAddress("456 Urban Road");
        restaurant2.setPhone("9876543211");
        restaurant2.setEmail("contact@urbantadka.com");
        restaurant2.setGstNumber("GSTIN22222");
        restaurant2.setIsActive(true);
        restaurant2 = restaurantRepository.save(restaurant2);

        superAdminUser = new User();
        superAdminUser.setName("Platform Super Admin");
        superAdminUser.setEmail("superadmin@platform.com");
        superAdminUser.setPasswordHash(passwordEncoder.encode("superPass123!"));
        superAdminUser.setRole(superAdminRole);
        superAdminUser.setRestaurant(null);
        superAdminUser.setIsActive(true);
        superAdminUser = userRepository.save(superAdminUser);

        admin1User = new User();
        admin1User.setName("Admin One");
        admin1User.setEmail("admin1@spicegarden.com");
        admin1User.setPasswordHash(passwordEncoder.encode("adminPass1!"));
        admin1User.setRole(adminRole);
        admin1User.setRestaurant(restaurant1);
        admin1User.setIsActive(true);
        admin1User = userRepository.save(admin1User);

        admin2User = new User();
        admin2User.setName("Admin Two");
        admin2User.setEmail("admin2@urbantadka.com");
        admin2User.setPasswordHash(passwordEncoder.encode("adminPass2!"));
        admin2User.setRole(adminRole);
        admin2User.setRestaurant(restaurant2);
        admin2User.setIsActive(true);
        admin2User = userRepository.save(admin2User);

        waiter1User = new User();
        waiter1User.setName("Waiter One");
        waiter1User.setEmail("waiter1@spicegarden.com");
        waiter1User.setPasswordHash(passwordEncoder.encode("waiterPass1!"));
        waiter1User.setRole(waiterRole);
        waiter1User.setRestaurant(restaurant1);
        waiter1User.setIsActive(true);
        waiter1User = userRepository.save(waiter1User);

        kitchen1User = new User();
        kitchen1User.setName("Kitchen One");
        kitchen1User.setEmail("kitchen1@spicegarden.com");
        kitchen1User.setPasswordHash(passwordEncoder.encode("kitchenPass1!"));
        kitchen1User.setRole(kitchenRole);
        kitchen1User.setRestaurant(restaurant1);
        kitchen1User.setIsActive(true);
        kitchen1User = userRepository.save(kitchen1User);

        superAdminToken = jwtService.generateToken(superAdminUser);
        admin1Token = jwtService.generateToken(admin1User);
        admin2Token = jwtService.generateToken(admin2User);
        waiter1Token = jwtService.generateToken(waiter1User);
        waiterSessionManager.registerSession(waiter1User.getId(), waiter1Token);
        kitchen1Token = jwtService.generateToken(kitchen1User);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (waiterSessionManager != null) {
            waiterSessionManager.clearAllSessions();
        }
    }

    // =========================================================================
    // 1. UNAUTHENTICATED REQUESTS
    // =========================================================================

    @Test
    @DisplayName("1. Unauthenticated requests to restaurant creation and deletion are rejected with 401")
    void testUnauthenticatedRequestsRejected() throws Exception {
        RestaurantRequest req = new RestaurantRequest();
        req.setName("Unauthenticated Cafe");

        mockMvc.perform(post("/api/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/restaurants/" + restaurant1.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/restaurants/" + restaurant1.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/restaurants/" + restaurant1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/platform/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. TENANT ADMIN REJECTION ON RESTAURANT CREATION & DELETION
    // =========================================================================

    @Test
    @DisplayName("2. Tenant ADMIN cannot create a restaurant via legacy POST /api/restaurants (returns 403)")
    void testTenantAdminCannotCreateRestaurant() throws Exception {
        RestaurantRequest req = new RestaurantRequest();
        req.setName("Rogue Tenant Restaurant");
        req.setAddress("789 Rogue St");
        req.setPhone("9998887776");
        req.setEmail("rogue@restaurant.com");
        req.setGstNumber("GSTINROGUE");

        mockMvc.perform(post("/api/restaurants")
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Tenant ADMIN cannot delete their own restaurant via legacy DELETE /api/restaurants/{id} (returns 403)")
    void testTenantAdminCannotDeleteOwnRestaurant() throws Exception {
        mockMvc.perform(delete("/api/restaurants/" + restaurant1.getId())
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isForbidden());

        assertTrue(restaurantRepository.existsById(restaurant1.getId()), "Restaurant 1 must not be deleted");
    }

    @Test
    @DisplayName("4. Tenant ADMIN cannot delete another restaurant via legacy DELETE /api/restaurants/{id} (returns 403)")
    void testTenantAdminCannotDeleteOtherRestaurant() throws Exception {
        mockMvc.perform(delete("/api/restaurants/" + restaurant2.getId())
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isForbidden());

        assertTrue(restaurantRepository.existsById(restaurant2.getId()), "Restaurant 2 must not be deleted");
    }

    // =========================================================================
    // 3. TENANT ADMIN REJECTION ON PLATFORM-LEVEL ENDPOINTS
    // =========================================================================

    @Test
    @DisplayName("5. Tenant ADMIN cannot invoke platform onboarding or status update endpoints (returns 403)")
    void testTenantAdminCannotAccessPlatformEndpoints() throws Exception {
        PlatformOnboardingRequest onboardReq = PlatformOnboardingRequest.builder()
                .restaurantName("Hacked Restaurant")
                .adminName("Hacker Admin")
                .adminEmail("hacker@platform.com")
                .adminPassword("Password123!")
                .build();

        mockMvc.perform(post("/api/platform/restaurants")
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardReq)))
                .andExpect(status().isForbidden());

        RestaurantStatusUpdateRequest statusReq = new RestaurantStatusUpdateRequest();
        statusReq.setIsActive(false);

        mockMvc.perform(patch("/api/platform/restaurants/" + restaurant2.getId() + "/status")
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/restaurants")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. SUPER_ADMIN PERMITTED OPERATIONS
    // =========================================================================

    @Test
    @DisplayName("6. SUPER_ADMIN can create a restaurant via POST /api/restaurants (returns 201)")
    void testSuperAdminCanCreateRestaurant() throws Exception {
        RestaurantRequest req = new RestaurantRequest();
        req.setName("Platform New Bistro");
        req.setAddress("42 Platform Way");
        req.setPhone("9123456780");
        req.setEmail("bistro@platform.com");
        req.setGstNumber("GSTINBISTRO");

        mockMvc.perform(post("/api/restaurants")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Platform New Bistro"));
    }

    @Test
    @DisplayName("7. SUPER_ADMIN can delete a restaurant via DELETE /api/restaurants/{id} (returns 204)")
    void testSuperAdminCanDeleteRestaurant() throws Exception {
        Restaurant temp = new Restaurant();
        temp.setName("To Be Deleted");
        temp.setIsActive(true);
        temp = restaurantRepository.save(temp);
        Long tempId = temp.getId();

        mockMvc.perform(delete("/api/restaurants/" + tempId)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isNoContent());

        assertFalse(restaurantRepository.existsById(tempId), "Restaurant must be deleted by SUPER_ADMIN");
    }

    // =========================================================================
    // 5. TENANT ISOLATION ON ORDINARY TENANT ADMIN OPERATIONS (GET & PUT)
    // =========================================================================

    @Test
    @DisplayName("8. Tenant ADMIN can view their own restaurant details, but cross-tenant access returns 403")
    void testTenantAdminGetOwnRestaurantAndCrossTenantForbidden() throws Exception {
        // Own restaurant -> 200 OK
        mockMvc.perform(get("/api/restaurants/" + restaurant1.getId())
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spice Garden"));

        // Other restaurant -> 403 Forbidden
        mockMvc.perform(get("/api/restaurants/" + restaurant2.getId())
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Tenant ADMIN can update their own restaurant profile, but cross-tenant update returns 403")
    void testTenantAdminUpdateOwnRestaurantAndCrossTenantForbidden() throws Exception {
        RestaurantRequest updateReq = new RestaurantRequest();
        updateReq.setName("Spice Garden Updated");
        updateReq.setAddress("999 New Street");
        updateReq.setPhone("9876500000");
        updateReq.setEmail("new@spicegarden.com");
        updateReq.setGstNumber("GSTINNEW1");

        // Own restaurant -> 200 OK
        mockMvc.perform(put("/api/restaurants/" + restaurant1.getId())
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spice Garden Updated"));

        // Other restaurant -> 403 Forbidden
        mockMvc.perform(put("/api/restaurants/" + restaurant2.getId())
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 6. WAITER AND KITCHEN REJECTION
    // =========================================================================

    @Test
    @DisplayName("10. WAITER and KITCHEN roles are rejected with 403 from all restaurant management endpoints")
    void testWaiterAndKitchenRolesRejected() throws Exception {
        mockMvc.perform(get("/api/restaurants/" + restaurant1.getId())
                        .header("Authorization", "Bearer " + waiter1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/restaurants/" + restaurant1.getId())
                        .header("Authorization", "Bearer " + kitchen1Token))
                .andExpect(status().isForbidden());

        RestaurantRequest req = new RestaurantRequest();
        req.setName("Waiter Restaurant");

        mockMvc.perform(post("/api/restaurants")
                        .header("Authorization", "Bearer " + waiter1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/restaurants/" + restaurant1.getId())
                        .header("Authorization", "Bearer " + waiter1Token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 7. DIRECT SERVICE-LAYER DEFENSE IN DEPTH
    // =========================================================================

    @Test
    @DisplayName("11. Direct service calls to createRestaurant and deleteRestaurant fail closed when not SUPER_ADMIN")
    void testDirectServiceLayerFailsClosedWithoutSuperAdmin() {
        RestaurantRequest req = new RestaurantRequest();
        req.setName("Service Direct Attempt");

        // Without authentication in SecurityContext
        SecurityContextHolder.clearContext();
        assertThrows(Exception.class, () -> restaurantService.createRestaurant(req));
        assertThrows(Exception.class, () -> restaurantService.deleteRestaurant(restaurant1.getId()));
    }
}
