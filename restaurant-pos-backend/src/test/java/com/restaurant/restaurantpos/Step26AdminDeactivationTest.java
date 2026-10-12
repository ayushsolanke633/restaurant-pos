package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.UserRequest;
import com.restaurant.restaurantpos.dto.UserResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Step26AdminDeactivationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private UserService userService;

    private Restaurant restaurant;
    private Role adminRole;
    private Role waiterRole;
    private Role kitchenRole;

    private User adminUser1;
    private User adminUser2;
    private User waiterUser;
    private User kitchenUser;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setName("ADMIN");

        waiterRole = new Role();
        waiterRole.setId(2L);
        waiterRole.setName("WAITER");

        kitchenRole = new Role();
        kitchenRole.setId(3L);
        kitchenRole.setName("KITCHEN");

        adminUser1 = new User();
        adminUser1.setId(10L);
        adminUser1.setRestaurant(restaurant);
        adminUser1.setRole(adminRole);
        adminUser1.setName("Admin One");
        adminUser1.setEmail("admin1@test.com");
        adminUser1.setIsActive(true);

        adminUser2 = new User();
        adminUser2.setId(11L);
        adminUser2.setRestaurant(restaurant);
        adminUser2.setRole(adminRole);
        adminUser2.setName("Admin Two");
        adminUser2.setEmail("admin2@test.com");
        adminUser2.setIsActive(true);

        waiterUser = new User();
        waiterUser.setId(20L);
        waiterUser.setRestaurant(restaurant);
        waiterUser.setRole(waiterRole);
        waiterUser.setName("Waiter One");
        waiterUser.setEmail("waiter1@test.com");
        waiterUser.setIsActive(true);

        kitchenUser = new User();
        kitchenUser.setId(30L);
        kitchenUser.setRestaurant(restaurant);
        kitchenUser.setRole(kitchenRole);
        kitchenUser.setName("Kitchen One");
        kitchenUser.setEmail("kitchen1@test.com");
        kitchenUser.setIsActive(true);

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
    }

    // 1. ADMIN cannot be deactivated via deactivateUser endpoint
    @Test
    void testAdminCannotBeDeactivated() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser1));
        doNothing().when(securityUtils).validateTenantAccess(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.deactivateUser(10L));
        assertTrue(ex.getMessage().contains("ADMIN users cannot be deactivated"));
        verify(userRepository, never()).save(any(User.class));
    }

    // 2. ADMIN cannot deactivate themselves or another ADMIN via updateUser
    @Test
    void testAdminCannotDeactivateSelfOrOtherAdminViaUpdate() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser1));
        doNothing().when(securityUtils).validateTenantAccess(1L);

        UserRequest req = new UserRequest();
        req.setIsActive(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.updateUser(10L, req));
        assertTrue(ex.getMessage().contains("ADMIN users cannot be deactivated"));
        verify(userRepository, never()).save(any(User.class));
    }

    // 3. WAITER can still be deactivated by authorized ADMIN
    @Test
    void testWaiterCanBeDeactivated() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(waiterUser));
        doNothing().when(securityUtils).validateTenantAccess(1L);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.deactivateUser(20L);

        assertNotNull(res);
        assertFalse(res.getIsActive());
        assertEquals("WAITER", res.getRoleName());
        verify(userRepository).save(waiterUser);
    }

    // 4. KITCHEN can still be deactivated by authorized ADMIN
    @Test
    void testKitchenCanBeDeactivated() {
        when(userRepository.findById(30L)).thenReturn(Optional.of(kitchenUser));
        doNothing().when(securityUtils).validateTenantAccess(1L);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.deactivateUser(30L);

        assertNotNull(res);
        assertFalse(res.getIsActive());
        assertEquals("KITCHEN", res.getRoleName());
        verify(userRepository).save(kitchenUser);
    }

    // 5. Existing inactive-user activation behavior still works
    @Test
    void testReactivateInactiveUser() {
        waiterUser.setIsActive(false);
        when(userRepository.findById(20L)).thenReturn(Optional.of(waiterUser));
        doNothing().when(securityUtils).validateTenantAccess(1L);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserRequest req = new UserRequest();
        req.setIsActive(true);

        UserResponse res = userService.updateUser(20L, req);

        assertNotNull(res);
        assertTrue(res.getIsActive());
        verify(userRepository).save(waiterUser);
    }
}