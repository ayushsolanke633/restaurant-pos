package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderResponse;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.exception.OrderWorkflowConflictException;
import com.restaurant.restaurantpos.repository.*;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.KitchenService;
import com.restaurant.restaurantpos.service.NotificationService;
import com.restaurant.restaurantpos.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KitchenIncrementalOrderUpdateRegressionTest {

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

    @Mock
    private RestaurantSettingsRepository settingsRepository;

    private OrderService orderService;
    private KitchenService kitchenService;

    private Restaurant restaurant;
    private RestaurantSettings kdsEnabledSettings;
    private User waiter;
    private User kitchenStaff;
    private RestaurantTable table;
    private MenuItem itemBurger;
    private MenuItem itemFries;
    private MenuItem itemCoke;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Gourmet Kitchen");

        kdsEnabledSettings = new RestaurantSettings();
        kdsEnabledSettings.setId(10L);
        kdsEnabledSettings.setRestaurant(restaurant);
        kdsEnabledSettings.setKitchenEnabled(true);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsEnabledSettings));

        waiter = new User();
        waiter.setId(100L);
        waiter.setName("Sam Waiter");
        waiter.setRestaurant(restaurant);

        kitchenStaff = new User();
        kitchenStaff.setId(200L);
        kitchenStaff.setName("Chef Gordon");
        kitchenStaff.setRestaurant(restaurant);

        table = new RestaurantTable();
        table.setId(10L);
        table.setTableNumber(5);
        table.setRestaurant(restaurant);
        table.setStatus("OCCUPIED");

        Category mainCat = new Category();
        mainCat.setId(1L);
        mainCat.setName("Mains");
        mainCat.setIsActive(true);

        Category sidesCat = new Category();
        sidesCat.setId(2L);
        sidesCat.setName("Sides");
        sidesCat.setIsActive(true);

        itemBurger = new MenuItem();
        itemBurger.setId(501L);
        itemBurger.setName("Classic Burger");
        itemBurger.setPrice(new BigDecimal("12.00"));
        itemBurger.setIsAvailable(true);
        itemBurger.setCategory(mainCat);
        itemBurger.setRestaurant(restaurant);

        itemFries = new MenuItem();
        itemFries.setId(502L);
        itemFries.setName("Crispy Fries");
        itemFries.setPrice(new BigDecimal("5.00"));
        itemFries.setIsAvailable(true);
        itemFries.setCategory(sidesCat);
        itemFries.setRestaurant(restaurant);

        itemCoke = new MenuItem();
        itemCoke.setId(503L);
        itemCoke.setName("Diet Coke");
        itemCoke.setPrice(new BigDecimal("3.00"));
        itemCoke.setIsAvailable(true);
        itemCoke.setCategory(sidesCat);
        itemCoke.setRestaurant(restaurant);

        when(menuItemRepository.findByIdAndRestaurantId(501L, 1L)).thenReturn(Optional.of(itemBurger));
        when(menuItemRepository.findByIdAndRestaurantId(502L, 1L)).thenReturn(Optional.of(itemFries));
        when(menuItemRepository.findByIdAndRestaurantId(503L, 1L)).thenReturn(Optional.of(itemCoke));
        when(menuItemRepository.findById(501L)).thenReturn(Optional.of(itemBurger));
        when(menuItemRepository.findById(502L)).thenReturn(Optional.of(itemFries));
        when(menuItemRepository.findById(503L)).thenReturn(Optional.of(itemCoke));

        orderService = new OrderService(
                orderRepository, orderItemRepository, orderStatusHistoryRepository,
                restaurantRepository, tableRepository, menuItemRepository,
                userRepository, customerRepository, securityUtils,
                eventPublisher, notificationService, billRepository, settingsRepository
        );

        kitchenService = new KitchenService(
                orderRepository, orderItemRepository, orderStatusHistoryRepository,
                userRepository, securityUtils, eventPublisher,
                notificationService, settingsRepository
        );
    }

    private Order createTestOrder(Long orderId, String status) {
        Order order = new Order();
        order.setId(orderId);
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setWaiter(waiter);
        order.setOrderType("DINE_IN");
        order.setStatus(status);
        order.setSubtotal(new BigDecimal("12.00"));
        order.setTaxAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("12.00"));
        order.setCreatedAt(LocalDateTime.now().minusMinutes(15));
        order.setUpdatedAt(LocalDateTime.now().minusMinutes(15));

        when(orderRepository.findByIdAndRestaurantId(orderId, 1L)).thenReturn(Optional.of(order));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(billRepository.existsByOrderId(orderId)).thenReturn(false);
        when(billRepository.existsByRestaurantIdAndOrderId(1L, orderId)).thenReturn(false);

        return order;
    }

    private OrderItem createTestOrderItem(Long itemId, Order order, MenuItem item, int qty, String status, int batchNumber, String notes) {
        OrderItem oi = new OrderItem();
        oi.setId(itemId);
        oi.setOrder(order);
        oi.setRestaurant(restaurant);
        oi.setMenuItem(item);
        oi.setQuantity(qty);
        oi.setUnitPrice(item.getPrice());
        oi.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(qty)));
        oi.setStatus(status);
        oi.setBatchNumber(batchNumber);
        oi.setNotes(notes);
        return oi;
    }

    // 1. Adding items to a NEW order (KDS enabled)
    @Test
    @DisplayName("1. Adding items to a NEW order creates batch #2 with status NEW and preserves batch #1")
    void testAddItemsToNewOrder_KdsEnabled() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order order = createTestOrder(101L, "NEW");
        OrderItem item1 = createTestOrderItem(1L, order, itemBurger, 1, "NEW", 1, "No onions");
        when(orderItemRepository.findByOrderIdAndRestaurantId(101L, 1L)).thenReturn(new ArrayList<>(List.of(item1)));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(502L, 2, "Extra crispy")));

        OrderResponse response = orderService.addItemsToOrder(101L, request);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());
        List<OrderItem> saved = captor.getValue();

        assertEquals(1, saved.size());
        OrderItem newBatchItem = saved.get(0);
        assertEquals(502L, newBatchItem.getMenuItem().getId());
        assertEquals(2, newBatchItem.getQuantity());
        assertEquals("NEW", newBatchItem.getStatus());
        assertEquals(2, newBatchItem.getBatchNumber());

        verify(eventPublisher).publishOrderUpdated(eq(order), eq(2), eq(true));
        assertEquals("NEW", order.getStatus());
    }

    // 2. Adding items to an ACCEPTED order (KDS enabled)
    @Test
    @DisplayName("2. Adding items to an ACCEPTED order marks only new items NEW in batch #2, keeping batch #1 ACCEPTED")
    void testAddItemsToAcceptedOrder_KdsEnabled() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order order = createTestOrder(102L, "ACCEPTED");
        OrderItem item1 = createTestOrderItem(1L, order, itemBurger, 1, "ACCEPTED", 1, null);
        when(orderItemRepository.findByOrderIdAndRestaurantId(102L, 1L)).thenReturn(new ArrayList<>(List.of(item1)));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(503L, 1, "With lemon")));

        orderService.addItemsToOrder(102L, request);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());
        OrderItem addedItem = captor.getValue().get(0);

        assertEquals("NEW", addedItem.getStatus());
        assertEquals(2, addedItem.getBatchNumber());
        assertEquals("ACCEPTED", item1.getStatus());
        assertEquals(1, item1.getBatchNumber());
    }

    // 3. Adding items to a PREPARING order (KDS enabled)
    @Test
    @DisplayName("3. Adding items to a PREPARING order preserves PREPARING for batch #1 and marks batch #2 as NEW")
    void testAddItemsToPreparingOrder_KdsEnabled() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order order = createTestOrder(103L, "PREPARING");
        OrderItem item1 = createTestOrderItem(1L, order, itemBurger, 2, "PREPARING", 1, "Medium rare");
        when(orderItemRepository.findByOrderIdAndRestaurantId(103L, 1L)).thenReturn(new ArrayList<>(List.of(item1)));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(502L, 1, null)));

        orderService.addItemsToOrder(103L, request);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());
        OrderItem addedItem = captor.getValue().get(0);

        assertEquals(2, addedItem.getBatchNumber());
        assertEquals("NEW", addedItem.getStatus());
        assertEquals("PREPARING", item1.getStatus());
        assertEquals(2, item1.getQuantity());
    }

    // 4. Preserving existing item status and quantity without deleting
    @Test
    @DisplayName("4. Never deletes existing order items and preserves original identities and notes")
    void testPreservingExistingItemStatusAndQuantity() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order order = createTestOrder(104L, "PREPARING");
        OrderItem item1 = createTestOrderItem(10L, order, itemBurger, 3, "PREPARING", 1, "Gluten-free bun");
        when(orderItemRepository.findByOrderIdAndRestaurantId(104L, 1L)).thenReturn(new ArrayList<>(List.of(item1)));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(501L, 1, "Gluten-free bun")));

        orderService.addItemsToOrder(104L, request);

        verify(orderItemRepository, never()).deleteByOrderId(anyLong());
        assertEquals(3, item1.getQuantity());
        assertEquals("PREPARING", item1.getStatus());
    }

    // 5. Showing only new items as pending acceptance in KDS queue
    @Test
    @DisplayName("5. KDS getKitchenOrders('NEW') returns only the new batch card, not the preparing batch card")
    void testShowingOnlyNewItemsAsPendingAcceptance() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Order order = createTestOrder(105L, "PREPARING");
        OrderItem b1 = createTestOrderItem(1L, order, itemBurger, 1, "PREPARING", 1, null);
        OrderItem b2 = createTestOrderItem(2L, order, itemFries, 2, "NEW", 2, null);

        when(orderRepository.findByRestaurantIdAndStatusInOrderByCreatedAtAsc(eq(1L), anyList()))
                .thenReturn(List.of(order));
        when(orderItemRepository.findByOrderIdAndRestaurantId(105L, 1L))
                .thenReturn(List.of(b1, b2));

        List<KitchenOrderResponse> newQueue = kitchenService.getKitchenOrders("NEW");
        assertEquals(1, newQueue.size());
        assertEquals(2, newQueue.get(0).getBatchNumber());
        assertEquals("NEW", newQueue.get(0).getStatus());
        assertTrue(newQueue.get(0).getIsUpdate());
        assertEquals(1, newQueue.get(0).getItems().size());
        assertEquals(502L, newQueue.get(0).getItems().get(0).getMenuItemId());

        List<KitchenOrderResponse> preparingQueue = kitchenService.getKitchenOrders("PREPARING");
        assertEquals(1, preparingQueue.size());
        assertEquals(1, preparingQueue.get(0).getBatchNumber());
        assertEquals("PREPARING", preparingQueue.get(0).getStatus());
        assertFalse(preparingQueue.get(0).getIsUpdate());
    }

    // 6. Accepting/preparing new items without changing old items
    @Test
    @DisplayName("6. Accepting batch #2 moves only batch #2 to ACCEPTED while batch #1 remains PREPARING")
    void testAcceptingNewItemsWithoutChangingOldItems() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(200L);

        Order order = createTestOrder(106L, "PREPARING");
        OrderItem b1 = createTestOrderItem(1L, order, itemBurger, 1, "PREPARING", 1, null);
        OrderItem b2 = createTestOrderItem(2L, order, itemFries, 2, "NEW", 2, null);

        when(orderItemRepository.findByOrderIdAndRestaurantId(106L, 1L))
                .thenReturn(List.of(b1, b2));

        KitchenOrderResponse response = kitchenService.acceptBatch(106L, 2);

        assertEquals("ACCEPTED", b2.getStatus());
        assertEquals("PREPARING", b1.getStatus());
        assertEquals(2, response.getBatchNumber());
        assertEquals("ACCEPTED", response.getStatus());

        KitchenOrderResponse prepResponse = kitchenService.prepareBatch(106L, 2);
        assertEquals("PREPARING", b2.getStatus());
        assertEquals("PREPARING", b1.getStatus());
        assertEquals("PREPARING", prepResponse.getStatus());
    }

    // 7. Multiple sequential additions to the same order
    @Test
    @DisplayName("7. Multiple sequential additions produce batch #1, #2, #3, progressing independently")
    void testMultipleSequentialAdditionsToSameOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order order = createTestOrder(107L, "PREPARING");
        OrderItem b1 = createTestOrderItem(1L, order, itemBurger, 1, "READY", 1, null);
        OrderItem b2 = createTestOrderItem(2L, order, itemFries, 1, "PREPARING", 2, null);

        when(orderItemRepository.findByOrderIdAndRestaurantId(107L, 1L))
                .thenReturn(new ArrayList<>(List.of(b1, b2)));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(503L, 2, "Chilled")));

        orderService.addItemsToOrder(107L, request);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());
        OrderItem b3 = captor.getValue().get(0);

        assertEquals(3, b3.getBatchNumber());
        assertEquals("NEW", b3.getStatus());
        assertEquals("READY", b1.getStatus());
        assertEquals("PREPARING", b2.getStatus());
    }

    // 8. KDS real-time updates publish events
    @Test
    @DisplayName("8. Real-time WebSocket notifications are published on item addition and batch transition")
    void testKdsRealTimeUpdates() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order order = createTestOrder(108L, "ACCEPTED");
        OrderItem b1 = createTestOrderItem(1L, order, itemBurger, 1, "ACCEPTED", 1, null);
        when(orderItemRepository.findByOrderIdAndRestaurantId(108L, 1L)).thenReturn(new ArrayList<>(List.of(b1)));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(502L, 1, null)));

        orderService.addItemsToOrder(108L, request);

        verify(eventPublisher).publishOrderUpdated(eq(order), eq(2), eq(true));

        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentUserId()).thenReturn(200L);
        OrderItem b2 = createTestOrderItem(2L, order, itemFries, 1, "NEW", 2, null);
        when(orderItemRepository.findByOrderIdAndRestaurantId(108L, 1L)).thenReturn(List.of(b1, b2));

        kitchenService.acceptBatch(108L, 2);
        verify(eventPublisher).publishOrderStatusChanged(eq(order), eq(2), eq(true));
    }

    // 9. Correct READY eligibility when old and new items have different states
    @Test
    @DisplayName("9. Order only becomes READY when all batches are READY; premature READY is prevented")
    void testCorrectReadyEligibilityWhenOldAndNewItemsHaveDifferentStates() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(200L);

        Order order = createTestOrder(109L, "PREPARING");
        OrderItem b1 = createTestOrderItem(1L, order, itemBurger, 1, "PREPARING", 1, null);
        OrderItem b2 = createTestOrderItem(2L, order, itemFries, 1, "PREPARING", 2, null);

        when(orderItemRepository.findByOrderIdAndRestaurantId(109L, 1L)).thenReturn(List.of(b1, b2));

        // Mark batch #1 READY
        kitchenService.readyBatch(109L, 1);

        assertEquals("READY", b1.getStatus());
        assertEquals("PREPARING", b2.getStatus());
        // Since batch #2 is still PREPARING, overall order MUST NOT be READY
        assertEquals("PREPARING", order.getStatus());

        // Mark batch #2 READY
        kitchenService.readyBatch(109L, 2);

        assertEquals("READY", b1.getStatus());
        assertEquals("READY", b2.getStatus());
        // Now that ALL batches are READY, order becomes READY
        assertEquals("READY", order.getStatus());
    }

    // 10. Tenant isolation and cancellation rules
    @Test
    @DisplayName("10. Tenant isolation prevents cross-tenant access and cancellation is rejected if items are progressing")
    void testTenantIsolationAndCancellationRules() {
        Order order = createTestOrder(110L, "PREPARING");
        OrderItem b1 = createTestOrderItem(1L, order, itemBurger, 1, "PREPARING", 1, null);
        OrderItem b2 = createTestOrderItem(2L, order, itemFries, 1, "NEW", 2, null);
        when(orderItemRepository.findByOrderIdAndRestaurantId(110L, 1L)).thenReturn(List.of(b1, b2));

        // Cross-tenant access check
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);
        when(securityUtils.getCurrentUserId()).thenReturn(999L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        OrderRequest crossTenantReq = new OrderRequest();
        crossTenantReq.setItems(List.of(new OrderItemRequest(503L, 1, null)));

        assertThrows(RuntimeException.class, () ->
                orderService.addItemsToOrder(110L, crossTenantReq)
        );

        // Cancellation check: Waiter cannot cancel an order once any item has progressed past NEW
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        OrderWorkflowConflictException cancelEx = assertThrows(OrderWorkflowConflictException.class, () ->
                orderService.cancelOrder(110L)
        );
        assertTrue(cancelEx.getMessage().contains("Order cannot be cancelled after kitchen acceptance"));
    }

    // 11. Adding items to a READY order resets order status to PREPARING
    @Test
    @DisplayName("11. Adding items to a READY order resets order status to PREPARING to prevent premature billing")
    void testAddingItemsToReadyOrderResetsStatus() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order order = createTestOrder(111L, "READY");
        OrderItem b1 = createTestOrderItem(1L, order, itemBurger, 1, "READY", 1, null);
        when(orderItemRepository.findByOrderIdAndRestaurantId(111L, 1L)).thenReturn(new ArrayList<>(List.of(b1)));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(502L, 1, null)));

        orderService.addItemsToOrder(111L, request);

        assertEquals("PREPARING", order.getStatus());
    }
}
