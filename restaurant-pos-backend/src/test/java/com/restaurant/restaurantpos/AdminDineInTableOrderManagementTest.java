package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.Category;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.exception.OrderWorkflowConflictException;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.NotificationService;
import com.restaurant.restaurantpos.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminDineInTableOrderManagementTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private BillRepository billRepository;
    @Mock private RestaurantSettingsRepository settingsRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private KitchenOrderEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;

    @InjectMocks private OrderService orderService;

    private Restaurant restaurant;
    private Restaurant otherRestaurant;
    private User adminUser;
    private RestaurantTable table1;
    private RestaurantTable table2;
    private MenuItem item1;
    private MenuItem item2;
    private Category activeCategory;
    private RestaurantSettings settingsKdsEnabled;
    private RestaurantSettings settingsKdsDisabled;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Main Restaurant");

        otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        otherRestaurant.setName("Other Restaurant");

        adminUser = new User();
        adminUser.setId(99L);
        adminUser.setRestaurant(restaurant);

        table1 = new RestaurantTable();
        table1.setId(10L);
        table1.setTableNumber(5);
        table1.setCapacity(4);
        table1.setStatus("AVAILABLE");
        table1.setRestaurant(restaurant);

        table2 = new RestaurantTable();
        table2.setId(20L);
        table2.setTableNumber(6);
        table2.setCapacity(2);
        table2.setStatus("OCCUPIED");
        table2.setRestaurant(restaurant);

        activeCategory = new Category();
        activeCategory.setId(100L);
        activeCategory.setIsActive(true);
        activeCategory.setRestaurant(restaurant);

        item1 = new MenuItem();
        item1.setId(1001L);
        item1.setName("Margherita Pizza");
        item1.setPrice(new BigDecimal("12.00"));
        item1.setIsAvailable(true);
        item1.setCategory(activeCategory);
        item1.setRestaurant(restaurant);

        item2 = new MenuItem();
        item2.setId(1002L);
        item2.setName("Lemonade");
        item2.setPrice(new BigDecimal("4.00"));
        item2.setIsAvailable(true);
        item2.setCategory(activeCategory);
        item2.setRestaurant(restaurant);

        settingsKdsEnabled = new RestaurantSettings();
        settingsKdsEnabled.setRestaurant(restaurant);
        settingsKdsEnabled.setKitchenEnabled(true);

        settingsKdsDisabled = new RestaurantSettings();
        settingsKdsDisabled.setRestaurant(restaurant);
        settingsKdsDisabled.setKitchenEnabled(false);
    }

    // -------------------------------------------------------------------------
    // 1. Admin creates an order from an available table
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("1. Admin creates a dine-in order from an available table -> sets table to OCCUPIED and saves order")
    void testAdminCreatesOrderFromAvailableTable() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        when(tableRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(table1));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(10L), eq("DINE_IN"), anyCollection()))
                .thenReturn(false);
        when(menuItemRepository.findByIdAndRestaurantId(1001L, 1L)).thenReturn(Optional.of(item1));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);

        Order savedOrder = new Order();
        savedOrder.setId(500L);
        savedOrder.setRestaurant(restaurant);
        savedOrder.setTable(table1);
        savedOrder.setOrderType("DINE_IN");
        savedOrder.setStatus("NEW");
        savedOrder.setSubtotal(new BigDecimal("24.00"));
        savedOrder.setTotalAmount(new BigDecimal("24.00"));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderItem savedItem = new OrderItem();
        savedItem.setId(1L);
        savedItem.setMenuItem(item1);
        savedItem.setQuantity(2);
        savedItem.setUnitPrice(new BigDecimal("12.00"));
        savedItem.setSubtotal(new BigDecimal("24.00"));
        savedItem.setBatchNumber(1);
        savedItem.setStatus("NEW");
        when(orderItemRepository.saveAll(anyList())).thenReturn(List.of(savedItem));

        OrderRequest request = new OrderRequest();
        request.setOrderType("DINE_IN");
        request.setTableId(10L);
        request.setNotes("Admin created table order");
        request.setItems(List.of(new OrderItemRequest(1001L, 2, "Extra cheese")));

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals("NEW", response.getStatus());

        // Verify table status is updated to OCCUPIED
        assertEquals("OCCUPIED", table1.getStatus());
        verify(tableRepository).save(table1);
        verify(eventPublisher).publishOrderCreated(savedOrder);
        verify(notificationService).notifyOrderCreated(savedOrder);
    }

    // -------------------------------------------------------------------------
    // 2. Admin adds items to an occupied table with KDS enabled
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("2. Admin adds items to occupied table with KDS enabled -> creates incremental batch")
    void testAdminAddsItemsWithKdsEnabled() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order existingOrder = new Order();
        existingOrder.setId(600L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setTable(table2);
        existingOrder.setOrderType("DINE_IN");
        existingOrder.setStatus("PREPARING");
        existingOrder.setSubtotal(new BigDecimal("12.00"));
        existingOrder.setTaxAmount(BigDecimal.ZERO);
        existingOrder.setDiscountAmount(BigDecimal.ZERO);
        existingOrder.setTotalAmount(new BigDecimal("12.00"));

        when(orderRepository.findByIdAndRestaurantIdForUpdate(600L, 1L)).thenReturn(Optional.of(existingOrder));
        when(billRepository.existsByOrderId(600L)).thenReturn(false);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsEnabled));

        // Existing Batch 1 item
        OrderItem batch1Item = new OrderItem();
        batch1Item.setId(10L);
        batch1Item.setOrder(existingOrder);
        batch1Item.setMenuItem(item1);
        batch1Item.setQuantity(1);
        batch1Item.setUnitPrice(new BigDecimal("12.00"));
        batch1Item.setSubtotal(new BigDecimal("12.00"));
        batch1Item.setBatchNumber(1);
        batch1Item.setStatus("PREPARING");

        // New Batch 2 item to be returned after add
        OrderItem batch2Item = new OrderItem();
        batch2Item.setId(11L);
        batch2Item.setOrder(existingOrder);
        batch2Item.setMenuItem(item2);
        batch2Item.setQuantity(2);
        batch2Item.setUnitPrice(new BigDecimal("4.00"));
        batch2Item.setSubtotal(new BigDecimal("8.00"));
        batch2Item.setBatchNumber(2);
        batch2Item.setStatus("NEW");

        when(orderItemRepository.findByOrderId(600L))
                .thenReturn(new ArrayList<>(List.of(batch1Item))) // first call for existingItems
                .thenReturn(List.of(batch1Item, batch2Item));     // second call for allItems

        when(menuItemRepository.findByIdAndRestaurantId(1002L, 1L)).thenReturn(Optional.of(item2));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);

        OrderRequest addReq = new OrderRequest();
        addReq.setItems(List.of(new OrderItemRequest(1002L, 2, "Cold")));

        OrderResponse response = orderService.addItemsToOrder(600L, addReq);

        assertNotNull(response);
        // Verify KDS WebSocket event with nextBatch (2) and isUpdate=true
        verify(eventPublisher).publishOrderUpdated(existingOrder, 2, true);
    }

    // -------------------------------------------------------------------------
    // 3. Existing batches retain their states and new items appear in correct batch
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("3. Existing batches retain their states and new items appear in correct batch number")
    void testExistingBatchesRetainStates() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order existingOrder = new Order();
        existingOrder.setId(700L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setTable(table2);
        existingOrder.setOrderType("DINE_IN");
        existingOrder.setStatus("READY"); // Order ready from kitchen
        existingOrder.setSubtotal(new BigDecimal("24.00"));
        existingOrder.setTaxAmount(BigDecimal.ZERO);
        existingOrder.setDiscountAmount(BigDecimal.ZERO);
        existingOrder.setTotalAmount(new BigDecimal("24.00"));

        when(orderRepository.findByIdAndRestaurantIdForUpdate(700L, 1L)).thenReturn(Optional.of(existingOrder));
        when(billRepository.existsByOrderId(700L)).thenReturn(false);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsEnabled));

        OrderItem b1Item = new OrderItem();
        b1Item.setId(21L);
        b1Item.setOrder(existingOrder);
        b1Item.setMenuItem(item1);
        b1Item.setQuantity(2);
        b1Item.setUnitPrice(new BigDecimal("12.00"));
        b1Item.setSubtotal(new BigDecimal("24.00"));
        b1Item.setBatchNumber(1);
        b1Item.setStatus("READY"); // Retains READY state!

        when(orderItemRepository.findByOrderId(700L))
                .thenReturn(new ArrayList<>(List.of(b1Item)))
                .thenReturn(List.of(b1Item));

        when(menuItemRepository.findByIdAndRestaurantId(1002L, 1L)).thenReturn(Optional.of(item2));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);

        OrderRequest addReq = new OrderRequest();
        addReq.setItems(List.of(new OrderItemRequest(1002L, 1, null)));

        orderService.addItemsToOrder(700L, addReq);

        verify(orderItemRepository).saveAll(itemsCaptor.capture());
        List<OrderItem> savedNewItems = itemsCaptor.getValue();
        assertEquals(1, savedNewItems.size());
        assertEquals(2, savedNewItems.get(0).getBatchNumber()); // Batch #2
        assertEquals("NEW", savedNewItems.get(0).getStatus());

        // Batch 1 item state remains READY
        assertEquals("READY", b1Item.getStatus());
        assertEquals(1, b1Item.getBatchNumber());

        // When order was READY and new items are added, order goes back to PREPARING
        assertEquals("PREPARING", existingOrder.getStatus());
    }

    // -------------------------------------------------------------------------
    // 4. Admin adds items with KDS disabled
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("4. Admin adds items with KDS disabled -> combines matching items into Batch 1 without incremental batches")
    void testAdminAddsItemsWithKdsDisabled() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order existingOrder = new Order();
        existingOrder.setId(800L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setTable(table2);
        existingOrder.setOrderType("DINE_IN");
        existingOrder.setStatus("ACCEPTED");
        existingOrder.setSubtotal(new BigDecimal("12.00"));
        existingOrder.setTaxAmount(BigDecimal.ZERO);
        existingOrder.setDiscountAmount(BigDecimal.ZERO);
        existingOrder.setTotalAmount(new BigDecimal("12.00"));

        when(orderRepository.findByIdAndRestaurantIdForUpdate(800L, 1L)).thenReturn(Optional.of(existingOrder));
        when(billRepository.existsByOrderId(800L)).thenReturn(false);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsDisabled));

        // Existing item: 1x Pizza (item1) with no notes
        OrderItem existingItem = new OrderItem();
        existingItem.setId(30L);
        existingItem.setOrder(existingOrder);
        existingItem.setMenuItem(item1);
        existingItem.setQuantity(1);
        existingItem.setUnitPrice(new BigDecimal("12.00"));
        existingItem.setSubtotal(new BigDecimal("12.00"));
        existingItem.setBatchNumber(1);
        existingItem.setStatus("ACCEPTED");

        when(orderItemRepository.findByOrderId(800L))
                .thenReturn(new ArrayList<>(List.of(existingItem)))
                .thenReturn(List.of(existingItem));

        when(menuItemRepository.findByIdAndRestaurantId(1001L, 1L)).thenReturn(Optional.of(item1));
        when(orderRepository.save(any(Order.class))).thenReturn(existingOrder);

        // Add 2 more Pizzas with matching notes (null)
        OrderRequest addReq = new OrderRequest();
        addReq.setItems(List.of(new OrderItemRequest(1001L, 2, null)));

        orderService.addItemsToOrder(800L, addReq);

        // Quantity consolidated to 3
        assertEquals(3, existingItem.getQuantity());
        assertEquals(new BigDecimal("36.00"), existingItem.getSubtotal());

        // Event published without kitchen batch
        verify(eventPublisher).publishOrderUpdated(existingOrder);
    }

    // -------------------------------------------------------------------------
    // 5. Total amount updates authoritatively
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("5. Order total updates authoritatively on backend after adding items")
    void testOrderTotalUpdatesAuthoritatively() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order existingOrder = new Order();
        existingOrder.setId(850L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setTable(table2);
        existingOrder.setOrderType("DINE_IN");
        existingOrder.setStatus("NEW");
        existingOrder.setSubtotal(new BigDecimal("12.00"));
        existingOrder.setTaxAmount(BigDecimal.ZERO);
        existingOrder.setDiscountAmount(BigDecimal.ZERO);
        existingOrder.setTotalAmount(new BigDecimal("12.00"));

        when(orderRepository.findByIdAndRestaurantIdForUpdate(850L, 1L)).thenReturn(Optional.of(existingOrder));
        when(billRepository.existsByOrderId(850L)).thenReturn(false);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsEnabled));

        OrderItem existingItem = new OrderItem();
        existingItem.setId(40L);
        existingItem.setOrder(existingOrder);
        existingItem.setMenuItem(item1);
        existingItem.setQuantity(1);
        existingItem.setUnitPrice(new BigDecimal("12.00"));
        existingItem.setSubtotal(new BigDecimal("12.00"));
        existingItem.setBatchNumber(1);

        OrderItem newItem = new OrderItem();
        newItem.setId(41L);
        newItem.setOrder(existingOrder);
        newItem.setMenuItem(item2);
        newItem.setQuantity(3);
        newItem.setUnitPrice(new BigDecimal("4.00"));
        newItem.setSubtotal(new BigDecimal("12.00"));
        newItem.setBatchNumber(2);

        when(orderItemRepository.findByOrderId(850L))
                .thenReturn(new ArrayList<>(List.of(existingItem)))
                .thenReturn(List.of(existingItem, newItem));

        when(menuItemRepository.findByIdAndRestaurantId(1002L, 1L)).thenReturn(Optional.of(item2));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderRequest addReq = new OrderRequest();
        addReq.setItems(List.of(new OrderItemRequest(1002L, 3, null)));

        OrderResponse res = orderService.addItemsToOrder(850L, addReq);

        assertEquals(new BigDecimal("24.00"), res.getSubtotal());
        assertEquals(new BigDecimal("24.00"), res.getTotalAmount());
    }

    // -------------------------------------------------------------------------
    // 6. Eligible order cancellation works and prohibited cancellation is blocked
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("6. Admin cancels NEW order -> status becomes CANCELLED and table becomes AVAILABLE")
    void testAdminCancelsEligibleOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order order = new Order();
        order.setId(900L);
        order.setRestaurant(restaurant);
        order.setTable(table2); // Table 2 is OCCUPIED
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");

        when(orderRepository.findByIdAndRestaurantId(900L, 1L)).thenReturn(Optional.of(order));
        when(billRepository.existsByRestaurantIdAndOrderId(1L, 900L)).thenReturn(false);
        when(billRepository.existsByOrderId(900L)).thenReturn(false);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsEnabled));

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(20L), eq("DINE_IN"), anyCollection()))
                .thenReturn(false); // No other active orders on Table 2

        OrderResponse response = orderService.cancelOrder(900L);

        assertEquals("CANCELLED", response.getStatus());
        assertEquals("AVAILABLE", table2.getStatus());
        verify(tableRepository).save(table2);
        verify(eventPublisher).publishOrderStatusChanged(order);
        verify(notificationService).notifyOrderCancelled(order);
    }

    @Test
    @DisplayName("7. Admin cancellation is blocked when status is PREPARING or READY")
    void testAdminCancellationBlockedInKitchenPreparation() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order prepOrder = new Order();
        prepOrder.setId(901L);
        prepOrder.setRestaurant(restaurant);
        prepOrder.setTable(table2);
        prepOrder.setOrderType("DINE_IN");
        prepOrder.setStatus("PREPARING");

        when(orderRepository.findByIdAndRestaurantId(901L, 1L)).thenReturn(Optional.of(prepOrder));
        when(billRepository.existsByRestaurantIdAndOrderId(1L, 901L)).thenReturn(false);
        when(billRepository.existsByOrderId(901L)).thenReturn(false);

        assertThrows(OrderWorkflowConflictException.class, () -> orderService.cancelOrder(901L));
        verify(orderRepository, never()).save(prepOrder);
    }

    // -------------------------------------------------------------------------
    // 8. Table availability safeguard: Never release table while another active order still occupies it
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("8. Cancellation never releases table if another active order occupies it")
    void testCancellationKeepsTableOccupiedWhenOtherActiveOrdersExist() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order orderToCancel = new Order();
        orderToCancel.setId(902L);
        orderToCancel.setRestaurant(restaurant);
        orderToCancel.setTable(table2);
        orderToCancel.setOrderType("DINE_IN");
        orderToCancel.setStatus("NEW");

        when(orderRepository.findByIdAndRestaurantId(902L, 1L)).thenReturn(Optional.of(orderToCancel));
        when(billRepository.existsByRestaurantIdAndOrderId(1L, 902L)).thenReturn(false);
        when(billRepository.existsByOrderId(902L)).thenReturn(false);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsKdsEnabled));
        when(orderRepository.save(any(Order.class))).thenReturn(orderToCancel);

        // Another active order exists on Table 2!
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(20L), eq("DINE_IN"), anyCollection()))
                .thenReturn(true);

        orderService.cancelOrder(902L);

        // Table 2 must NOT be changed to AVAILABLE!
        assertEquals("OCCUPIED", table2.getStatus());
        verify(tableRepository, never()).save(table2);
    }

    // -------------------------------------------------------------------------
    // 9. Existing bills and payments cannot be corrupted by adding items
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("9. Adding items is blocked if a bill already exists for the order")
    void testCannotAddItemsWhenBillExists() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order billedOrder = new Order();
        billedOrder.setId(903L);
        billedOrder.setRestaurant(restaurant);
        billedOrder.setTable(table2);
        billedOrder.setOrderType("DINE_IN");
        billedOrder.setStatus("READY");

        when(orderRepository.findByIdAndRestaurantIdForUpdate(903L, 1L)).thenReturn(Optional.of(billedOrder));
        when(billRepository.existsByOrderId(903L)).thenReturn(true); // Bill exists!

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(903L, request));
        assertTrue(ex.getMessage().contains("Cannot add items because a bill has already been generated"));
    }

    @Test
    @DisplayName("9b. Cancellation is blocked if a bill already exists for the order")
    void testCannotCancelOrderWhenBillExists() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        Order billedOrder = new Order();
        billedOrder.setId(904L);
        billedOrder.setRestaurant(restaurant);
        billedOrder.setTable(table2);
        billedOrder.setOrderType("DINE_IN");
        billedOrder.setStatus("NEW");

        when(orderRepository.findByIdAndRestaurantId(904L, 1L)).thenReturn(Optional.of(billedOrder));
        when(billRepository.existsByRestaurantIdAndOrderId(1L, 904L)).thenReturn(true); // Bill exists!

        assertThrows(OrderWorkflowConflictException.class, () -> orderService.cancelOrder(904L));
    }

    // -------------------------------------------------------------------------
    // 10. Cross-tenant access is rejected
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("10. Cross-tenant order creation or update is strictly rejected")
    void testCrossTenantAccessIsRejected() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L); // Logged into restaurant 2

        // Table 1 belongs to restaurant 1
        when(tableRepository.findByIdAndRestaurantId(10L, 2L)).thenReturn(Optional.empty());
        when(tableRepository.findById(10L)).thenReturn(Optional.of(table1));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(req));
    }

    // -------------------------------------------------------------------------
    // 11. Concurrent actions cannot create duplicate active orders
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("11. Concurrent order creation for the same table throws conflict and blocks duplicate order")
    void testConcurrentOrderCreationOnSameTableIsBlocked() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        when(tableRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(table1));
        // Another request just created an active order on Table 1 concurrently!
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(10L), eq("DINE_IN"), anyCollection()))
                .thenReturn(true);

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(req));
        assertTrue(ex.getMessage().contains("Table already has an active order") || ex.getMessage().contains("Table is not available"));
        verify(orderRepository, never()).save(any(Order.class));
    }
}
