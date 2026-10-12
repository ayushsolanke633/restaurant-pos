package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.NotificationService;
import com.restaurant.restaurantpos.service.OrderService;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminOrderSortingTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private KitchenOrderEventPublisher eventPublisher;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BillRepository billRepository;

    @InjectMocks
    private OrderService orderService;

    private Restaurant mockRestaurant;

    @BeforeEach
    void setUp() {
        mockRestaurant = new Restaurant();
        mockRestaurant.setId(1L);
    }

    @Test
    @DisplayName("Admin getAllOrders returns orders sorted by createdAt DESC (newest first, older last)")
    void getAllOrders_ReturnsNewestFirst_UsingCreatedAtDesc() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        LocalDateTime now = LocalDateTime.now();

        // Order A: id = 100, created 2 hours ago
        Order orderA = new Order();
        orderA.setId(100L);
        orderA.setRestaurant(mockRestaurant);
        orderA.setCreatedAt(now.minusHours(2));
        orderA.setStatus("COMPLETED");

        // Order B: id = 20, created 10 minutes ago (NEWER than A despite smaller ID)
        Order orderB = new Order();
        orderB.setId(20L);
        orderB.setRestaurant(mockRestaurant);
        orderB.setCreatedAt(now.minusMinutes(10));
        orderB.setStatus("NEW");

        // Mock repository returning Order B first, then Order A (createdAt DESC)
        when(orderRepository.findByRestaurantIdOrderByCreatedAtDesc(1L))
                .thenReturn(Arrays.asList(orderB, orderA));

        when(orderItemRepository.findByOrderId(anyLong())).thenReturn(Collections.emptyList());

        List<OrderResponse> result = orderService.getAllOrders(null, null);

        assertNotNull(result);
        assertEquals(2, result.size());
        // First order must be Order B (id 20, newer timestamp)
        assertEquals(20L, result.get(0).getId());
        assertEquals(now.minusMinutes(10), result.get(0).getCreatedAt());

        // Second order must be Order A (id 100, older timestamp)
        assertEquals(100L, result.get(1).getId());
        assertEquals(now.minusHours(2), result.get(1).getCreatedAt());

        verify(orderRepository).findByRestaurantIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("Order ID does NOT determine ordering; createdAt timestamp controls position")
    void getAllOrders_OrderIdDoesNotDetermineOrdering() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        LocalDateTime now = LocalDateTime.now();

        // Order 1: ID = 500, created yesterday
        Order oldOrderHighId = new Order();
        oldOrderHighId.setId(500L);
        oldOrderHighId.setRestaurant(mockRestaurant);
        oldOrderHighId.setCreatedAt(now.minusDays(1));

        // Order 2: ID = 5, created today
        Order newOrderLowId = new Order();
        newOrderLowId.setId(5L);
        newOrderLowId.setRestaurant(mockRestaurant);
        newOrderLowId.setCreatedAt(now);

        when(orderRepository.findByRestaurantIdOrderByCreatedAtDesc(1L))
                .thenReturn(Arrays.asList(newOrderLowId, oldOrderHighId));
        when(orderItemRepository.findByOrderId(anyLong())).thenReturn(Collections.emptyList());

        List<OrderResponse> result = orderService.getAllOrders(null, null);

        assertEquals(5L, result.get(0).getId());
        assertEquals(500L, result.get(1).getId());
    }

    @Test
    @DisplayName("Order status does NOT affect reverse chronological ordering")
    void getAllOrders_StatusDoesNotAffectOrdering() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        LocalDateTime now = LocalDateTime.now();

        Order recentCompleted = new Order();
        recentCompleted.setId(1L);
        recentCompleted.setRestaurant(mockRestaurant);
        recentCompleted.setCreatedAt(now.minusMinutes(5));
        recentCompleted.setStatus("COMPLETED");

        Order olderNewOrder = new Order();
        olderNewOrder.setId(2L);
        olderNewOrder.setRestaurant(mockRestaurant);
        olderNewOrder.setCreatedAt(now.minusMinutes(30));
        olderNewOrder.setStatus("NEW");

        when(orderRepository.findByRestaurantIdOrderByCreatedAtDesc(1L))
                .thenReturn(Arrays.asList(recentCompleted, olderNewOrder));
        when(orderItemRepository.findByOrderId(anyLong())).thenReturn(Collections.emptyList());

        List<OrderResponse> result = orderService.getAllOrders(null, null);

        assertEquals(1L, result.get(0).getId());
        assertEquals("COMPLETED", result.get(0).getStatus());
        assertEquals(2L, result.get(1).getId());
        assertEquals("NEW", result.get(1).getStatus());
    }

    @Test
    @DisplayName("Tenant isolation remains intact when querying orders with filters")
    void getAllOrders_PreservesTenantIsolation() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        when(orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(2L, "READY"))
                .thenReturn(Collections.emptyList());

        List<OrderResponse> result = orderService.getAllOrders("READY", null);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(orderRepository).findByRestaurantIdAndStatusOrderByCreatedAtDesc(2L, "READY");
        verify(orderRepository, never()).findByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, "READY");
    }
}
