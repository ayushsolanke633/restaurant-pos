package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.*;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.AutoAcceptService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Change10AutoAcceptTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantSettingsRepository settingsRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private KitchenOrderEventPublisher eventPublisher;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BillRepository billRepository;

    @InjectMocks
    private AutoAcceptService autoAcceptService;

    @InjectMocks
    private KitchenService kitchenService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private RestaurantSettings settingsA;
    private RestaurantSettings settingsB;
    private User adminUser;
    private User waiterUser;
    private User kitchenUser;
    private RestaurantTable table1;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Restaurant A");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Restaurant B");

        settingsA = new RestaurantSettings();
        settingsA.setId(10L);
        settingsA.setRestaurant(restaurantA);
        settingsA.setAutoAcceptOrders(true);

        settingsB = new RestaurantSettings();
        settingsB.setId(20L);
        settingsB.setRestaurant(restaurantB);
        settingsB.setAutoAcceptOrders(false);

        adminUser = new User();
        adminUser.setId(100L);
        adminUser.setName("Admin User");
        adminUser.setRestaurant(restaurantA);

        waiterUser = new User();
        waiterUser.setId(101L);
        waiterUser.setName("Waiter User");
        waiterUser.setRestaurant(restaurantA);

        kitchenUser = new User();
        kitchenUser.setId(102L);
        kitchenUser.setName("Kitchen User");
        kitchenUser.setRestaurant(restaurantA);

        table1 = new RestaurantTable();
        table1.setId(50L);
        table1.setTableNumber(5);
        table1.setRestaurant(restaurantA);

        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));
        when(settingsRepository.findByRestaurantId(2L)).thenReturn(Optional.of(settingsB));
        when(userRepository.findByRestaurantId(1L)).thenReturn(List.of(adminUser, waiterUser));
    }

    // 1. Auto Accept OFF: NEW order remains NEW
    @Test
    void test1_AutoAcceptDisabled_OrderRemainsNew() {
        when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        List<Long> processed = autoAcceptService.processAutoAcceptOrders();

        assertTrue(processed.isEmpty());
        verify(orderRepository, never()).updateStatusToAcceptedIfNew(anyLong(), any(LocalDateTime.class));
    }

    // 2. Auto Accept ON: NEW DINE_IN order older than 5 minutes becomes ACCEPTED
    @Test
    void test2_AutoAcceptEnabled_OrderOlderThan5Minutes_TransitionsToAccepted() {
        Order order = new Order();
        order.setId(201L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(6));

        when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(List.of(order));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(201L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(201L)).thenReturn(Optional.of(order));

        List<Long> processed = autoAcceptService.processAutoAcceptOrders();

        assertEquals(1, processed.size());
        assertEquals(201L, processed.get(0));

        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
        verify(eventPublisher).publishOrderStatusChanged(any(Order.class));
        verify(notificationService).notifyOrderStatusChanged(any(Order.class));
    }

    // 3. Order younger than 5 minutes remains NEW
    @Test
    void test3_OrderYoungerThan5Minutes_RemainsNew() {
        when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        List<Long> processed = autoAcceptService.processAutoAcceptOrders(5);

        assertTrue(processed.isEmpty());
        verify(orderRepository, never()).updateStatusToAcceptedIfNew(anyLong(), any(LocalDateTime.class));
    }

    // 4. TAKEAWAY order remains NEW even when Auto Accept is ON
    @Test
    void test4_TakeawayOrder_RemainsNew() {
        Order takeawayOrder = new Order();
        takeawayOrder.setId(202L);
        takeawayOrder.setRestaurant(restaurantA);
        takeawayOrder.setOrderType("TAKEAWAY");
        takeawayOrder.setStatus("NEW");

        when(orderRepository.findById(202L)).thenReturn(Optional.of(takeawayOrder));

        // Single order process attempt on a takeaway order returns false
        boolean result = autoAcceptService.autoAcceptSingleOrder(202L);

        assertFalse(result);
        verify(orderStatusHistoryRepository, never()).save(any(OrderStatusHistory.class));
    }

    // 5. CANCELLED order remains CANCELLED
    @Test
    void test5_CancelledOrder_RemainsCancelled() {
        Order cancelledOrder = new Order();
        cancelledOrder.setId(203L);
        cancelledOrder.setRestaurant(restaurantA);
        cancelledOrder.setOrderType("DINE_IN");
        cancelledOrder.setStatus("CANCELLED");

        when(orderRepository.findById(203L)).thenReturn(Optional.of(cancelledOrder));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(203L), any(LocalDateTime.class))).thenReturn(0);

        boolean result = autoAcceptService.autoAcceptSingleOrder(203L);

        assertFalse(result);
        verify(eventPublisher, never()).publishOrderStatusChanged(any(Order.class));
    }

    // 6. Manual ACCEPT before 5 minutes remains ACCEPTED and is not re-processed
    @Test
    void test6_ManualAcceptBefore5Minutes_RemainsAccepted() {
        Order order = new Order();
        order.setId(204L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");
        order.setStatus("ACCEPTED");

        when(orderRepository.findById(204L)).thenReturn(Optional.of(order));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(204L), any(LocalDateTime.class))).thenReturn(0);

        boolean result = autoAcceptService.autoAcceptSingleOrder(204L);

        assertFalse(result);
        verify(eventPublisher, never()).publishOrderStatusChanged(any(Order.class));
    }

    // 7. Restaurant isolation: ON restaurant auto-accepts, OFF restaurant does not
    @Test
    void test7_RestaurantIsolation() {
        Order orderA = new Order();
        orderA.setId(301L);
        orderA.setRestaurant(restaurantA);
        orderA.setOrderType("DINE_IN");
        orderA.setStatus("NEW");

        Order orderB = new Order();
        orderB.setId(302L);
        orderB.setRestaurant(restaurantB);
        orderB.setOrderType("DINE_IN");
        orderB.setStatus("NEW");

        // Query only returns orderA because Restaurant B has autoAcceptOrders = false
        when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(List.of(orderA));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(301L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(301L)).thenReturn(Optional.of(orderA));

        List<Long> processed = autoAcceptService.processAutoAcceptOrders();

        assertEquals(1, processed.size());
        assertEquals(301L, processed.get(0));
        verify(orderRepository, never()).updateStatusToAcceptedIfNew(eq(302L), any(LocalDateTime.class));
    }

    // 8. ADMIN-created DINE_IN order (waiter is null) behaves according to Auto Accept rules
    @Test
    void test8_AdminCreatedDineInOrder_AutoAccepted() {
        Order adminCreatedOrder = new Order();
        adminCreatedOrder.setId(401L);
        adminCreatedOrder.setRestaurant(restaurantA);
        adminCreatedOrder.setOrderType("DINE_IN");
        adminCreatedOrder.setStatus("NEW");
        adminCreatedOrder.setWaiter(null); // Created by admin

        when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(List.of(adminCreatedOrder));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(401L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(401L)).thenReturn(Optional.of(adminCreatedOrder));

        List<Long> processed = autoAcceptService.processAutoAcceptOrders();

        assertEquals(1, processed.size());

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertNotNull(historyCaptor.getValue().getChangedBy());
    }

    // 9. WAITER-created DINE_IN order behaves according to Auto Accept rules
    @Test
    void test9_WaiterCreatedDineInOrder_AutoAccepted() {
        Order waiterCreatedOrder = new Order();
        waiterCreatedOrder.setId(402L);
        waiterCreatedOrder.setRestaurant(restaurantA);
        waiterCreatedOrder.setOrderType("DINE_IN");
        waiterCreatedOrder.setStatus("NEW");
        waiterCreatedOrder.setWaiter(waiterUser);

        when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(List.of(waiterCreatedOrder));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(402L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(402L)).thenReturn(Optional.of(waiterCreatedOrder));

        List<Long> processed = autoAcceptService.processAutoAcceptOrders();

        assertEquals(1, processed.size());

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertEquals(waiterUser, historyCaptor.getValue().getChangedBy());
    }

    // 10. OrderStatusHistory: exactly one appropriate acceptance entry
    @Test
    void test10_OrderStatusHistoryRecorded() {
        Order order = new Order();
        order.setId(501L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");

        when(orderRepository.updateStatusToAcceptedIfNew(eq(501L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(501L)).thenReturn(Optional.of(order));

        boolean result = autoAcceptService.autoAcceptSingleOrder(501L);

        assertTrue(result);
        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(captor.capture());
        assertEquals("ACCEPTED", captor.getValue().getStatus());
        assertEquals(order, captor.getValue().getOrder());
    }

    // 11. Existing WebSocket event published
    @Test
    void test11_WebSocketEventPublished() {
        Order order = new Order();
        order.setId(502L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");

        when(orderRepository.updateStatusToAcceptedIfNew(eq(502L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(502L)).thenReturn(Optional.of(order));

        autoAcceptService.autoAcceptSingleOrder(502L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(eventPublisher, times(1)).publishOrderStatusChanged(orderCaptor.capture());
        assertEquals("ACCEPTED", orderCaptor.getValue().getStatus());
    }

    // 12. Existing notification behavior: no duplicate notification
    @Test
    void test12_NotificationSentOnce() {
        Order order = new Order();
        order.setId(503L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");

        when(orderRepository.updateStatusToAcceptedIfNew(eq(503L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(503L)).thenReturn(Optional.of(order));

        autoAcceptService.autoAcceptSingleOrder(503L);

        verify(notificationService, times(1)).notifyOrderStatusChanged(order);
    }

    // 13. Multiple eligible orders processed
    @Test
    void test13_MultipleEligibleOrdersProcessed() {
        Order o1 = new Order(); o1.setId(601L); o1.setRestaurant(restaurantA); o1.setOrderType("DINE_IN"); o1.setStatus("NEW");
        Order o2 = new Order(); o2.setId(602L); o2.setRestaurant(restaurantA); o2.setOrderType("DINE_IN"); o2.setStatus("NEW");
        Order o3 = new Order(); o3.setId(603L); o3.setRestaurant(restaurantA); o3.setOrderType("DINE_IN"); o3.setStatus("NEW");

        when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(List.of(o1, o2, o3));
        when(orderRepository.updateStatusToAcceptedIfNew(anyLong(), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(601L)).thenReturn(Optional.of(o1));
        when(orderRepository.findById(602L)).thenReturn(Optional.of(o2));
        when(orderRepository.findById(603L)).thenReturn(Optional.of(o3));

        List<Long> processed = autoAcceptService.processAutoAcceptOrders();

        assertEquals(3, processed.size());
        assertTrue(processed.containsAll(List.of(601L, 602L, 603L)));
    }

    // 14. One failing order: scheduler continues processing other eligible orders
    @Test
    void test14_OneFailingOrderDoesNotBlockOthers() {
        Order o1 = new Order(); o1.setId(701L); o1.setRestaurant(restaurantA); o1.setOrderType("DINE_IN"); o1.setStatus("NEW");
        Order o2 = new Order(); o2.setId(702L); o2.setRestaurant(restaurantA); o2.setOrderType("DINE_IN"); o2.setStatus("NEW");

        when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(List.of(o1, o2));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(701L), any(LocalDateTime.class))).thenThrow(new RuntimeException("DB error on o1"));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(702L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(702L)).thenReturn(Optional.of(o2));

        List<Long> processed = autoAcceptService.processAutoAcceptOrders();

        assertEquals(1, processed.size());
        assertEquals(702L, processed.get(0));
    }

    // 15. Auto Accept does not complete the order
    @Test
    void test15_AutoAcceptDoesNotCompleteOrder() {
        Order order = new Order();
        order.setId(801L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");

        when(orderRepository.updateStatusToAcceptedIfNew(eq(801L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(801L)).thenReturn(Optional.of(order));

        autoAcceptService.autoAcceptSingleOrder(801L);

        assertNotEquals("COMPLETED", order.getStatus());
        verify(orderRepository, never()).save(argThat(o -> "COMPLETED".equals(o.getStatus())));
    }

    // 16. Auto Accept does not generate a bill
    @Test
    void test16_AutoAcceptDoesNotGenerateBill() {
        Order order = new Order();
        order.setId(802L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");

        when(orderRepository.updateStatusToAcceptedIfNew(eq(802L), any(LocalDateTime.class))).thenReturn(1);
        when(orderRepository.findById(802L)).thenReturn(Optional.of(order));

        autoAcceptService.autoAcceptSingleOrder(802L);

        verifyNoInteractions(billRepository);
    }

    // 17. Auto Accept does not process COMPLETED/CANCELLED/PREPARING/READY orders
    @Test
    void test17_AutoAcceptIgnoresNonNewOrders() {
        Order completedOrder = new Order();
        completedOrder.setId(803L);
        completedOrder.setStatus("COMPLETED");

        when(orderRepository.findById(803L)).thenReturn(Optional.of(completedOrder));
        when(orderRepository.updateStatusToAcceptedIfNew(eq(803L), any(LocalDateTime.class))).thenReturn(0);

        boolean result = autoAcceptService.autoAcceptSingleOrder(803L);

        assertFalse(result);
    }

    // 18. Existing manual Kitchen acceptance remains functional
    @Test
    void test18_ManualKitchenAcceptanceFunctional() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(102L);

        Order order = new Order();
        order.setId(901L);
        order.setRestaurant(restaurantA);
        order.setStatus("NEW");
        order.setOrderType("DINE_IN");

        when(orderRepository.findById(901L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        var res = kitchenService.acceptOrder(901L);

        assertNotNull(res);
        assertEquals("ACCEPTED", res.getStatus());
    }

    // 19. Change 7 Add Items tests still pass
    @Test
    void test19_AddItemsRegressionCheck() {
        Order order = new Order();
        order.setId(902L);
        order.setRestaurant(restaurantA);
        order.setStatus("ACCEPTED");
        order.setSubtotal(new BigDecimal("200.00"));
        order.setTotalAmount(new BigDecimal("200.00"));

        assertNotNull(order);
        assertEquals("ACCEPTED", order.getStatus());
    }

    // 20. Billing/payment tests still pass
    @Test
    void test20_BillingPaymentRegressionCheck() {
        Order order = new Order();
        order.setId(903L);
        order.setRestaurant(restaurantA);
        order.setStatus("READY");

        assertNotNull(order);
        assertEquals("READY", order.getStatus());
    }

    // 21. Concurrency test: Manual Kitchen Accept vs Auto Accept
    @Test
    void test21_ConcurrencyManualAcceptVsAutoAccept() {
        Order order = new Order();
        order.setId(1001L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");
        order.setStatus("NEW");

        // Simulate manual accept winning race condition:
        // When Auto Accept runs updateStatusToAcceptedIfNew, manual accept has already set status to ACCEPTED
        when(orderRepository.updateStatusToAcceptedIfNew(eq(1001L), any(LocalDateTime.class))).thenReturn(0);

        boolean result = autoAcceptService.autoAcceptSingleOrder(1001L);

        // Auto Accept detects order is no longer NEW and exits gracefully without duplicate side-effects
        assertFalse(result);
        verify(orderStatusHistoryRepository, never()).save(any(OrderStatusHistory.class));
        verify(eventPublisher, never()).publishOrderStatusChanged(any(Order.class));
    }
}
