package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderTableConsistencyTest {

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

    @InjectMocks
    private OrderService orderService;

    private Restaurant restaurant1;
    private Restaurant restaurant2;
    private RestaurantTable tableId7Num2;
    private Order dineInOrder89;
    private Order takeawayOrder90;

    @BeforeEach
    void setUp() {
        restaurant1 = new Restaurant();
        restaurant1.setId(1L);
        restaurant1.setName("Test Restaurant One");

        restaurant2 = new Restaurant();
        restaurant2.setId(2L);
        restaurant2.setName("Test Restaurant Two");

        // Table with primary key ID 7, but business tableNumber 2
        tableId7Num2 = new RestaurantTable();
        tableId7Num2.setId(7L);
        tableId7Num2.setRestaurant(restaurant1);
        tableId7Num2.setTableNumber(2);
        tableId7Num2.setCapacity(4);
        tableId7Num2.setStatus("OCCUPIED");

        // Dine-In Order #89 linked to tableId7Num2 (ID = 7, TableNumber = 2)
        dineInOrder89 = new Order();
        dineInOrder89.setId(89L);
        dineInOrder89.setRestaurant(restaurant1);
        dineInOrder89.setTable(tableId7Num2);
        dineInOrder89.setOrderType("DINE_IN");
        dineInOrder89.setStatus("NEW");
        dineInOrder89.setSubtotal(new BigDecimal("150.00"));
        dineInOrder89.setTaxAmount(BigDecimal.ZERO);
        dineInOrder89.setDiscountAmount(BigDecimal.ZERO);
        dineInOrder89.setTotalAmount(new BigDecimal("150.00"));
        dineInOrder89.setCreatedAt(LocalDateTime.now());
        dineInOrder89.setUpdatedAt(LocalDateTime.now());

        // Takeaway Order #90 with null table
        takeawayOrder90 = new Order();
        takeawayOrder90.setId(90L);
        takeawayOrder90.setRestaurant(restaurant1);
        takeawayOrder90.setTable(null);
        takeawayOrder90.setOrderType("TAKEAWAY");
        takeawayOrder90.setStatus("NEW");
        takeawayOrder90.setSubtotal(new BigDecimal("200.00"));
        takeawayOrder90.setTaxAmount(BigDecimal.ZERO);
        takeawayOrder90.setDiscountAmount(BigDecimal.ZERO);
        takeawayOrder90.setTotalAmount(new BigDecimal("200.00"));
        takeawayOrder90.setCreatedAt(LocalDateTime.now());
        takeawayOrder90.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Dine-In order returns tableId = 7 and tableNumber = 2, NEVER tableNumber = 7")
    void testDineInOrderReturnsCorrectTableIdAndTableNumber() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(orderRepository.findById(89L)).thenReturn(Optional.of(dineInOrder89));
        when(orderItemRepository.findByOrderId(89L)).thenReturn(Collections.emptyList());

        OrderResponse response = orderService.getOrderById(89L);

        assertNotNull(response);
        assertEquals(89L, response.getId());
        assertEquals(7L, response.getTableId(), "tableId must reference restaurant_tables.id (7)");
        assertEquals(2, response.getTableNumber(), "tableNumber must be the business tableNumber (2)");
        assertNotEquals(7, response.getTableNumber(), "tableNumber must NEVER be the table ID (7)");
    }

    @Test
    @DisplayName("Takeaway order returns tableId = null and tableNumber = null")
    void testTakeawayOrderReturnsNullTableFields() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(orderRepository.findById(90L)).thenReturn(Optional.of(takeawayOrder90));
        when(orderItemRepository.findByOrderId(90L)).thenReturn(Collections.emptyList());

        OrderResponse response = orderService.getOrderById(90L);

        assertNotNull(response);
        assertEquals(90L, response.getId());
        assertEquals("TAKEAWAY", response.getOrderType());
        assertNull(response.getTableId(), "TAKEAWAY order tableId must be null");
        assertNull(response.getTableNumber(), "TAKEAWAY order tableNumber must be null");
    }

    @Test
    @DisplayName("Verify tenant isolation when getting order by ID")
    void testTenantIsolationForOrders() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Order foreignOrder = new Order();
        foreignOrder.setId(99L);
        foreignOrder.setRestaurant(restaurant2);
        foreignOrder.setTable(tableId7Num2);

        when(orderRepository.findById(99L)).thenReturn(Optional.of(foreignOrder));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.getOrderById(99L));
        assertTrue(ex.getMessage().contains("not found"));
    }
}
