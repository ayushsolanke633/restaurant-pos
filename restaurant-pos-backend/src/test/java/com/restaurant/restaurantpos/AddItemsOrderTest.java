package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
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
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AddItemsOrderTest {

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

    @InjectMocks
    private OrderService orderService;

    private Restaurant restaurant;
    private User waiter1;
    private User waiter2;
    private Order order;
    private MenuItem menuItem1;
    private MenuItem menuItem2;

    @BeforeEach
    void setUp() {
        RestaurantSettings settings = new RestaurantSettings();
        settings.setKitchenEnabled(false);
        lenient().when(settingsRepository.findByRestaurantId(anyLong())).thenReturn(Optional.of(settings));

        restaurant = new Restaurant();
        restaurant.setId(1L);

        waiter1 = new User();
        waiter1.setId(10L);
        waiter1.setRestaurant(restaurant);

        waiter2 = new User();
        waiter2.setId(20L);
        waiter2.setRestaurant(restaurant);

        order = new Order();
        order.setId(100L);
        order.setRestaurant(restaurant);
        order.setWaiter(waiter1);
        order.setOrderType("DINE_IN");
        order.setStatus("PREPARING");
        order.setSubtotal(new BigDecimal("100.00"));
        order.setTaxAmount(new BigDecimal("10.00"));
        order.setDiscountAmount(new BigDecimal("5.00"));
        order.setTotalAmount(new BigDecimal("105.00"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        menuItem1 = new MenuItem();
        menuItem1.setId(1001L);
        menuItem1.setRestaurant(restaurant);
        menuItem1.setName("Coke");
        menuItem1.setPrice(new BigDecimal("50.00"));
        menuItem1.setIsAvailable(true);

        menuItem2 = new MenuItem();
        menuItem2.setId(1002L);
        menuItem2.setRestaurant(restaurant);
        menuItem2.setName("Burger");
        menuItem2.setPrice(new BigDecimal("150.00"));
        menuItem2.setIsAvailable(true);
    }

    @Test
    @DisplayName("1. WAITER can add items to own DINE_IN order")
    void testWaiterCanAddItemsToOwnDineInOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));

        OrderItem existingItem = new OrderItem();
        existingItem.setId(500L);
        existingItem.setOrder(order);
        existingItem.setMenuItem(menuItem1);
        existingItem.setQuantity(1);
        existingItem.setUnitPrice(new BigDecimal("50.00"));
        existingItem.setSubtotal(new BigDecimal("50.00"));

        when(orderItemRepository.findByOrderId(100L)).thenReturn(new ArrayList<>(List.of(existingItem)));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderRequest request = new OrderRequest();
        OrderItemRequest itemReq = new OrderItemRequest(1001L, 2, null);
        request.setItems(List.of(itemReq));

        OrderResponse response = orderService.addItemsToOrder(100L, request);

        assertNotNull(response);
        verify(orderItemRepository).saveAll(any());
        verify(eventPublisher).publishOrderUpdated(any(Order.class));
    }

    @Test
    @DisplayName("2. WAITER cannot add items to another waiter's order")
    void testWaiterCannotAddItemsToAnotherWaitersOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(20L); // Waiter 2
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order)); // Assigned to Waiter 1

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.addItemsToOrder(100L, request));
    }

    @Test
    @DisplayName("3. WAITER cannot add items to TAKEAWAY order")
    void testWaiterCannotAddItemsToTakeawayOrder() {
        order.setOrderType("TAKEAWAY");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.addItemsToOrder(100L, request));
    }

    @Test
    @DisplayName("4. KITCHEN cannot add items")
    void testKitchenCannotAddItems() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(30L);
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        assertThrows(AccessDeniedException.class, () -> orderService.addItemsToOrder(100L, request));
    }

    @Test
    @DisplayName("5. ADMIN can add items within tenant")
    void testAdminCanAddItemsWithinTenant() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));
        when(orderItemRepository.findByOrderId(100L)).thenReturn(new ArrayList<>());
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        OrderResponse response = orderService.addItemsToOrder(100L, request);
        assertNotNull(response);
    }

    @Test
    @DisplayName("6. Cross-restaurant order access is rejected")
    void testCrossRestaurantOrderAccessRejected() {
        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        order.setRestaurant(otherRestaurant);

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(100L, request));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("7. Cross-restaurant menu item is rejected")
    void testCrossRestaurantMenuItemRejected() {
        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        menuItem1.setRestaurant(otherRestaurant);

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(100L, request));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("8. Unavailable menu item is rejected")
    void testUnavailableMenuItemRejected() {
        menuItem1.setIsAvailable(false);

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        Exception ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(100L, request));
        assertTrue(ex.getMessage().toLowerCase().contains("not available"));
    }

    @Test
    @DisplayName("9. Quantity <= 0 is rejected")
    void testQuantityZeroOrNegativeRejected() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 0, null)));

        Exception ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(100L, request));
        assertTrue(ex.getMessage().toLowerCase().contains("quantity must be greater than 0"));
    }

    @Test
    @DisplayName("10. COMPLETED order rejects add-items")
    void testCompletedOrderRejectsAddItems() {
        order.setStatus("COMPLETED");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        Exception ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(100L, request));
        assertTrue(ex.getMessage().contains("Cannot add items to order with status COMPLETED"));
    }

    @Test
    @DisplayName("11. CANCELLED order rejects add-items")
    void testCancelledOrderRejectsAddItems() {
        order.setStatus("CANCELLED");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        Exception ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(100L, request));
        assertTrue(ex.getMessage().contains("Cannot add items to order with status CANCELLED"));
    }

    @Test
    @DisplayName("12. Existing items are preserved")
    void testExistingItemsArePreserved() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1002L)).thenReturn(Optional.of(menuItem2));

        OrderItem existingItem = new OrderItem();
        existingItem.setId(500L);
        existingItem.setOrder(order);
        existingItem.setMenuItem(menuItem1);
        existingItem.setQuantity(1);
        existingItem.setUnitPrice(new BigDecimal("50.00"));
        existingItem.setSubtotal(new BigDecimal("50.00"));

        when(orderItemRepository.findByOrderId(100L)).thenReturn(List.of(existingItem));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1002L, 1, null)));

        orderService.addItemsToOrder(100L, request);

        verify(orderItemRepository, never()).deleteByOrderId(anyLong());
        verify(orderItemRepository).saveAll(any());
    }

    @Test
    @DisplayName("13. Same item + same notes merges quantity")
    void testSameItemSameNotesMergesQuantity() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));

        OrderItem existingItem = new OrderItem();
        existingItem.setId(500L);
        existingItem.setOrder(order);
        existingItem.setMenuItem(menuItem1);
        existingItem.setQuantity(1);
        existingItem.setUnitPrice(new BigDecimal("50.00"));
        existingItem.setNotes("Less ice");
        existingItem.setSubtotal(new BigDecimal("50.00"));

        when(orderItemRepository.findByOrderId(100L)).thenReturn(List.of(existingItem));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 2, "Less ice")));

        orderService.addItemsToOrder(100L, request);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());

        List<OrderItem> savedList = captor.getValue();
        assertEquals(1, savedList.size());
        assertEquals(3, savedList.get(0).getQuantity());
        assertEquals(new BigDecimal("150.00"), savedList.get(0).getSubtotal());
    }

    @Test
    @DisplayName("14. Same item + different notes creates separate item")
    void testSameItemDifferentNotesCreatesSeparateItem() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));

        OrderItem existingItem = new OrderItem();
        existingItem.setId(500L);
        existingItem.setOrder(order);
        existingItem.setMenuItem(menuItem1);
        existingItem.setQuantity(1);
        existingItem.setUnitPrice(new BigDecimal("50.00"));
        existingItem.setNotes("Less ice");
        existingItem.setSubtotal(new BigDecimal("50.00"));

        when(orderItemRepository.findByOrderId(100L)).thenReturn(List.of(existingItem));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 2, "No ice")));

        orderService.addItemsToOrder(100L, request);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());

        List<OrderItem> savedList = captor.getValue();
        assertEquals(1, savedList.size()); // 1 new item added to save list
        assertEquals(2, savedList.get(0).getQuantity());
        assertEquals("No ice", savedList.get(0).getNotes());
    }

    @Test
    @DisplayName("15. Backend price is used instead of client price")
    void testBackendPriceIsUsed() {
        menuItem1.setPrice(new BigDecimal("75.00"));

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));
        when(orderItemRepository.findByOrderId(100L)).thenReturn(new ArrayList<>());
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 2, null)));

        orderService.addItemsToOrder(100L, request);

        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());

        List<OrderItem> savedList = captor.getValue();
        assertEquals(new BigDecimal("75.00"), savedList.get(0).getUnitPrice());
        assertEquals(new BigDecimal("150.00"), savedList.get(0).getSubtotal());
    }

    @Test
    @DisplayName("16. Order subtotal is recalculated correctly")
    void testOrderSubtotalRecalculated() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));

        OrderItem item1 = new OrderItem();
        item1.setMenuItem(menuItem1);
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("50.00"));
        item1.setSubtotal(new BigDecimal("100.00"));

        when(orderItemRepository.findByOrderId(100L)).thenReturn(List.of(item1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        OrderResponse response = orderService.addItemsToOrder(100L, request);
        assertNotNull(response);
        // 2 existing + 1 added = 3 Cokes @ 50.00 = 150.00 subtotal
        assertEquals(new BigDecimal("150.00"), order.getSubtotal());
    }

    @Test
    @DisplayName("17. Order total is recalculated correctly")
    void testOrderTotalRecalculated() {
        order.setTaxAmount(new BigDecimal("20.00"));
        order.setDiscountAmount(new BigDecimal("10.00"));

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));

        OrderItem item1 = new OrderItem();
        item1.setMenuItem(menuItem1);
        item1.setQuantity(4);
        item1.setUnitPrice(new BigDecimal("50.00"));
        item1.setSubtotal(new BigDecimal("200.00"));

        when(orderItemRepository.findByOrderId(100L)).thenReturn(List.of(item1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        orderService.addItemsToOrder(100L, request);

        // 4 existing + 1 added = 5 Cokes @ 50.00 = subtotal 250.00
        // subtotal 250 + tax 20 - discount 10 = 260
        assertEquals(new BigDecimal("260.00"), order.getTotalAmount());
    }

    @Test
    @DisplayName("18. Existing generated bill prevents add-items")
    void testExistingGeneratedBillPreventsAddItems() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(true);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        Exception ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(100L, request));
        assertTrue(ex.getMessage().contains("bill has already been generated"));
    }

    @Test
    @DisplayName("19. ORDER_UPDATED event is published after successful update")
    void testOrderUpdatedEventPublished() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));
        when(orderItemRepository.findByOrderId(100L)).thenReturn(new ArrayList<>());
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        orderService.addItemsToOrder(100L, request);

        verify(eventPublisher, times(1)).publishOrderUpdated(order);
    }

    @Test
    @DisplayName("20. Failed transaction does not broadcast a successful order update")
    void testFailedTransactionDoesNotBroadcastEvent() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(100L)).thenReturn(false);
        when(menuItemRepository.findById(1001L)).thenReturn(Optional.of(menuItem1));

        // Simulate save failure
        doThrow(new RuntimeException("Database error")).when(orderItemRepository).saveAll(any());

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(new OrderItemRequest(1001L, 1, null)));

        assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(100L, request));
        verify(eventPublisher, never()).publishOrderUpdated(any());
    }
}
