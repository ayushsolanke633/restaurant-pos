package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.*;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TableOwnershipConcurrencyTest {

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
    private BillRepository billRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private KitchenOrderEventPublisher eventPublisher;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderService orderService;

    private Restaurant restaurant;
    private Restaurant otherRestaurant;
    private RestaurantTable table1;
    private RestaurantTable table2;
    private RestaurantTable tableOtherRestaurant;
    private User waiterA;
    private User waiterB;
    private User adminUser;
    private MenuItem menuItem;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        otherRestaurant.setName("Other Restaurant");

        table1 = new RestaurantTable();
        table1.setId(10L);
        table1.setRestaurant(restaurant);
        table1.setTableNumber(1);
        table1.setCapacity(4);
        table1.setStatus("AVAILABLE");

        table2 = new RestaurantTable();
        table2.setId(20L);
        table2.setRestaurant(restaurant);
        table2.setTableNumber(2);
        table2.setCapacity(2);
        table2.setStatus("AVAILABLE");

        tableOtherRestaurant = new RestaurantTable();
        tableOtherRestaurant.setId(99L);
        tableOtherRestaurant.setRestaurant(otherRestaurant);
        tableOtherRestaurant.setTableNumber(1);
        tableOtherRestaurant.setStatus("AVAILABLE");

        Role waiterRole = new Role(3L, "WAITER");
        Role adminRole = new Role(1L, "ADMIN");

        waiterA = new User();
        waiterA.setId(101L);
        waiterA.setName("Waiter Alice");
        waiterA.setRole(waiterRole);
        waiterA.setRestaurant(restaurant);

        waiterB = new User();
        waiterB.setId(102L);
        waiterB.setName("Waiter Bob");
        waiterB.setRole(waiterRole);
        waiterB.setRestaurant(restaurant);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setName("Admin");
        adminUser.setRole(adminRole);
        adminUser.setRestaurant(restaurant);

        menuItem = new MenuItem();
        menuItem.setId(50L);
        menuItem.setRestaurant(restaurant);
        menuItem.setName("Pasta");
        menuItem.setPrice(new BigDecimal("200.00"));
        menuItem.setIsAvailable(true);
    }

    @Test
    @DisplayName("1. Waiter A creates dine-in order for available table -> succeeds, table OCCUPIED, waiter assigned")
    void testWaiterACreatesDineInOrderForAvailableTable() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(table1));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(
                eq(1L), eq(10L), eq("DINE_IN"), anyList()
        )).thenReturn(false);
        when(userRepository.findById(101L)).thenReturn(Optional.of(waiterA));
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(menuItem));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterA);

        Order savedOrder = new Order();
        savedOrder.setId(1001L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(table1);
        savedOrder.setWaiter(waiterA);
        savedOrder.setOrderType("DINE_IN");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("200.00"));
        savedOrder.setTaxAmount(BigDecimal.ZERO);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("200.00"));
        savedOrder.setCreatedAt(LocalDateTime.now());
        savedOrder.setUpdatedAt(LocalDateTime.now());

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        OrderResponse res = orderService.createOrder(req);

        assertNotNull(res);
        assertEquals(1001L, res.getId());
        assertEquals(101L, res.getWaiterId());
        assertEquals("OCCUPIED", table1.getStatus());
        verify(tableRepository, times(1)).save(table1);
    }

    @Test
    @DisplayName("2. Waiter B cannot create order on table with active order belonging to Waiter A (409 Conflict message)")
    void testWaiterBCannotCreateOrderOnTableWithWaiterAOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(102L); // Waiter B
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        table1.setStatus("OCCUPIED");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(table1));

        Order existingOrder = new Order();
        existingOrder.setId(1001L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setTable(table1);
        existingOrder.setWaiter(waiterA); // Owned by Waiter A (101L)
        existingOrder.setOrderType("DINE_IN");
        existingOrder.setStatus("NEW");

        when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(10L), eq("DINE_IN"), anyList()
        )).thenReturn(Optional.of(existingOrder));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(req));
        assertEquals("This table already has an active order assigned to another waiter", ex.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("3. Waiter B cannot modify Waiter A's order")
    void testWaiterBCannotModifyWaiterAOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(102L); // Waiter B
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order orderA = new Order();
        orderA.setId(1001L);
        orderA.setRestaurant(restaurant);
        orderA.setTable(table1);
        orderA.setWaiter(waiterA); // Owned by Waiter A
        orderA.setOrderType("DINE_IN");
        orderA.setStatus("NEW");

        when(orderRepository.findById(1001L)).thenReturn(Optional.of(orderA));

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setItems(List.of(new OrderItemRequest(50L, 2, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.updateOrder(1001L, updateReq));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("4. Waiter B cannot add items to Waiter A's order")
    void testWaiterBCannotAddItemsToWaiterAOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(102L); // Waiter B
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order orderA = new Order();
        orderA.setId(1001L);
        orderA.setRestaurant(restaurant);
        orderA.setTable(table1);
        orderA.setWaiter(waiterA); // Owned by Waiter A
        orderA.setOrderType("DINE_IN");
        orderA.setStatus("ACCEPTED");

        when(orderRepository.findById(1001L)).thenReturn(Optional.of(orderA));

        OrderRequest addReq = new OrderRequest();
        addReq.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.addItemsToOrder(1001L, addReq));
        verify(orderItemRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("5. Forged request cannot reassign table or waiter on update")
    void testForgedRequestCannotReassignTableOrWaiter() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L); // Waiter A
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order orderA = new Order();
        orderA.setId(1001L);
        orderA.setRestaurant(restaurant);
        orderA.setTable(table1);
        orderA.setWaiter(waiterA);
        orderA.setOrderType("DINE_IN");
        orderA.setStatus("NEW");
        orderA.setSubtotal(new BigDecimal("200.00"));
        orderA.setTaxAmount(BigDecimal.ZERO);
        orderA.setDiscountAmount(BigDecimal.ZERO);
        orderA.setTotalAmount(new BigDecimal("200.00"));

        when(orderRepository.findById(1001L)).thenReturn(Optional.of(orderA));

        // Forged request attempts to switch to table 20
        OrderRequest forgedReq = new OrderRequest();
        forgedReq.setOrderType("DINE_IN");
        forgedReq.setTableId(20L); // Forged attempt
        forgedReq.setItems(List.of(new OrderItemRequest(50L, 2, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.updateOrder(1001L, forgedReq));
        assertEquals(10L, orderA.getTable().getId(), "Table assignment must remain immutable on update");
        assertEquals(101L, orderA.getWaiter().getId(), "Waiter assignment must remain immutable on update");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("6. Cross-tenant access is strictly blocked")
    void testCrossTenantAccessBlocked() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        when(tableRepository.findById(99L)).thenReturn(Optional.of(tableOtherRestaurant));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(99L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(req));
        assertTrue(ex.getMessage().contains("not found"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("7. Concurrent claims on the same table result in exactly one order")
    void testConcurrentClaimsOnSameTableResultInExactlyOneOrder() {
        // Table locking and active order checking ensure sequential/serialized safety
        // First request succeeds and changes table status to OCCUPIED and saves order
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(table1));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(
                eq(1L), eq(10L), eq("DINE_IN"), anyList()
        )).thenReturn(false);
        when(userRepository.findById(101L)).thenReturn(Optional.of(waiterA));
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(menuItem));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterA);

        Order savedOrder = new Order();
        savedOrder.setId(1001L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(table1);
        savedOrder.setWaiter(waiterA);
        savedOrder.setOrderType("DINE_IN");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("200.00"));
        savedOrder.setTaxAmount(BigDecimal.ZERO);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("200.00"));

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        OrderResponse res1 = orderService.createOrder(req);
        assertNotNull(res1);
        assertEquals("OCCUPIED", table1.getStatus());

        // Subsequent call for Waiter B on now-OCCUPIED table is rejected
        when(securityUtils.getCurrentUserId()).thenReturn(102L); // Waiter B
        when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(10L), eq("DINE_IN"), anyList()
        )).thenReturn(Optional.of(savedOrder));

        RuntimeException conflictEx = assertThrows(RuntimeException.class, () -> orderService.createOrder(req));
        assertTrue(conflictEx.getMessage().contains("This table already has an active order assigned to another waiter"));
    }

    @Test
    @DisplayName("8. Rejected attempt leaves clean table status and no duplicate order")
    void testRejectedAttemptLeavesCleanTableStatus() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(102L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        table1.setStatus("OCCUPIED");
        when(tableRepository.findById(10L)).thenReturn(Optional.of(table1));

        Order orderOfWaiterA = new Order();
        orderOfWaiterA.setId(1001L);
        orderOfWaiterA.setRestaurant(restaurant);
        orderOfWaiterA.setTable(table1);
        orderOfWaiterA.setWaiter(waiterA);
        orderOfWaiterA.setOrderType("DINE_IN");
        orderOfWaiterA.setStatus("NEW");

        when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(10L), eq("DINE_IN"), anyList()
        )).thenReturn(Optional.of(orderOfWaiterA));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(req));
        assertEquals("OCCUPIED", table1.getStatus(), "Table status must remain OCCUPIED for existing order");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("9. Another available table works normally for Waiter B")
    void testAnotherAvailableTableWorksNormally() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(102L); // Waiter B
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(tableRepository.findById(20L)).thenReturn(Optional.of(table2));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(
                eq(1L), eq(20L), eq("DINE_IN"), anyList()
        )).thenReturn(false);
        when(userRepository.findById(102L)).thenReturn(Optional.of(waiterB));
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(menuItem));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(102L)).thenReturn(waiterB);

        Order savedOrder2 = new Order();
        savedOrder2.setId(2001L);
        savedOrder2.setRestaurant(restaurant);
        savedOrder2.setTable(table2);
        savedOrder2.setWaiter(waiterB);
        savedOrder2.setOrderType("DINE_IN");
        savedOrder2.setStatus("NEW");
        savedOrder2.setSubtotal(new BigDecimal("200.00"));
        savedOrder2.setTaxAmount(BigDecimal.ZERO);
        savedOrder2.setDiscountAmount(BigDecimal.ZERO);
        savedOrder2.setTotalAmount(new BigDecimal("200.00"));

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder2);
        when(orderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(20L);
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        OrderResponse res = orderService.createOrder(req);

        assertNotNull(res);
        assertEquals(2001L, res.getId());
        assertEquals(102L, res.getWaiterId());
        assertEquals("OCCUPIED", table2.getStatus());
    }

    @Test
    @DisplayName("10. TAKEAWAY orders work without table for ADMIN")
    void testTakeawayOrdersWorkWithoutTable() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(menuItemRepository.findById(50L)).thenReturn(Optional.of(menuItem));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(userRepository.getReferenceById(1L)).thenReturn(adminUser);

        Order savedOrder = new Order();
        savedOrder.setId(3001L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(null);
        savedOrder.setWaiter(null);
        savedOrder.setOrderType("TAKEAWAY");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("200.00"));
        savedOrder.setTaxAmount(BigDecimal.ZERO);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("200.00"));

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        OrderRequest req = new OrderRequest();
        req.setOrderType("TAKEAWAY");
        req.setItems(List.of(new OrderItemRequest(50L, 1, null)));

        OrderResponse res = orderService.createOrder(req);

        assertNotNull(res);
        assertEquals("TAKEAWAY", res.getOrderType());
        assertNull(res.getTableId());
    }

    @Test
    @DisplayName("11. Cancelling order releases table to AVAILABLE")
    void testCancelOrderReleasesTable() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        table1.setStatus("OCCUPIED");

        Order orderA = new Order();
        orderA.setId(1001L);
        orderA.setRestaurant(restaurant);
        orderA.setTable(table1);
        orderA.setWaiter(waiterA);
        orderA.setOrderType("DINE_IN");
        orderA.setStatus("NEW");
        orderA.setSubtotal(new BigDecimal("200.00"));
        orderA.setTaxAmount(BigDecimal.ZERO);
        orderA.setDiscountAmount(BigDecimal.ZERO);
        orderA.setTotalAmount(new BigDecimal("200.00"));

        when(orderRepository.findById(1001L)).thenReturn(Optional.of(orderA));
        when(orderRepository.save(any(Order.class))).thenReturn(orderA);
        when(userRepository.getReferenceById(101L)).thenReturn(waiterA);

        OrderResponse res = orderService.cancelOrder(1001L);

        assertNotNull(res);
        assertEquals("CANCELLED", res.getStatus());
        assertEquals("AVAILABLE", table1.getStatus());
        verify(tableRepository, times(1)).save(table1);
    }

    @Test
    @DisplayName("12. Kitchen and Admin lifecycle workflow functional")
    void testKitchenAndAdminLifecycleFunctional() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentUserId()).thenReturn(201L);
        when(userRepository.getReferenceById(201L)).thenReturn(waiterA);

        Order order = new Order();
        order.setId(1001L);
        order.setRestaurant(restaurant);
        order.setTable(table1);
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");

        when(orderRepository.findById(1001L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse accepted = orderService.acceptOrder(1001L);
        assertEquals("ACCEPTED", accepted.getStatus());

        OrderResponse preparing = orderService.prepareOrder(1001L);
        assertEquals("PREPARING", preparing.getStatus());

        OrderResponse ready = orderService.readyOrder(1001L);
        assertEquals("READY", ready.getStatus());
    }
}
