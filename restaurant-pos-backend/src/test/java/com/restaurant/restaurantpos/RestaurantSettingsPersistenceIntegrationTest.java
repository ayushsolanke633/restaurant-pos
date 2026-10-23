package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.restaurantpos.dto.RestaurantSettingsRequest;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
public class RestaurantSettingsPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantSettingsRepository settingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Restaurant restaurant1;
    private Restaurant restaurant2;

    private Role adminRole;
    private User admin1;
    private User admin2;
    private String token1;
    private String token2;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        settingsRepository.deleteAll();
        userRepository.deleteAll();
        tableRepository.deleteAll();
        restaurantRepository.deleteAll();

        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));

        restaurant1 = new Restaurant();
        restaurant1.setName("Bistro One");
        restaurant1.setIsActive(true);
        restaurant1 = restaurantRepository.save(restaurant1);

        restaurant2 = new Restaurant();
        restaurant2.setName("Trattoria Two");
        restaurant2.setIsActive(true);
        restaurant2 = restaurantRepository.save(restaurant2);

        admin1 = new User();
        admin1.setName("Admin One");
        admin1.setEmail("admin1@bistroone.com");
        admin1.setPasswordHash(passwordEncoder.encode("password"));
        admin1.setRole(adminRole);
        admin1.setRestaurant(restaurant1);
        admin1.setIsActive(true);
        admin1 = userRepository.save(admin1);
        token1 = jwtService.generateToken(admin1);

        admin2 = new User();
        admin2.setName("Admin Two");
        admin2.setEmail("admin2@trattoria.com");
        admin2.setPasswordHash(passwordEncoder.encode("password"));
        admin2.setRole(adminRole);
        admin2.setRestaurant(restaurant2);
        admin2.setIsActive(true);
        admin2 = userRepository.save(admin2);
        token2 = jwtService.generateToken(admin2);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("1. Default KDS setting is enabled for a new restaurant")
    void testDefaultKdsSettingIsEnabledForNewRestaurant() throws Exception {
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true));
    }

    @Test
    @DisplayName("2. Existing enabled restaurant can disable KDS, GET returns false, remains false on reload, and enabling persists true")
    void testDisableAndReEnableKdsPersistenceLifecycle() throws Exception {
        // Step A: Initially KDS defaults to enabled (true)
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true));

        // Step B: Disable KDS with kitchenEnabled = false
        RestaurantSettingsRequest disableRequest = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false));

        // Step C: GET settings returns false after the update
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false));

        // Step D: The disabled setting remains false after reloading settings (simulating page reload)
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false));

        // Step E: Enabling KDS again persists true
        RestaurantSettingsRequest enableRequest = RestaurantSettingsRequest.builder()
                .kitchenEnabled(true)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enableRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true));

        // Step F: GET settings returns true after re-enabling
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true));
    }

    @Test
    @DisplayName("3. One restaurant's toggle cannot change another restaurant's setting")
    void testOneRestaurantToggleCannotChangeAnotherRestaurantSetting() throws Exception {
        // Restaurant 1: disable KDS
        RestaurantSettingsRequest disableRequest = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false));

        // Verify Restaurant 1 settings GET returns false
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false));

        // Now verify Restaurant 2 GET settings must still be default true (unaffected by Restaurant 1's change)
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true));

        // Switch back to Restaurant 1 to verify it is still false
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false));
    }

    @Test
    @DisplayName("4. Disabling KDS automatically sets Auto-Accept Orders to OFF and persists both values")
    void testDisablingKdsTurnsAutoAcceptOffAndPersistsBothValues() throws Exception {
        // Step A: Initially turn on Auto-Accept while KDS is enabled
        RestaurantSettingsRequest enableAutoAccept = RestaurantSettingsRequest.builder()
                .autoAcceptOrders(true)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enableAutoAccept)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true))
                .andExpect(jsonPath("$.autoAcceptOrders").value(true));

        // Verify GET confirms both are true
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true))
                .andExpect(jsonPath("$.autoAcceptOrders").value(true));

        // Step B: Admin disables KDS (kitchenEnabled = false)
        RestaurantSettingsRequest disableKds = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableKds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));

        // Step C: Verify database entity directly has both kitchenEnabled = false and autoAcceptOrders = false
        var savedSettings = settingsRepository.findByRestaurantId(restaurant1.getId()).orElseThrow();
        assertFalse(savedSettings.getKitchenEnabled());
        assertFalse(savedSettings.getAutoAcceptOrders());
    }

    @Test
    @DisplayName("5. Refreshing settings or reloading keeps both KDS and Auto-Accept toggles OFF")
    void testRefreshingSettingsKeepsBothTogglesOff() throws Exception {
        // Setup: Turn on Auto-Accept and then disable KDS
        RestaurantSettingsRequest enableAutoAccept = RestaurantSettingsRequest.builder()
                .autoAcceptOrders(true)
                .build();
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enableAutoAccept)))
                .andExpect(status().isOk());

        RestaurantSettingsRequest disableKds = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableKds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));

        // First GET (initial fetch)
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));

        // Second GET (simulating user clicking Refresh or navigating back to page)
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));
    }

    @Test
    @DisplayName("6. Re-enabling KDS leaves Auto-Accept OFF by default and does not automatically enable it")
    void testReEnablingKdsDoesNotTurnAutoAcceptOn() throws Exception {
        // Setup: Disable KDS
        RestaurantSettingsRequest disableKds = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableKds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));

        // Re-enable KDS (kitchenEnabled = true, autoAcceptOrders omitted)
        RestaurantSettingsRequest reEnableKds = RestaurantSettingsRequest.builder()
                .kitchenEnabled(true)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reEnableKds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));

        // Verify GET after re-enabling KDS confirms autoAcceptOrders is still false
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));
    }

    @Test
    @DisplayName("7. Auto-Accept cannot be enabled while KDS is disabled")
    void testAutoAcceptCannotBeEnabledWhileKdsIsDisabled() throws Exception {
        // Step A: Disable KDS
        RestaurantSettingsRequest disableKds = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableKds)))
                .andExpect(status().isOk());

        // Step B: Attempt to enable Auto-Accept while KDS is disabled -> should return 400 Bad Request
        RestaurantSettingsRequest enableAutoAccept = RestaurantSettingsRequest.builder()
                .autoAcceptOrders(true)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enableAutoAccept)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Kitchen Display System (KDS) must be enabled")));

        // Step C: Verify DB setting is still false
        var savedSettings = settingsRepository.findByRestaurantId(restaurant1.getId()).orElseThrow();
        assertFalse(savedSettings.getAutoAcceptOrders());

        // Step D: Attempt to simultaneously send kitchenEnabled = false and autoAcceptOrders = true -> should return 400
        RestaurantSettingsRequest invalidCombo = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .autoAcceptOrders(true)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCombo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Kitchen Display System (KDS) must be enabled")));
    }

    @Test
    @DisplayName("8. Other restaurant settings remain unchanged when KDS is disabled and Auto-Accept turns OFF")
    void testOtherRestaurantSettingsRemainUnchangedWhenKdsDisabled() throws Exception {
        // Setup initial custom settings
        RestaurantSettingsRequest initialConfig = RestaurantSettingsRequest.builder()
                .taxEnabled(true)
                .defaultTaxRate(new java.math.BigDecimal("12.50"))
                .serviceChargeEnabled(true)
                .defaultServiceChargeRate(new java.math.BigDecimal("7.00"))
                .currency("USD")
                .timezone("America/New_York")
                .invoicePrefix("BISTRO")
                .receiptHeader("Welcome to Bistro One")
                .receiptFooter("Have a wonderful day!")
                .autoAcceptOrders(true)
                .kitchenEnabled(true)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialConfig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.invoicePrefix").value("BISTRO"))
                .andExpect(jsonPath("$.autoAcceptOrders").value(true))
                .andExpect(jsonPath("$.kitchenEnabled").value(true));

        // Disable KDS only
        RestaurantSettingsRequest disableKds = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();

        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableKds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.invoicePrefix").value("BISTRO"))
                .andExpect(jsonPath("$.defaultTaxRate").value(12.50))
                .andExpect(jsonPath("$.defaultServiceChargeRate").value(7.00))
                .andExpect(jsonPath("$.receiptHeader").value("Welcome to Bistro One"))
                .andExpect(jsonPath("$.receiptFooter").value("Have a wonderful day!"));
    }

    @Test
    @DisplayName("9. Multi-tenant isolation: Restaurant 1 disabling KDS does not affect Restaurant 2 settings")
    void testMultiTenantIsolationForKdsAndAutoAccept() throws Exception {
        // Restaurant 1: Enable Auto-Accept with KDS = true
        RestaurantSettingsRequest r1Config = RestaurantSettingsRequest.builder()
                .kitchenEnabled(true)
                .autoAcceptOrders(true)
                .build();
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r1Config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true))
                .andExpect(jsonPath("$.autoAcceptOrders").value(true));

        // Restaurant 2: Enable Auto-Accept with KDS = true
        RestaurantSettingsRequest r2Config = RestaurantSettingsRequest.builder()
                .kitchenEnabled(true)
                .autoAcceptOrders(true)
                .build();
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r2Config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true))
                .andExpect(jsonPath("$.autoAcceptOrders").value(true));

        // Restaurant 1: Disable KDS (auto-accept turns OFF for R1)
        RestaurantSettingsRequest r1Disable = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();
        mockMvc.perform(put("/api/settings")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r1Disable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));

        // Restaurant 2 GET: must STILL be KDS = true, autoAcceptOrders = true!
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(true))
                .andExpect(jsonPath("$.autoAcceptOrders").value(true));

        // Restaurant 1 GET: still KDS = false, autoAcceptOrders = false
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchenEnabled").value(false))
                .andExpect(jsonPath("$.autoAcceptOrders").value(false));
    }
}
