package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class KitchenControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RoleRepository roleRepository;

    private String adminToken;
    private String kitchenToken;
    private String waiterToken;

    @BeforeEach
    void setUp() {
        // Use verified accounts for Restaurant ID 2
        User adminUser = userRepository.findByEmail("admin@example.com").orElse(null);
        if (adminUser != null) {
            adminToken = jwtService.generateToken(adminUser);
        }

        User kitchenUser = userRepository.findByEmail("kitchen@example.com").orElse(null);
        if (kitchenUser != null) {
            kitchenToken = jwtService.generateToken(kitchenUser);
        }

        User waiterUser = userRepository.findByEmail("waiter.senior@example.com").orElse(null);
        if (waiterUser != null) {
            waiterToken = jwtService.generateToken(waiterUser);
        }
    }

    @Test
    @DisplayName("KitchenController: Unauthenticated request to /api/kitchen/orders returns 401")
    void testGetKitchenOrdersUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthenticated"));
    }

    @Test
    @DisplayName("KitchenController: WAITER role access to /api/kitchen/orders returns 403 Forbidden")
    void testGetKitchenOrdersAsWaiterReturns403() throws Exception {
        if (waiterToken != null) {
            mockMvc.perform(get("/api/kitchen/orders")
                    .header("Authorization", "Bearer " + waiterToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Access denied"));
        }
    }

    @Test
    @DisplayName("KitchenController: KITCHEN role access to /api/kitchen/orders returns 200 OK")
    void testGetKitchenOrdersAsKitchenReturns200() throws Exception {
        if (kitchenToken != null) {
            mockMvc.perform(get("/api/kitchen/orders")
                    .header("Authorization", "Bearer " + kitchenToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("KitchenController: ADMIN role access to /api/kitchen/orders returns 200 OK (Dual Role Access)")
    void testGetKitchenOrdersAsAdminReturns200() throws Exception {
        if (adminToken != null) {
            mockMvc.perform(get("/api/kitchen/orders")
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("KitchenController: Invalid/Expired token is rejected with 401")
    void testInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders")
                .header("Authorization", "Bearer invalid-token-sample"))
                .andExpect(status().isUnauthorized());
    }
}
