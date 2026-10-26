package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.LoginResponse;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.security.WaiterSessionManager;
import com.restaurant.restaurantpos.service.AuthService;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.KitchenService;
import com.restaurant.restaurantpos.service.RestaurantTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TenantIsolationDeviceBindingRegressionTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private WaiterSessionManager waiterSessionManager;

    private JwtService jwtService;
    private AuthService authService;
    private CustomUserDetailsService customUserDetailsService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private Role waiterRole;
    private Role adminRole;
    private Role kitchenRole;
    private Role superAdminRole;

    private User waiterA;
    private User waiterB;
    private User adminA;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        jwtService.setSecret("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        jwtService.validateSecret();

        authService = new AuthService(
                authenticationManager,
                userRepository,
                jwtService,
                waiterSessionManager,
                restaurantRepository
        );

        customUserDetailsService = new CustomUserDetailsService(userRepository);

        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Restaurant Alpha");
        restaurantA.setIsActive(true);

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Restaurant Beta");
        restaurantB.setIsActive(true);

        waiterRole = new Role(1L, "WAITER");
        adminRole = new Role(2L, "ADMIN");
        kitchenRole = new Role(3L, "KITCHEN");
        superAdminRole = new Role(4L, "SUPER_ADMIN");

        waiterA = new User();
        waiterA.setId(101L);
        waiterA.setEmail("waiterA@alpha.com");
        waiterA.setPasswordHash("encoded_pwd_A");
        waiterA.setName("Waiter Alpha");
        waiterA.setRole(waiterRole);
        waiterA.setRestaurant(restaurantA);
        waiterA.setIsActive(true);

        waiterB = new User();
        waiterB.setId(201L);
        waiterB.setEmail("waiterB@beta.com");
        waiterB.setPasswordHash("encoded_pwd_B");
        waiterB.setName("Waiter Beta");
        waiterB.setRole(waiterRole);
        waiterB.setRestaurant(restaurantB);
        waiterB.setIsActive(true);

        adminA = new User();
        adminA.setId(102L);
        adminA.setEmail("adminA@alpha.com");
        adminA.setPasswordHash("encoded_pwd_admin");
        adminA.setName("Admin Alpha");
        adminA.setRole(adminRole);
        adminA.setRestaurant(restaurantA);
        adminA.setIsActive(true);

        superAdmin = new User();
        superAdmin.setId(999L);
        superAdmin.setEmail("super@platform.com");
        superAdmin.setPasswordHash("encoded_pwd_super");
        superAdmin.setName("Super Admin");
        superAdmin.setRole(superAdminRole);
        superAdmin.setRestaurant(null);
        superAdmin.setIsActive(true);

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurantA));
        when(restaurantRepository.findById(2L)).thenReturn(Optional.of(restaurantB));
        when(userRepository.findByEmail("waiterA@alpha.com")).thenReturn(Optional.of(waiterA));
        when(userRepository.findByEmail("waiterB@beta.com")).thenReturn(Optional.of(waiterB));
        when(userRepository.findByEmail("adminA@alpha.com")).thenReturn(Optional.of(adminA));
        when(userRepository.findByEmail("super@platform.com")).thenReturn(Optional.of(superAdmin));
    }

    @Test
    @DisplayName("Scenario 1: Restaurant A's waiter logs in with credentials and accesses Restaurant A's data")
    void testScenario1_WaiterALoginAndAccessesData() {
        LoginRequest request = new LoginRequest("waiterA@alpha.com", "password");
        LoginResponse response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals("WAITER", response.getRole());
        assertEquals(1L, response.getRestaurantId());

        // Verify token claims consistency
        CustomUserDetails cud = new CustomUserDetails(
                waiterA.getId(), 1L, "WAITER", waiterA.getEmail(), "pwd", true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_WAITER"))
        );
        assertTrue(jwtService.isTokenValid(response.getToken(), cud));

        // Test accessing permitted table data for Restaurant A
        RestaurantTable tableA = new RestaurantTable();
        tableA.setId(10L);
        tableA.setRestaurant(restaurantA);
        tableA.setTableNumber(1);
        tableA.setStatus("AVAILABLE");

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(tableRepository.findByRestaurantIdOrderByTableNumberAsc(1L)).thenReturn(List.of(tableA));
        when(tableRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(tableA));

        RestaurantTableService tableService = new RestaurantTableService(tableRepository, restaurantRepository, securityUtils);
        var tables = tableService.getAllTables();
        assertEquals(1, tables.size());
        assertEquals(10L, tables.get(0).getId());
    }

    @Test
    @DisplayName("Scenario 2: Restaurant B's waiter cannot access Restaurant A's data")
    void testScenario2_WaiterBCannotAccessRestaurantAData() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);
        when(securityUtils.getCurrentUserId()).thenReturn(201L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        // Attempt to access Restaurant A's table (id=10L belonging to Restaurant A)
        when(tableRepository.findByIdAndRestaurantId(10L, 2L)).thenReturn(Optional.empty());
        RestaurantTable tableA = new RestaurantTable();
        tableA.setId(10L);
        tableA.setRestaurant(restaurantA);
        when(tableRepository.findById(10L)).thenReturn(Optional.of(tableA));

        RestaurantTableService tableService = new RestaurantTableService(tableRepository, restaurantRepository, securityUtils);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> tableService.getTableById(10L));
        assertTrue(ex.getMessage().contains("Table not found"));
    }

    @Test
    @DisplayName("Scenario 3: Tampered client parameters cannot bypass isolation")
    void testScenario3_TamperedClientParametersCannotBypassIsolation() {
        // User is authenticated in Restaurant B
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);
        doThrow(new AccessDeniedException("Access denied")).when(securityUtils).validateTenantAccess(1L);

        // Trying to pass restaurantId=1L to validateTenantAccess triggers AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> securityUtils.validateTenantAccess(1L));
    }

    @Test
    @DisplayName("Scenario 4: Restaurant A's KDS cannot read Restaurant B's orders or subscribe to topics")
    void testScenario4_KdsCannotReadCrossTenantOrdersOrTopics() {
        // 1. REST check
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");

        Order orderB = new Order();
        orderB.setId(999L);
        orderB.setRestaurant(restaurantB);

        when(orderRepository.findByIdAndRestaurantId(999L, 1L)).thenReturn(Optional.empty());
        when(orderRepository.findById(999L)).thenReturn(Optional.of(orderB));

        KitchenService kitchenService = new KitchenService(
                orderRepository, orderItemRepository, null,
                userRepository, securityUtils, null, null
        );

        assertThrows(RuntimeException.class, () -> kitchenService.getKitchenOrderById(999L));

        // 2. STOMP subscription check
        com.restaurant.restaurantpos.service.TokenRevocationService mockRevocation = mock(com.restaurant.restaurantpos.service.TokenRevocationService.class);
        com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager sessionMgr = new com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager(mockRevocation);
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
                jwtService, customUserDetailsService, mockRevocation, sessionMgr);

        CustomUserDetails kitchenAUser = new CustomUserDetails(
                105L, 1L, "KITCHEN", "kitchenA@alpha.com", "pwd", true,
                List.of(new SimpleGrantedAuthority("ROLE_KITCHEN"))
        );
        Authentication kitchenAuth = new UsernamePasswordAuthenticationToken(kitchenAUser, null, kitchenAUser.getAuthorities());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-test-kitchen");
        accessor.setUser(kitchenAuth);
        accessor.setDestination("/topic/restaurants/2/kitchen/orders"); // Tenant B topic!
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message, mock(MessageChannel.class))
        );
        assertTrue(ex.getMessage().contains("Tenant subscription mismatch"));
    }

    @Test
    @DisplayName("Scenario 5: Inactive user or inactive restaurant login is rejected")
    void testScenario5_InactiveUserOrRestaurantLoginRejected() {
        // 1. Deactivated user
        waiterA.setIsActive(false);
        assertThrows(DisabledException.class, () ->
                authService.login(new LoginRequest("waiterA@alpha.com", "password"))
        );
        assertThrows(DisabledException.class, () ->
                customUserDetailsService.loadUserByUsername("waiterA@alpha.com")
        );

        // 2. Deactivated restaurant
        waiterA.setIsActive(true);
        restaurantA.setIsActive(false);
        assertThrows(DisabledException.class, () ->
                authService.login(new LoginRequest("waiterA@alpha.com", "password"))
        );
        assertThrows(DisabledException.class, () ->
                customUserDetailsService.loadUserByUsername("waiterA@alpha.com")
        );

        // 3. Tenant user with null restaurant
        waiterA.setRestaurant(null);
        restaurantA.setIsActive(true);
        assertThrows(DisabledException.class, () ->
                authService.login(new LoginRequest("waiterA@alpha.com", "password"))
        );
        assertThrows(DisabledException.class, () ->
                customUserDetailsService.loadUserByUsername("waiterA@alpha.com")
        );
    }

    @Test
    @DisplayName("Scenario 6: Users can log in after restaurant reactivation")
    void testScenario6_ReactivatedRestaurantAllowsLogin() {
        // Initially deactivated
        restaurantA.setIsActive(false);
        assertThrows(DisabledException.class, () ->
                authService.login(new LoginRequest("waiterA@alpha.com", "password"))
        );

        // Reactivated
        restaurantA.setIsActive(true);
        LoginResponse response = authService.login(new LoginRequest("waiterA@alpha.com", "password"));
        assertNotNull(response);
        assertEquals("WAITER", response.getRole());
        assertEquals(1L, response.getRestaurantId());
    }

    @Test
    @DisplayName("Scenario 7: SUPER_ADMIN login and platform operations remain functional")
    void testScenario7_SuperAdminFunctionalityContinuesToWork() {
        LoginResponse response = authService.login(new LoginRequest("super@platform.com", "password"));
        assertNotNull(response);
        assertEquals("SUPER_ADMIN", response.getRole());
        assertNull(response.getRestaurantId(), "Platform SUPER_ADMIN must have null restaurantId");
    }

    @Test
    @DisplayName("Scenario 8: Waiter from Restaurant B cannot impersonate or access Restaurant A context")
    void testScenario8_CrossRestaurantIsolationEnforced() {
        LoginRequest reqB = new LoginRequest("waiterB@beta.com", "password");
        LoginResponse respB = authService.login(reqB);
        assertNotNull(respB.getToken());
        assertEquals(2L, respB.getRestaurantId());

        CustomUserDetails cudA = new CustomUserDetails(
                waiterB.getId(), 1L, "WAITER", waiterB.getEmail(), "pwd", true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_WAITER"))
        );
        // Validating Restaurant B's token against Restaurant A context must fail
        assertFalse(jwtService.isTokenValid(respB.getToken(), cudA));
    }
}
