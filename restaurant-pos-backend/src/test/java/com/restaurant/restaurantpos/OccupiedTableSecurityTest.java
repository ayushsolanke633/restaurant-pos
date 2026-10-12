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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OccupiedTableSecurityTest {

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
    private RestaurantTable tablePK8;
    private Order activeOrder23;

    @BeforeEach
    void setUp() {
        restaurant1 = new Restaurant();
        restaurant1.setId(1L);
        restaurant1.setName("Restaurant One");

        restaurant2 = new Restaurant();
        restaurant2.setId(2L);
        restaurant2.setName("Restaurant Two");

        tablePK8 = new RestaurantTable();
        tablePK8.setId(8L);
        tablePK8.setRestaurant(restaurant1);
        tablePK8.setTableNumber(3451);
        tablePK8.setCapacity(4);
        tablePK8.setStatus("OCCUPIED");

        activeOrder23 = new Order();
        activeOrder23.setId(23L);
        activeOrder23.setRestaurant(restaurant1);
        activeOrder23.setTable(tablePK8);
        activeOrder23.setOrderType("DINE_IN");
        activeOrder23.setStatus("NEW");
        activeOrder23.setSubtotal(new BigDecimal("250.00"));
        activeOrder23.setTaxAmount(BigDecimal.ZERO);
        activeOrder23.setDiscountAmount(BigDecimal.ZERO);
        activeOrder23.setTotalAmount(new BigDecimal("250.00"));
        activeOrder23.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testGetActiveOrderByTableByPrimaryKeySuccess() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(tableRepository.findByIdAndRestaurantId(8L, 1L)).thenReturn(Optional.of(tablePK8));
        when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(8L), eq("DINE_IN"), anyCollection()
        )).thenReturn(Optional.of(activeOrder23));
        when(orderItemRepository.findByOrderId(23L)).thenReturn(Collections.emptyList());

        OrderResponse res = orderService.getActiveOrderByTable(8L);

        assertNotNull(res);
        assertEquals(23L, res.getId());
        assertEquals(8L, res.getTableId());
        assertEquals("DINE_IN", res.getOrderType());
    }

    @Test
    void testGetActiveOrderByTableByTableNumberFallbackSuccess() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(tableRepository.findByIdAndRestaurantId(3451L, 1L)).thenReturn(Optional.empty());
        when(tableRepository.findByRestaurantIdAndTableNumber(1L, 3451)).thenReturn(Optional.of(tablePK8));
        when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(8L), eq("DINE_IN"), anyCollection()
        )).thenReturn(Optional.of(activeOrder23));
        when(orderItemRepository.findByOrderId(23L)).thenReturn(Collections.emptyList());

        OrderResponse res = orderService.getActiveOrderByTable(3451L);

        assertNotNull(res);
        assertEquals(23L, res.getId());
        assertEquals(8L, res.getTableId());
        assertEquals("DINE_IN", res.getOrderType());
    }

    @Test
    void testGetActiveOrderByTableNoActiveOrderThrowsException() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(tableRepository.findByIdAndRestaurantId(8L, 1L)).thenReturn(Optional.of(tablePK8));
        when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(8L), eq("DINE_IN"), anyCollection()
        )).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.getActiveOrderByTable(8L));
        assertTrue(ex.getMessage().contains("No active order found"));
    }

    @Test
    void testGetActiveOrderByTableTableNotFoundThrowsException() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(tableRepository.findByIdAndRestaurantId(999L, 1L)).thenReturn(Optional.empty());
        when(tableRepository.findByRestaurantIdAndTableNumber(1L, 999)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.getActiveOrderByTable(999L));
        assertTrue(ex.getMessage().contains("Table not found"));
    }

    @Test
    void testTenantAccessValidationFailure() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        RestaurantTable otherTenantTable = new RestaurantTable();
        otherTenantTable.setId(50L);
        otherTenantTable.setRestaurant(restaurant2);
        otherTenantTable.setTableNumber(500);

        when(tableRepository.findByIdAndRestaurantId(50L, 1L)).thenReturn(Optional.empty());
        when(tableRepository.findByRestaurantIdAndTableNumber(1L, 500)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> orderService.getActiveOrderByTable(50L));
    }

    @Test
    void testKitchenRoleRestrictedFromCreatingOrCancellingOrders() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");

        assertThrows(AccessDeniedException.class, () -> orderService.cancelOrder(23L));
    }
}
