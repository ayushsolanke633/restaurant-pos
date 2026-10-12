package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
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
public class Change9WaiterOrderHistoryTest {

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

    private Restaurant restaurant;
    private RestaurantTable table1;
    private User waiter1;
    private User waiter2;
    private MenuItem item1;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        table1 = new RestaurantTable();
        table1.setId(10L);
        table1.setRestaurant(restaurant);
        table1.setTableNumber(5);
        table1.setCapacity(4);
        table1.setStatus("AVAILABLE");

        Role waiterRole = new Role(3L, "WAITER");

        waiter1 = new User();
        waiter1.setId(101L);
        waiter1.setName("Waiter One");
        waiter1.setRole(waiterRole);
        waiter1.setRestaurant(restaurant);

        waiter2 = new User();
        waiter2.setId(102L);
        waiter2.setName("Waiter Two");
        waiter2.setRole(waiterRole);
        waiter2.setRestaurant(restaurant);

        item1 = new MenuItem();
        item1.setId(50L);
        item1.setRestaurant(restaurant);
        item1.setName("Burger");
        item1.setPrice(new BigDecimal("200.00")); // Current price is 200.00
        item1.setIsAvailable(true);
    }

    @Test
    @DisplayName("1. Waiter can get own COMPLETED DINE_IN orders")
    void testWaiterCanGetOwnCompletedOrders() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        Order completedOrder = new Order();
        completedOrder.setId(1001L);
        completedOrder.setRestaurant(restaurant);
        completedOrder.setTable(table1);
        completedOrder.setWaiter(waiter1);
        completedOrder.setOrderType("DINE_IN");
        completedOrder.setStatus("COMPLETED");
        completedOrder.setSubtotal(new BigDecimal("150.00"));
        completedOrder.setTaxAmount(BigDecimal.ZERO);
        completedOrder.setDiscountAmount(BigDecimal.ZERO);
        completedOrder.setTotalAmount(new BigDecimal("150.00"));
        completedOrder.setCreatedAt(LocalDateTime.now().minusHours(2));

        OrderItem orderItem = new OrderItem();
        orderItem.setId(1L);
        orderItem.setOrder(completedOrder);
        orderItem.setMenuItem(item1);
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(new BigDecimal("150.00")); // Stored historical unit price
        orderItem.setSubtotal(new BigDecimal("150.00"));

        when(orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
                1L, 101L, "DINE_IN", "COMPLETED"
        )).thenReturn(List.of(completedOrder));
        when(orderItemRepository.findByOrderId(1001L)).thenReturn(List.of(orderItem));

        List<OrderResponse> history = orderService.getWaiterOrderHistory("COMPLETED");

        assertNotNull(history);
        assertEquals(1, history.size());
        OrderResponse res = history.get(0);
        assertEquals(1001L, res.getId());
        assertEquals("COMPLETED", res.getStatus());
        assertEquals("DINE_IN", res.getOrderType());
        assertEquals(101L, res.getWaiterId());
        assertEquals(5, res.getTableNumber());
        assertEquals(1, res.getItems().size());
        assertEquals(new BigDecimal("150.00"), res.getItems().get(0).getUnitPrice());
    }

    @Test
    @DisplayName("2. Waiter can get own CANCELLED DINE_IN orders")
    void testWaiterCanGetOwnCancelledOrders() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        Order cancelledOrder = new Order();
        cancelledOrder.setId(1002L);
        cancelledOrder.setRestaurant(restaurant);
        cancelledOrder.setTable(table1);
        cancelledOrder.setWaiter(waiter1);
        cancelledOrder.setOrderType("DINE_IN");
        cancelledOrder.setStatus("CANCELLED");
        cancelledOrder.setSubtotal(new BigDecimal("150.00"));
        cancelledOrder.setTaxAmount(BigDecimal.ZERO);
        cancelledOrder.setDiscountAmount(BigDecimal.ZERO);
        cancelledOrder.setTotalAmount(new BigDecimal("150.00"));
        cancelledOrder.setCreatedAt(LocalDateTime.now().minusHours(1));

        when(orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
                1L, 101L, "DINE_IN", "CANCELLED"
        )).thenReturn(List.of(cancelledOrder));
        when(orderItemRepository.findByOrderId(1002L)).thenReturn(Collections.emptyList());

        List<OrderResponse> history = orderService.getWaiterOrderHistory("CANCELLED");

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("CANCELLED", history.get(0).getStatus());
    }

    @Test
    @DisplayName("3. Waiter history combines COMPLETED and CANCELLED by default when no status filter passed")
    void testWaiterHistoryReturnsCompletedAndCancelledByDefault() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        Order o1 = new Order();
        o1.setId(2001L);
        o1.setRestaurant(restaurant);
        o1.setTable(table1);
        o1.setWaiter(waiter1);
        o1.setOrderType("DINE_IN");
        o1.setStatus("COMPLETED");

        Order o2 = new Order();
        o2.setId(2002L);
        o2.setRestaurant(restaurant);
        o2.setTable(table1);
        o2.setWaiter(waiter1);
        o2.setOrderType("DINE_IN");
        o2.setStatus("CANCELLED");

        when(orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(101L), eq("DINE_IN"), eq(Arrays.asList("COMPLETED", "CANCELLED"))
        )).thenReturn(List.of(o1, o2));

        List<OrderResponse> history = orderService.getWaiterOrderHistory(null);

        assertEquals(2, history.size());
        assertEquals(2001L, history.get(0).getId());
        assertEquals(2002L, history.get(1).getId());
    }

    @Test
    @DisplayName("4. Waiter cannot get another waiter's orders")
    void testWaiterCannotGetAnotherWaitersOrders() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L); // Authenticated as Waiter 1 (101L)

        when(orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(101L), eq("DINE_IN"), any()
        )).thenReturn(Collections.emptyList());

        List<OrderResponse> history = orderService.getWaiterOrderHistory(null);

        assertTrue(history.isEmpty());
        // Verify repository was called ONLY for waiter 101L and NEVER for waiter 102L
        verify(orderRepository).findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(101L), eq("DINE_IN"), any()
        );
        verify(orderRepository, never()).findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(102L), eq("DINE_IN"), any()
        );
    }

    @Test
    @DisplayName("5. Waiter getOrderById for another waiter's order is rejected")
    void testWaiterGetOrderByIdForAnotherWaiterIsRejected() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        Order orderOfWaiter2 = new Order();
        orderOfWaiter2.setId(3001L);
        orderOfWaiter2.setRestaurant(restaurant);
        orderOfWaiter2.setTable(table1);
        orderOfWaiter2.setWaiter(waiter2); // Owned by Waiter 2
        orderOfWaiter2.setOrderType("DINE_IN");
        orderOfWaiter2.setStatus("COMPLETED");

        when(orderRepository.findById(3001L)).thenReturn(Optional.of(orderOfWaiter2));

        assertThrows(AccessDeniedException.class, () -> orderService.getOrderById(3001L));
    }

    @Test
    @DisplayName("6. Waiter cannot get TAKEAWAY history")
    void testWaiterCannotGetTakeawayHistory() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        orderService.getWaiterOrderHistory(null);

        // Verify query ALWAYS uses orderType = DINE_IN
        verify(orderRepository).findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(101L), eq("DINE_IN"), any()
        );
    }

    @Test
    @DisplayName("7. Kitchen role is forbidden from waiter history")
    void testKitchenIsForbiddenFromWaiterHistory() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");

        assertThrows(AccessDeniedException.class, () -> orderService.getWaiterOrderHistory(null));
    }

    @Test
    @DisplayName("8. Historical item prices come from OrderItem stored unitPrice, not MenuItem current price")
    void testHistoricalPricesComeFromOrderItem() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        Order order = new Order();
        order.setId(4001L);
        order.setRestaurant(restaurant);
        order.setTable(table1);
        order.setWaiter(waiter1);
        order.setOrderType("DINE_IN");
        order.setStatus("COMPLETED");

        // item1 current price in DB is 200.00
        assertEquals(new BigDecimal("200.00"), item1.getPrice());

        // OrderItem has historical price of 120.00
        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setOrder(order);
        orderItem.setMenuItem(item1);
        orderItem.setQuantity(2);
        orderItem.setUnitPrice(new BigDecimal("120.00")); // Old price!
        orderItem.setSubtotal(new BigDecimal("240.00"));

        when(orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(101L), eq("DINE_IN"), any()
        )).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderId(4001L)).thenReturn(List.of(orderItem));

        List<OrderResponse> history = orderService.getWaiterOrderHistory(null);

        assertEquals(1, history.size());
        OrderResponse res = history.get(0);
        assertEquals(1, res.getItems().size());
        assertEquals(new BigDecimal("120.00"), res.getItems().get(0).getUnitPrice());
        assertNotEquals(item1.getPrice(), res.getItems().get(0).getUnitPrice());
    }

    @Test
    @DisplayName("9. Order history results are sorted newest first")
    void testOrderHistoryIsSortedNewestFirst() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(101L);

        Order newerOrder = new Order();
        newerOrder.setId(5002L);
        newerOrder.setCreatedAt(LocalDateTime.now());

        Order olderOrder = new Order();
        olderOrder.setId(5001L);
        olderOrder.setCreatedAt(LocalDateTime.now().minusDays(1));

        // Repository returns sorted by createdAt DESC
        when(orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(101L), eq("DINE_IN"), any()
        )).thenReturn(List.of(newerOrder, olderOrder));

        List<OrderResponse> history = orderService.getWaiterOrderHistory(null);

        assertEquals(2, history.size());
        assertEquals(5002L, history.get(0).getId());
        assertEquals(5001L, history.get(1).getId());
    }
}
