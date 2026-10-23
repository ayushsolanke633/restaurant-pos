package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RealDeviceLoginTenantIsolationE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Restaurant restaurantAmbika;
    private Restaurant restaurantB;

    private Role adminRole;
    private Role waiterRole;
    private Role kitchenRole;
    private Role superAdminRole;

    private User adminAmbika;
    private User waiterAmbika;
    private User kitchenAmbika;
    private User adminB;
    private User waiterB;
    private User superAdmin;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        restaurantTableRepository.deleteAll();
        restaurantRepository.deleteAll();

        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));
        waiterRole = roleRepository.findByName("WAITER")
                .orElseGet(() -> roleRepository.save(new Role(null, "WAITER")));
        kitchenRole = roleRepository.findByName("KITCHEN")
                .orElseGet(() -> roleRepository.save(new Role(null, "KITCHEN")));
        superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "SUPER_ADMIN")));

        restaurantAmbika = new Restaurant();
        restaurantAmbika.setName("Restaurant Ambika");
        restaurantAmbika.setIsActive(true);
        restaurantAmbika = restaurantRepository.save(restaurantAmbika);

        restaurantB = new Restaurant();
        restaurantB.setName("Restaurant B");
        restaurantB.setIsActive(true);
        restaurantB = restaurantRepository.save(restaurantB);

        adminAmbika = new User();
        adminAmbika.setName("Ambika Admin");
        adminAmbika.setEmail("admin@ambika.com");
        adminAmbika.setPasswordHash(passwordEncoder.encode("adminPass123"));
        adminAmbika.setRole(adminRole);
        adminAmbika.setRestaurant(restaurantAmbika);
        adminAmbika.setIsActive(true);
        adminAmbika = userRepository.save(adminAmbika);

        waiterAmbika = new User();
        waiterAmbika.setName("Ambika Waiter");
        waiterAmbika.setEmail("waiter@ambika.com");
        waiterAmbika.setPasswordHash(passwordEncoder.encode("waiterPass123"));
        waiterAmbika.setRole(waiterRole);
        waiterAmbika.setRestaurant(restaurantAmbika);
        waiterAmbika.setIsActive(true);
        waiterAmbika = userRepository.save(waiterAmbika);

        kitchenAmbika = new User();
        kitchenAmbika.setName("Ambika Kitchen");
        kitchenAmbika.setEmail("kitchen@ambika.com");
        kitchenAmbika.setPasswordHash(passwordEncoder.encode("kitchenPass123"));
        kitchenAmbika.setRole(kitchenRole);
        kitchenAmbika.setRestaurant(restaurantAmbika);
        kitchenAmbika.setIsActive(true);
        kitchenAmbika = userRepository.save(kitchenAmbika);

        adminB = new User();
        adminB.setName("Restaurant B Admin");
        adminB.setEmail("admin@restaurantb.com");
        adminB.setPasswordHash(passwordEncoder.encode("adminBPass123"));
        adminB.setRole(adminRole);
        adminB.setRestaurant(restaurantB);
        adminB.setIsActive(true);
        adminB = userRepository.save(adminB);

        waiterB = new User();
        waiterB.setName("Restaurant B Waiter");
        waiterB.setEmail("waiter@restaurantb.com");
        waiterB.setPasswordHash(passwordEncoder.encode("waiterBPass123"));
        waiterB.setRole(waiterRole);
        waiterB.setRestaurant(restaurantB);
        waiterB.setIsActive(true);
        waiterB = userRepository.save(waiterB);

        superAdmin = new User();
        superAdmin.setName("Super Admin");
        superAdmin.setEmail("super@platform.com");
        superAdmin.setPasswordHash(passwordEncoder.encode("superPass123"));
        superAdmin.setRole(superAdminRole);
        superAdmin.setRestaurant(null);
        superAdmin.setIsActive(true);
        superAdmin = userRepository.save(superAdmin);

        RestaurantTable tableAmbika = new RestaurantTable();
        tableAmbika.setRestaurant(restaurantAmbika);
        tableAmbika.setTableNumber(1);
        tableAmbika.setCapacity(4);
        tableAmbika.setStatus("AVAILABLE");
        restaurantTableRepository.save(tableAmbika);
    }

    @Test
    @DisplayName("Ambika waiter logs in with credentials and accesses Ambika tables")
    void testAmbikaWaiterLoginAndAccessTables() throws Exception {
        LoginRequest req = new LoginRequest("waiter@ambika.com", "waiterPass123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("WAITER"))
                .andExpect(jsonPath("$.restaurantId").value(restaurantAmbika.getId()))
                .andReturn();

        Map<?, ?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        String jwtToken = (String) resp.get("token");

        mockMvc.perform(get("/api/tables")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Admin and Kitchen staff log in successfully with standard credentials")
    void testAdminAndKitchenLoginSuccess() throws Exception {
        LoginRequest adminReq = new LoginRequest("admin@ambika.com", "adminPass123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.restaurantId").value(restaurantAmbika.getId()));

        LoginRequest kitchenReq = new LoginRequest("kitchen@ambika.com", "kitchenPass123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kitchenReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("KITCHEN"))
                .andExpect(jsonPath("$.restaurantId").value(restaurantAmbika.getId()));
    }

    @Test
    @DisplayName("Restaurant B waiter cannot access Restaurant Ambika data")
    void testRestaurantBWaiterCannotAccessAmbikaData() throws Exception {
        LoginRequest reqB = new LoginRequest("waiter@restaurantb.com", "waiterBPass123");

        MvcResult resultB = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurantB.getId()))
                .andReturn();

        Map<?, ?> respB = objectMapper.readValue(resultB.getResponse().getContentAsString(), Map.class);
        String waiterBToken = (String) respB.get("token");

        // Waiter B gets empty list for Restaurant B tables, never sees Ambika's table 1
        MvcResult tablesResult = mockMvc.perform(get("/api/tables")
                        .header("Authorization", "Bearer " + waiterBToken))
                .andExpect(status().isOk())
                .andReturn();

        String tablesContent = tablesResult.getResponse().getContentAsString();
        assertEquals("[]", tablesContent.trim());
    }

    @Test
    @DisplayName("Super admin logs in without restaurant context")
    void testSuperAdminLogin() throws Exception {
        LoginRequest req = new LoginRequest("super@platform.com", "superPass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.restaurantId").doesNotExist());
    }

    @Test
    @DisplayName("Deactivated user login is rejected")
    void testDeactivatedUserLoginRejected() throws Exception {
        waiterAmbika.setIsActive(false);
        userRepository.save(waiterAmbika);

        LoginRequest req = new LoginRequest("waiter@ambika.com", "waiterPass123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
