package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.BillRequest;
import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderResponse;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.exception.OrderWorkflowConflictException;
import com.restaurant.restaurantpos.repository.*;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.AutoAcceptService;
import com.restaurant.restaurantpos.service.BillService;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KitchenIncrementalEdgeCasesIntegrationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private KitchenOrderEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;
    @Mock private BillRepository billRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RestaurantSettingsRepository settingsRepository;

    private OrderService orderService;
    private KitchenService kitchenService;
    private AutoAcceptService autoAcceptService;
    private BillService billService;

    private Restaurant restaurant;
    private RestaurantSettings settingsKdsOnAutoAcceptOff;
    private RestaurantSettings settingsKdsOnAutoAcceptOn;
    private User waiter;
    private User kitchenStaff;
    private User admin;
    private RestaurantTable table;
    private MenuItem itemBurger;
    private MenuItem itemFries;
    private MenuItem itemDrink;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Grand Bistro");

        settingsKdsOnAutoAcceptOff = new RestaurantSettings();
        settingsKdsOnAutoAcceptOff.setId(10L);
        settingsKdsOnAutoAcceptOff.setRestaurant(restaurant);
        settingsKdsOnAutoAcceptOff.setKitchenEnabled(true);
        settingsKdsOnAutoAcceptOff.setAutoAcceptOrders(false);

        settingsKdsOnAutoAcceptOn = new RestaurantSettings();
        settingsKdsOnAutoAcceptOn.setId(11L);
        settingsKdsOnAutoAcceptOn.setRestaurant(restaurant);
        settingsKdsOnAutoAcceptOn.setKitchenEnabled(true);
        settingsKdsOnAutoAcceptOn.setAutoAcceptOrders(true);

        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsOnAutoAcceptOff));

        Role waiterRole = new Role(1L, "WAITER");
        waiter = new User();
        waiter.setId(100L);
        waiter.setName("John Waiter");
        waiter.setRole(waiterRole);
        waiter.setRestaurant(restaurant);

        Role kitchenRole = new Role(2L, "KITCHEN");
        kitchenStaff = new User();
        kitchenStaff.setId(200L);
        kitchenStaff.setName("Chef Ramsay");
        kitchenStaff.setRole(kitchenRole);
        kitchenStaff.setRestaurant(restaurant);

        Role adminRole = new Role(3L, "ADMIN");
        admin = new User();
        admin.setId(300L);
        admin.setName("Admin Manager");
        admin.setRole(adminRole);
        admin.setRestaurant(restaurant);

        table = new RestaurantTable();
        table.setId(10L);
        table.setTableNumber(5);
        table.setRestaurant(restaurant);
        table.setStatus("OCCUPIED");

        Category mainCat = new Category();
        mainCat.setId(1L);
        mainCat.setName("Mains");
        mainCat.setIsActive(true);
        mainCat.setRestaurant(restaurant);

        itemBurger = new MenuItem();
        itemBurger.setId(501L);
        itemBurger.setName("Gourmet Burger");
        itemBurger.setPrice(new BigDecimal("15.00"));
        itemBurger.setIsAvailable(true);
        itemBurger.setCategory(mainCat);
        itemBurger.setRestaurant(restaurant);

        itemFries = new MenuItem();
        itemFries.setId(502L);
        itemFries.setName("Truffle Fries");
        itemFries.setPrice(new BigDecimal("7.00"));
        itemFries.setIsAvailable(true);
        itemFries.setCategory(mainCat);
        itemFries.setRestaurant(restaurant);

        itemDrink = new MenuItem();
        itemDrink.setId(503L);
        itemDrink.setName("Craft Cola");
        itemDrink.setPrice(new BigDecimal("4.00"));
        itemDrink.setIsAvailable(true);
        itemDrink.setCategory(mainCat);
        itemDrink.setRestaurant(restaurant);

        when(menuItemRepository.findByIdAndRestaurantId(501L, 1L)).thenReturn(Optional.of(itemBurger));
        when(menuItemRepository.findByIdAndRestaurantId(502L, 1L)).thenReturn(Optional.of(itemFries));
        when(menuItemRepository.findByIdAndRestaurantId(503L, 1L)).thenReturn(Optional.of(itemDrink));
        when(menuItemRepository.findById(501L)).thenReturn(Optional.of(itemBurger));
        when(menuItemRepository.findById(502L)).thenReturn(Optional.of(itemFries));
        when(menuItemRepository.findById(503L)).thenReturn(Optional.of(itemDrink));

        when(userRepository.getReferenceById(100L)).thenReturn(waiter);
        when(userRepository.getReferenceById(200L)).thenReturn(kitchenStaff);
        when(userRepository.getReferenceById(300L)).thenReturn(admin);
        when(userRepository.findById(100L)).thenReturn(Optional.of(waiter));
        when(userRepository.findById(200L)).thenReturn(Optional.of(kitchenStaff));
        when(userRepository.findById(300L)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdAndRestaurantId(300L, 1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(waiter));
        when(userRepository.findByIdAndRestaurantId(200L, 1L)).thenReturn(Optional.of(kitchenStaff));

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

        autoAcceptService = new AutoAcceptService(
                orderRepository, settingsRepository, orderStatusHistoryRepository,
                userRepository, eventPublisher, notificationService, orderItemRepository
        );

        billService = new BillService(
                billRepository, paymentRepository, orderRepository,
                orderStatusHistoryRepository, tableRepository, userRepository,
                securityUtils, eventPublisher, notificationService, settingsRepository
        );
    }

    private Order createMockOrder(Long orderId, String status) {
        Order order = new Order();
        order.setId(orderId);
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setWaiter(waiter);
        order.setOrderType("DINE_IN");
        order.setStatus(status);
        order.setSubtotal(new BigDecimal("15.00"));
        order.setTaxAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("15.00"));
        order.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        order.setUpdatedAt(LocalDateTime.now().minusMinutes(20));

        when(orderRepository.findByIdAndRestaurantId(orderId, 1L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndRestaurantIdForUpdate(orderId, 1L)).thenReturn(Optional.of(order));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(billRepository.existsByOrderId(orderId)).thenReturn(false);
        when(billRepository.existsByRestaurantIdAndOrderId(1L, orderId)).thenReturn(false);

        return order;
    }

    private OrderItem createMockOrderItem(Long itemId, Order order, MenuItem item, int qty, String status, int batchNumber, String notes) {
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

    // =========================================================================
    // 1. Incremental additions with Auto-Accept both ON and OFF
    // =========================================================================

    @Test
    @DisplayName("Edge Case 1a: Incremental additions with Auto-Accept OFF - New batch remains NEW; existing batch unchanged")
    void testIncrementalAddition_AutoAcceptOff() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsOnAutoAcceptOff));

        Order order = createMockOrder(201L, "PREPARING");
        OrderItem b1 = createMockOrderItem(1L, order, itemBurger, 2, "PREPARING", 1, "Well done");
        when(orderItemRepository.findByOrderIdAndRestaurantId(201L, 1L)).thenReturn(new ArrayList<>(List.of(b1)));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(502L, 1, "Extra salt")));

        orderService.addItemsToOrder(201L, request);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());
        OrderItem b2 = captor.getValue().get(0);

        assertEquals("NEW", b2.getStatus(), "New batch must start in NEW when Auto-Accept is OFF");
        assertEquals(2, b2.getBatchNumber());
        assertEquals("PREPARING", b1.getStatus(), "Existing batch must remain PREPARING");

        // Attempting to run autoAcceptService when Auto-Accept is OFF
        boolean autoAccepted = autoAcceptService.autoAcceptSingleOrder(201L);
        assertFalse(autoAccepted, "Auto-accept must return false when Auto-Accept is OFF");
        assertEquals("NEW", b2.getStatus(), "New batch must stay NEW");
        assertEquals("PREPARING", b1.getStatus(), "Existing batch must remain PREPARING");
    }

    @Test
    @DisplayName("Edge Case 1b: Incremental additions with Auto-Accept ON - New batch transitions to ACCEPTED; existing batch PREPARING unchanged")
    void testIncrementalAddition_AutoAcceptOn() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsOnAutoAcceptOn));

        Order order = createMockOrder(202L, "PREPARING");
        OrderItem b1 = createMockOrderItem(1L, order, itemBurger, 1, "PREPARING", 1, null);
        OrderItem b2 = createMockOrderItem(2L, order, itemFries, 2, "NEW", 2, null);

        when(orderItemRepository.findByOrderId(202L)).thenReturn(List.of(b1, b2));
        when(orderItemRepository.findByOrderIdAndRestaurantId(202L, 1L)).thenReturn(List.of(b1, b2));
        when(orderItemRepository.findByOrderIdAndBatchNumber(202L, 2)).thenReturn(List.of(b2));

        // When autoAcceptSingleOrder runs for an order with Auto-Accept ON
        boolean autoAccepted = autoAcceptService.autoAcceptSingleOrder(202L);
        assertTrue(autoAccepted, "Auto-accept must succeed for pending NEW batch when Auto-Accept is ON");

        assertEquals("ACCEPTED", b2.getStatus(), "Batch #2 must be transitioned to ACCEPTED");
        assertEquals("PREPARING", b1.getStatus(), "Batch #1 must retain its PREPARING state without being reset");
        assertEquals("PREPARING", order.getStatus(), "Overall order status must remain PREPARING");

        verify(eventPublisher).publishOrderStatusChanged(eq(order), eq(2), eq(true));
    }

    // =========================================================================
    // 2. Adding items when overall order is READY
    // =========================================================================

    @Test
    @DisplayName("Edge Case 2: Adding items to READY order resets order to PREPARING, preserves existing READY items, and recalculates READY only when all batches ready")
    void testAddingItemsWhenOverallOrderIsReady() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsOnAutoAcceptOff));

        Order order = createMockOrder(203L, "READY");
        OrderItem b1 = createMockOrderItem(10L, order, itemBurger, 2, "READY", 1, "Medium");
        when(orderItemRepository.findByOrderIdAndRestaurantId(203L, 1L)).thenReturn(new ArrayList<>(List.of(b1)));

        // Waiter adds a new item (Batch 2)
        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(503L, 1, "With ice")));

        orderService.addItemsToOrder(203L, request);

        // 1. Overall status resets from READY to PREPARING
        assertEquals("PREPARING", order.getStatus(), "Overall order status must reset to PREPARING to prevent premature billing");

        // 2. Existing items in Batch 1 must preserve READY status, id, and quantity
        assertEquals("READY", b1.getStatus());
        assertEquals(2, b1.getQuantity());
        assertEquals("Medium", b1.getNotes());
        assertEquals(10L, b1.getId());

        // 3. New item in Batch 2 is NEW
        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());
        OrderItem b2 = captor.getValue().get(0);
        b2.setId(11L);
        assertEquals("NEW", b2.getStatus());
        assertEquals(2, b2.getBatchNumber());

        // 4. Kitchen advances Batch 2: NEW -> ACCEPTED -> PREPARING -> READY
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentUserId()).thenReturn(200L);
        when(orderItemRepository.findByOrderIdAndRestaurantId(203L, 1L)).thenReturn(List.of(b1, b2));

        // Accept Batch 2
        kitchenService.acceptBatch(203L, 2);
        assertEquals("ACCEPTED", b2.getStatus());
        assertEquals("READY", b1.getStatus(), "Batch 1 must stay READY");
        assertEquals("PREPARING", order.getStatus());

        // Prepare Batch 2
        kitchenService.prepareBatch(203L, 2);
        assertEquals("PREPARING", b2.getStatus());
        assertEquals("READY", b1.getStatus());
        assertEquals("PREPARING", order.getStatus());

        // Ready Batch 2
        kitchenService.readyBatch(203L, 2);
        assertEquals("READY", b2.getStatus());
        assertEquals("READY", b1.getStatus());

        // 5. Now that ALL batches (1 and 2) are READY, overall order becomes READY again!
        assertEquals("READY", order.getStatus(), "Order must recalculate to READY once all batches are ready");
    }

    // =========================================================================
    // 3. Batch-specific endpoints cannot modify another batch + role & tenant permissions
    // =========================================================================

    @Test
    @DisplayName("Edge Case 3a: Batch endpoints only modify targeted batch; cannot modify another batch")
    void testBatchEndpointsCannotModifyAnotherBatch() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(200L);

        Order order = createMockOrder(204L, "PREPARING");
        OrderItem b1 = createMockOrderItem(1L, order, itemBurger, 1, "PREPARING", 1, null);
        OrderItem b2 = createMockOrderItem(2L, order, itemFries, 1, "NEW", 2, null);
        OrderItem b3 = createMockOrderItem(3L, order, itemDrink, 1, "READY", 3, null);

        when(orderItemRepository.findByOrderIdAndRestaurantId(204L, 1L)).thenReturn(List.of(b1, b2, b3));

        // Accept ONLY Batch 2
        kitchenService.acceptBatch(204L, 2);

        assertEquals("ACCEPTED", b2.getStatus(), "Batch 2 must be ACCEPTED");
        assertEquals("PREPARING", b1.getStatus(), "Batch 1 must not be modified");
        assertEquals("READY", b3.getStatus(), "Batch 3 must not be modified");

        // Attempt invalid state jump: Readying Batch 2 while it is in ACCEPTED
        RuntimeException invalidTransitionEx = assertThrows(RuntimeException.class, () ->
                kitchenService.readyBatch(204L, 2)
        );
        assertTrue(invalidTransitionEx.getMessage().contains("Invalid status transition to READY"));
    }

    @Test
    @DisplayName("Edge Case 3b: Batch endpoints enforce tenant isolation and reject cross-tenant access")
    void testBatchEndpointsEnforceTenantIsolation() {
        Order order = createMockOrder(205L, "PREPARING");
        OrderItem b1 = createMockOrderItem(1L, order, itemBurger, 1, "NEW", 1, null);

        // Security context is Restaurant 2 (different tenant)
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentUserId()).thenReturn(200L);

        assertThrows(RuntimeException.class, () ->
                kitchenService.acceptBatch(205L, 1)
        );
    }

    @Test
    @DisplayName("Edge Case 3c: Batch endpoints enforce role permissions - WAITER role is denied access")
    void testBatchEndpointsEnforceKitchenRolePermissions() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER"); // WAITER role

        assertThrows(AccessDeniedException.class, () ->
                kitchenService.acceptBatch(204L, 1)
        );

        assertThrows(AccessDeniedException.class, () ->
                kitchenService.prepareBatch(204L, 1)
        );

        assertThrows(AccessDeniedException.class, () ->
                kitchenService.readyBatch(204L, 1)
        );
    }

    // =========================================================================
    // 4. Concurrent additions cannot create duplicate or incorrectly ordered batch numbers
    // =========================================================================

    @Test
    @DisplayName("Edge Case 4: Concurrent additions cannot create duplicate or incorrectly ordered batch numbers")
    void testConcurrentAdditionsSequentialBatchOrdering() throws Exception {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order order = createMockOrder(206L, "PREPARING");
        OrderItem b1 = createMockOrderItem(1L, order, itemBurger, 1, "PREPARING", 1, null);

        List<OrderItem> liveDatabaseItems = Collections.synchronizedList(new ArrayList<>(List.of(b1)));
        when(orderItemRepository.findByOrderIdAndRestaurantId(eq(206L), eq(1L))).thenAnswer(inv -> new ArrayList<>(liveDatabaseItems));

        doAnswer(inv -> {
            List<OrderItem> saving = inv.getArgument(0);
            liveDatabaseItems.addAll(saving);
            return saving;
        }).when(orderItemRepository).saveAll(anyList());

        // Concurrently simulate 4 additions to the same order
        int numberOfConcurrentAdditions = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentAdditions);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfConcurrentAdditions);
        List<Integer> assignedBatches = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger idGenerator = new AtomicInteger(10);

        // Synchronize on the order row lock (mimicking SELECT ... FOR UPDATE)
        Object orderLock = new Object();

        for (int i = 0; i < numberOfConcurrentAdditions; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    synchronized (orderLock) {
                        OrderRequest req = new OrderRequest();
                        req.setItems(List.of(new OrderItemRequest(502L, 1, "Concurrent addition #" + index)));
                        orderService.addItemsToOrder(206L, req);

                        OrderItem latest = liveDatabaseItems.get(liveDatabaseItems.size() - 1);
                        latest.setId((long) idGenerator.incrementAndGet());
                        assignedBatches.add(latest.getBatchNumber());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All concurrent additions must complete");
        executor.shutdown();

        // Verify that 4 batches were created
        assertEquals(4, assignedBatches.size());
        Collections.sort(assignedBatches);
        // Batch numbers must be strictly 2, 3, 4, 5 without duplicates!
        assertEquals(List.of(2, 3, 4, 5), assignedBatches, "Batch numbers must be strictly sequential (2, 3, 4, 5) without duplicates");
    }

    // =========================================================================
    // 5. Fresh order, legacy items, multiple additions, billing, and cancellation
    // =========================================================================

    @Test
    @DisplayName("Edge Case 5: Fresh order, legacy items without batch, multiple additions, billing eligibility, and cancellation rules")
    void testCompleteLifecycleFreshOrderLegacyItemsBillingAndCancellation() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        // 1. Fresh order creation: batch 1
        Order freshOrder = createMockOrder(207L, "NEW");
        OrderItem initialItem = createMockOrderItem(1L, freshOrder, itemBurger, 1, "NEW", 1, null);
        when(orderItemRepository.findByOrderIdAndRestaurantId(207L, 1L)).thenReturn(new ArrayList<>(List.of(initialItem)));

        // 2. Legacy items support: simulated legacy item with null status and null batchNumber
        OrderItem legacyItem = new OrderItem();
        legacyItem.setId(2L);
        legacyItem.setOrder(freshOrder);
        legacyItem.setRestaurant(restaurant);
        legacyItem.setMenuItem(itemBurger);
        legacyItem.setQuantity(1);
        legacyItem.setUnitPrice(itemBurger.getPrice());
        legacyItem.setSubtotal(itemBurger.getPrice());
        legacyItem.setStatus(null); // legacy null
        legacyItem.setBatchNumber(null); // legacy null

        when(orderItemRepository.findByOrderIdAndRestaurantId(207L, 1L))
                .thenReturn(new ArrayList<>(List.of(initialItem, legacyItem)));

        // 3. Waiter adds items (Batch 2)
        OrderRequest addReq1 = new OrderRequest();
        addReq1.setItems(List.of(new OrderItemRequest(502L, 1, "Addon batch 2")));
        orderService.addItemsToOrder(207L, addReq1);

        ArgumentCaptor<List<OrderItem>> captor1 = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor1.capture());
        OrderItem batch2Item = captor1.getValue().get(0);
        assertEquals(2, batch2Item.getBatchNumber(), "First addition must be batch #2");
        assertEquals("NEW", batch2Item.getStatus());

        // 4. Waiter adds more items (Batch 3)
        when(orderItemRepository.findByOrderIdAndRestaurantId(207L, 1L))
                .thenReturn(new ArrayList<>(List.of(initialItem, legacyItem, batch2Item)));
        OrderRequest addReq2 = new OrderRequest();
        addReq2.setItems(List.of(new OrderItemRequest(503L, 2, "Addon batch 3")));
        orderService.addItemsToOrder(207L, addReq2);

        ArgumentCaptor<List<OrderItem>> captor2 = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository, atLeastOnce()).saveAll(captor2.capture());
        OrderItem batch3Item = captor2.getValue().get(0);
        assertEquals(3, batch3Item.getBatchNumber(), "Second addition must be batch #3");

        // 5. Billing attempt while order is PREPARING must be REJECTED
        freshOrder.setStatus("PREPARING");
        initialItem.setStatus("READY");
        legacyItem.setStatus("READY");
        batch2Item.setStatus("PREPARING");
        batch3Item.setStatus("NEW");
        when(orderItemRepository.findByOrderIdAndRestaurantId(207L, 1L))
                .thenReturn(List.of(initialItem, legacyItem, batch2Item, batch3Item));

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(securityUtils.getCurrentUserId()).thenReturn(300L);
        BillRequest billRequest = new BillRequest();
        billRequest.setOrderId(207L);

        OrderWorkflowConflictException billEx = assertThrows(OrderWorkflowConflictException.class, () ->
                billService.createBill(billRequest)
        );
        assertTrue(billEx.getMessage().contains("Order is not in READY status"));

        // 6. Cancellation attempt while kitchen is preparing must be REJECTED
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentUserId()).thenReturn(100L);
        OrderWorkflowConflictException cancelEx = assertThrows(OrderWorkflowConflictException.class, () ->
                orderService.cancelOrder(207L)
        );
        assertTrue(cancelEx.getMessage().contains("Order cannot be cancelled after kitchen acceptance"));

        // 7. Advance all items to READY -> Billing SUCCEEDS
        initialItem.setStatus("READY");
        legacyItem.setStatus("READY");
        batch2Item.setStatus("READY");
        batch3Item.setStatus("READY");
        freshOrder.setStatus("READY");

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(securityUtils.getCurrentUserId()).thenReturn(300L);
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> {
            Bill b = i.getArgument(0);
            b.setId(5001L);
            return b;
        });

        assertDoesNotThrow(() -> billService.createBill(billRequest));
    }
}
