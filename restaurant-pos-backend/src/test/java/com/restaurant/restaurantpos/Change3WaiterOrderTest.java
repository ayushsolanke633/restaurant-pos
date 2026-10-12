package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Change3WaiterOrderTest {

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

    private Restaurant restaurant;
    private RestaurantTable availableTable;
    private User waiterUser1;
    private User waiterUser2;
    private MenuItem item1;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        availableTable = new RestaurantTable();
        availableTable.setId(10L);
        availableTable.setRestaurant(restaurant);
        availableTable.setTableNumber(5);
        availableTable.setCapacity(4);
        availableTable.setStatus("AVAILABLE");

        Role waiterRole = new Role(3L, "WAITER");

        waiterUser1 = new User();
        waiterUser1.setId(101L);
        waiterUser1.setName("Waiter One");
        waiterUser1.setRole(waiterRole);
        waiterUser1.setRestaurant(restaurant);

        waiterUser2 = new User();
        waiterUser2.setId(102L);
        waiterUser2.setName("Waiter Two");
        waiterUser2.setRole(waiterRole);
        waiterUser2.setRestaurant(restaurant);

        item1 = new MenuItem();
        item1.setId(50L);
        item1.setRestaurant(restaurant);
        item1.setName("Burger");
        item1.setPrice(new BigDecimal("150.00"));
        item1.setIsAvailable(true);
    }

    @Test
    @DisplayName("1. WAITER can create DINE_IN order")
    void testWaiterCanCreateDineInOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(availableTable));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(10L), eq("DINE_IN"), anyList()))
                .thenReturn(false);
        when(userRepository.findById(101L)).thenReturn(Optional.of(waiterUser1));
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(item1));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterUser1);

        Order savedOrder = new Order();
        savedOrder.setId(1001L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(availableTable);
        savedOrder.setWaiter(waiterUser1);
        savedOrder.setOrderType("DINE_IN");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("150.00"));
        savedOrder.setTaxAmount(BigDecimal.ZERO);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("150.00"));
        savedOrder.setCreatedAt(LocalDateTime.now());
        savedOrder.setUpdatedAt(LocalDateTime.now());

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, "No onion")));

        OrderResponse res = orderService.createOrder(req);

        assertNotNull(res);
        assertEquals(1001L, res.getId());
        assertEquals(101L, res.getWaiterId());
        assertEquals("DINE_IN", res.getOrderType());
        assertEquals("OCCUPIED", availableTable.getStatus());
        verify(eventPublisher, times(1)).publishOrderCreated(savedOrder);
    }

    @Test
    @DisplayName("2. WAITER cannot create TAKEAWAY order")
    void testWaiterCannotCreateTakeawayOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        OrderRequest req = new OrderRequest();
        req.setOrderType("TAKEAWAY");
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.createOrder(req));
    }

    @Test
    @DisplayName("3. KITCHEN cannot create order")
    void testKitchenCannotCreateOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(201L);
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.createOrder(req));
    }

    @Test
    @DisplayName("4. ADMIN can create DINE_IN order")
    void testAdminCanCreateDineInOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(availableTable));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(10L), eq("DINE_IN"), anyList()))
                .thenReturn(false);
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(item1));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(1L)).thenReturn(waiterUser1);

        Order savedOrder = new Order();
        savedOrder.setId(1002L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(availableTable);
        savedOrder.setWaiter(null); // Admin creation doesn't set a waiter
        savedOrder.setOrderType("DINE_IN");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("150.00"));
        savedOrder.setTaxAmount(BigDecimal.ZERO);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("150.00"));

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        OrderResponse res = orderService.createOrder(req);
        assertNotNull(res);
        assertNull(res.getWaiterId());
    }

    @Test
    @DisplayName("5. waiterId is derived from authenticated user")
    void testWaiterIdDerivedFromAuthUser() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(availableTable));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(10L), eq("DINE_IN"), anyList()))
                .thenReturn(false);
        when(userRepository.findById(101L)).thenReturn(Optional.of(waiterUser1));
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(item1));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterUser1);

        Order savedOrder = new Order();
        savedOrder.setId(1003L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(availableTable);
        savedOrder.setWaiter(waiterUser1);
        savedOrder.setOrderType("DINE_IN");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("150.00"));
        savedOrder.setTaxAmount(BigDecimal.ZERO);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("150.00"));

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        OrderResponse res = orderService.createOrder(req);
        assertEquals(101L, res.getWaiterId());
        assertEquals("Waiter One", res.getWaiterName());
    }

    @Test
    @DisplayName("6. Waiter cannot impersonate another waiter")
    void testWaiterCannotImpersonateAnotherWaiter() {
        // Even if request body had waiter details, backend derives waiter from SecurityUtils.getCurrentUserId()
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(availableTable));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(10L), eq("DINE_IN"), anyList()))
                .thenReturn(false);
        when(userRepository.findById(101L)).thenReturn(Optional.of(waiterUser1));
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(item1));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterUser1);

        Order savedOrder = new Order();
        savedOrder.setId(1004L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(availableTable);
        savedOrder.setWaiter(waiterUser1); // Always 101L
        savedOrder.setOrderType("DINE_IN");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("150.00"));
        savedOrder.setTaxAmount(BigDecimal.ZERO);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("150.00"));

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        OrderResponse res = orderService.createOrder(req);
        assertEquals(101L, res.getWaiterId());
        verify(userRepository, never()).findById(102L); // Never fetches waiter 102
    }

    @Test
    @DisplayName("7. Duplicate active table order is rejected")
    void testDuplicateActiveTableOrderIsRejected() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(availableTable));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(10L), eq("DINE_IN"), anyList()))
                .thenReturn(true);

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(req));
        assertTrue(ex.getMessage().contains("Table already has an active order"));
    }

    @Test
    @DisplayName("8. Tenant isolation")
    void testTenantIsolation() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        otherRestaurant.setName("Other Restaurant");

        RestaurantTable otherTable = new RestaurantTable();
        otherTable.setId(99L);
        otherTable.setRestaurant(otherRestaurant);
        otherTable.setTableNumber(1);
        otherTable.setStatus("AVAILABLE");

        when(tableRepository.findById(99L)).thenReturn(Optional.of(otherTable));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(99L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(req));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("9. Waiter can modify own NEW order")
    void testWaiterCanModifyOwnNewOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order existingOrder = new Order();
        existingOrder.setId(500L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setTable(availableTable);
        existingOrder.setWaiter(waiterUser1);
        existingOrder.setOrderType("DINE_IN");
        existingOrder.setStatus("NEW");
        existingOrder.setSubtotal(new BigDecimal("150.00"));
        existingOrder.setTaxAmount(BigDecimal.ZERO);
        existingOrder.setDiscountAmount(BigDecimal.ZERO);
        existingOrder.setTotalAmount(new BigDecimal("150.00"));

        when(orderRepository.findById(500L)).thenReturn(Optional.of(existingOrder));
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(item1));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);
        when(orderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setItems(List.of(new OrderItemRequest(50L, 2, "Extra crispy")));

        OrderResponse res = orderService.updateOrder(500L, updateReq);
        assertNotNull(res);
        verify(eventPublisher, times(1)).publishOrderUpdated(existingOrder);
    }

    @Test
    @DisplayName("10. Waiter cannot modify ACCEPTED order")
    void testWaiterCannotModifyAcceptedOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order acceptedOrder = new Order();
        acceptedOrder.setId(501L);
        acceptedOrder.setRestaurant(restaurant);
        acceptedOrder.setTable(availableTable);
        acceptedOrder.setWaiter(waiterUser1);
        acceptedOrder.setOrderType("DINE_IN");
        acceptedOrder.setStatus("ACCEPTED");

        when(orderRepository.findById(501L)).thenReturn(Optional.of(acceptedOrder));

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.updateOrder(501L, updateReq));
        assertTrue(ex.getMessage().contains("Order can only be modified while status is NEW"));
    }

    @Test
    @DisplayName("11. Waiter cannot modify another waiter's order")
    void testWaiterCannotModifyAnotherWaitersOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(102L); // Waiter 2
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order orderOfWaiter1 = new Order();
        orderOfWaiter1.setId(502L);
        orderOfWaiter1.setRestaurant(restaurant);
        orderOfWaiter1.setTable(availableTable);
        orderOfWaiter1.setWaiter(waiterUser1); // Owned by Waiter 1
        orderOfWaiter1.setOrderType("DINE_IN");
        orderOfWaiter1.setStatus("NEW");

        when(orderRepository.findById(502L)).thenReturn(Optional.of(orderOfWaiter1));

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.updateOrder(502L, updateReq));
    }

    @Test
    @DisplayName("12. Waiter can cancel own NEW order")
    void testWaiterCanCancelOwnNewOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        RestaurantTable occupiedTable = new RestaurantTable();
        occupiedTable.setId(10L);
        occupiedTable.setRestaurant(restaurant);
        occupiedTable.setStatus("OCCUPIED");

        Order newOrder = new Order();
        newOrder.setId(600L);
        newOrder.setRestaurant(restaurant);
        newOrder.setTable(occupiedTable);
        newOrder.setWaiter(waiterUser1);
        newOrder.setOrderType("DINE_IN");
        newOrder.setStatus("NEW");
        newOrder.setSubtotal(new BigDecimal("150.00"));
        newOrder.setTaxAmount(BigDecimal.ZERO);
        newOrder.setDiscountAmount(BigDecimal.ZERO);
        newOrder.setTotalAmount(new BigDecimal("150.00"));

        when(orderRepository.findById(600L)).thenReturn(Optional.of(newOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(newOrder);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterUser1);

        OrderResponse res = orderService.cancelOrder(600L);
        assertNotNull(res);
        assertEquals("CANCELLED", res.getStatus());
        assertEquals("AVAILABLE", occupiedTable.getStatus());
        verify(eventPublisher, times(1)).publishOrderStatusChanged(newOrder);
    }

    @Test
    @DisplayName("13. Waiter cannot cancel ACCEPTED order")
    void testWaiterCannotCancelAcceptedOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        Order acceptedOrder = new Order();
        acceptedOrder.setId(601L);
        acceptedOrder.setRestaurant(restaurant);
        acceptedOrder.setTable(availableTable);
        acceptedOrder.setWaiter(waiterUser1);
        acceptedOrder.setOrderType("DINE_IN");
        acceptedOrder.setStatus("ACCEPTED");

        when(orderRepository.findById(601L)).thenReturn(Optional.of(acceptedOrder));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.cancelOrder(601L));
        assertTrue(ex.getMessage().contains("Invalid cancellation status"));
    }

    @Test
    @DisplayName("14. NEW cancellation releases table correctly")
    void testNewCancellationReleasesTableCorrectly() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        RestaurantTable tableToRelease = new RestaurantTable();
        tableToRelease.setId(10L);
        tableToRelease.setRestaurant(restaurant);
        tableToRelease.setStatus("OCCUPIED");

        Order newOrder = new Order();
        newOrder.setId(602L);
        newOrder.setRestaurant(restaurant);
        newOrder.setTable(tableToRelease);
        newOrder.setWaiter(waiterUser1);
        newOrder.setOrderType("DINE_IN");
        newOrder.setStatus("NEW");
        newOrder.setSubtotal(new BigDecimal("150.00"));
        newOrder.setTaxAmount(BigDecimal.ZERO);
        newOrder.setDiscountAmount(BigDecimal.ZERO);
        newOrder.setTotalAmount(new BigDecimal("150.00"));

        when(orderRepository.findById(602L)).thenReturn(Optional.of(newOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(newOrder);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterUser1);

        orderService.cancelOrder(602L);

        assertEquals("AVAILABLE", tableToRelease.getStatus());
        verify(tableRepository, times(1)).save(tableToRelease);
    }

    @Test
    @DisplayName("15. Order creation publishes existing WebSocket event")
    void testOrderCreationPublishesWebSocketEvent() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(availableTable));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(10L), eq("DINE_IN"), anyList()))
                .thenReturn(false);
        when(userRepository.findById(101L)).thenReturn(Optional.of(waiterUser1));
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(item1));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterUser1);

        Order savedOrder = new Order();
        savedOrder.setId(1005L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(availableTable);
        savedOrder.setWaiter(waiterUser1);
        savedOrder.setOrderType("DINE_IN");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("150.00"));
        savedOrder.setTaxAmount(BigDecimal.ZERO);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("150.00"));

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        orderService.createOrder(req);

        verify(eventPublisher, times(1)).publishOrderCreated(savedOrder);
    }
}
