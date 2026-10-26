package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderResponse;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.*;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.KitchenService;
import com.restaurant.restaurantpos.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Step24KitchenTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private KitchenOrderEventPublisher eventPublisher;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private KitchenService kitchenService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private User kitchenUser;
    private RestaurantTable table1;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Royal Dine");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Ocean Breeze");

        kitchenUser = new User();
        kitchenUser.setId(10L);

        table1 = new RestaurantTable();
        table1.setId(5L);
        table1.setTableNumber(12);
        table1.setRestaurant(restaurantA);
    }

    // 1. KITCHEN can GET kitchen orders sorted by createdAt ASC
    @Test
    void testGetKitchenOrdersSuccess() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Order order1 = new Order();
        order1.setId(100L);
        order1.setRestaurant(restaurantA);
        order1.setStatus("NEW");
        order1.setOrderType("DINE_IN");
        order1.setTable(table1);
        order1.setNotes("Order Note 1");
        order1.setCreatedAt(LocalDateTime.now().minusMinutes(10));

        Order order2 = new Order();
        order2.setId(101L);
        order2.setRestaurant(restaurantA);
        order2.setStatus("ACCEPTED");
        order2.setOrderType("TAKEAWAY");
        order2.setNotes("Order Note 2");
        order2.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        when(orderRepository.findByRestaurantIdAndStatusInOrderByCreatedAtAsc(eq(1L), anyCollection()))
                .thenReturn(Arrays.asList(order1, order2));

        List<KitchenOrderResponse> res = kitchenService.getKitchenOrders(null);

        assertEquals(2, res.size());
        assertEquals(100L, res.get(0).getOrderId());
        assertEquals("DINE_IN", res.get(0).getOrderType());
        assertEquals(12, res.get(0).getTableNumber());
        assertEquals("Order Note 1", res.get(0).getNotes());

        assertEquals(101L, res.get(1).getOrderId());
        assertEquals("TAKEAWAY", res.get(1).getOrderType());
        assertNull(res.get(1).getTableNumber());
        assertEquals("Order Note 2", res.get(1).getNotes());
    }

    // 1b. ADMIN can also GET kitchen orders
    @Test
    void testGetKitchenOrdersAsAdminSuccess() {
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Order order1 = new Order();
        order1.setId(100L);
        order1.setRestaurant(restaurantA);
        order1.setStatus("NEW");
        order1.setOrderType("DINE_IN");
        order1.setTable(table1);
        order1.setNotes("Order Note 1");
        order1.setCreatedAt(LocalDateTime.now().minusMinutes(10));

        when(orderRepository.findByRestaurantIdAndStatusInOrderByCreatedAtAsc(eq(1L), anyCollection()))
                .thenReturn(Collections.singletonList(order1));

        List<KitchenOrderResponse> res = kitchenService.getKitchenOrders(null);

        assertEquals(1, res.size());
        assertEquals(100L, res.get(0).getOrderId());
    }

    // 2, 22, 23, 24. KITCHEN can GET kitchen order detail with order and item notes
    @Test
    void testGetKitchenOrderByIdSuccess() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Order order = new Order();
        order.setId(100L);
        order.setRestaurant(restaurantA);
        order.setStatus("NEW");
        order.setOrderType("DINE_IN");
        order.setTable(table1);
        order.setNotes("Order level note");

        Category category = new Category();
        category.setName("Starters");

        MenuItem menuItem = new MenuItem();
        menuItem.setId(50L);
        menuItem.setName("Paneer Tikka");
        menuItem.setCategory(category);

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrder(order);
        item.setMenuItem(menuItem);
        item.setQuantity(2);
        item.setNotes("Extra spicy");

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(100L)).thenReturn(Collections.singletonList(item));

        KitchenOrderResponse res = kitchenService.getKitchenOrderById(100L);

        assertNotNull(res);
        assertEquals(100L, res.getOrderId());
        assertEquals("DINE_IN", res.getOrderId() != null ? res.getOrderType() : null);
        assertEquals(12, res.getTableNumber());
        assertEquals("Order level note", res.getNotes());
        assertEquals(1, res.getItems().size());
        assertEquals("Paneer Tikka", res.getItems().get(0).getMenuItemName());
        assertEquals("Extra spicy", res.getItems().get(0).getNotes());
        assertEquals("Starters", res.getItems().get(0).getCategoryName());
    }

    // 3 & 25. KITCHEN can accept NEW order & OrderStatusHistory is recorded
    @Test
    void testKitchenAcceptNewOrder() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        Order order = new Order();
        order.setId(100L);
        order.setRestaurant(restaurantA);
        order.setStatus("NEW");
        order.setOrderType("DINE_IN");

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        KitchenOrderResponse res = kitchenService.acceptOrder(100L);

        assertEquals("ACCEPTED", res.getStatus());
        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
        verify(eventPublisher).publishOrderStatusChanged(any(Order.class));
    }

    // 4. KITCHEN can move ACCEPTED → PREPARING
    @Test
    void testKitchenMovePreparing() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        Order order = new Order();
        order.setId(100L);
        order.setRestaurant(restaurantA);
        order.setStatus("ACCEPTED");

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        KitchenOrderResponse res = kitchenService.prepareOrder(100L);

        assertEquals("PREPARING", res.getStatus());
        verify(eventPublisher).publishOrderStatusChanged(any(Order.class));
    }

    // 5. KITCHEN can move PREPARING → READY
    @Test
    void testKitchenMoveReady() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        Order order = new Order();
        order.setId(100L);
        order.setRestaurant(restaurantA);
        order.setStatus("PREPARING");

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        KitchenOrderResponse res = kitchenService.readyOrder(100L);

        assertEquals("READY", res.getStatus());
        verify(eventPublisher).publishOrderStatusChanged(any(Order.class));
    }

    // Invalid transition throws HTTP 400 RuntimeException
    @Test
    void testInvalidTransitionThrowsException() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        Order order = new Order();
        order.setId(100L);
        order.setRestaurant(restaurantA);
        order.setStatus("NEW");

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> kitchenService.prepareOrder(100L));
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    // 12 & 13. Non-kitchen roles (e.g. WAITER) receive 403 Forbidden on Kitchen endpoints
    @Test
    void testNonKitchenRoleReceives403() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        assertThrows(AccessDeniedException.class, () -> kitchenService.getKitchenOrders(null));

        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        assertThrows(AccessDeniedException.class, () -> kitchenService.acceptOrder(100L));
    }

    // 15, 16, 17. Queue filter validation (COMPLETED and CANCELLED rejected)
    @Test
    void testInvalidQueueStatusFilter() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        assertThrows(RuntimeException.class, () -> kitchenService.getKitchenOrders("COMPLETED"));
        assertThrows(RuntimeException.class, () -> kitchenService.getKitchenOrders("CANCELLED"));
        assertThrows(RuntimeException.class, () -> kitchenService.getKitchenOrders("INVALID_STATUS"));
    }

    // 19 & 20. Multi-tenant isolation test
    @Test
    void testTenantIsolation() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Order orderB = new Order();
        orderB.setId(200L);
        orderB.setRestaurant(restaurantB); // Restaurant B
        orderB.setStatus("NEW");

        when(orderRepository.findById(200L)).thenReturn(Optional.of(orderB));

        RuntimeException ex1 = assertThrows(RuntimeException.class, () -> kitchenService.getKitchenOrderById(200L));
        assertTrue(ex1.getMessage().contains("not found"));

        RuntimeException ex2 = assertThrows(RuntimeException.class, () -> kitchenService.acceptOrder(200L));
        assertTrue(ex2.getMessage().contains("not found"));
    }
}
