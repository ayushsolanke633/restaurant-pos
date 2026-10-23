package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderCancellationWorkflowRegressionTest {

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
    private User waiter1;
    private User waiter2;
    private RestaurantTable table1;
    private RestaurantSettings kdsEnabledSettings;
    private RestaurantSettings kdsDisabledSettings;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("The Grand Palace");

        otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        otherRestaurant.setName("Different Restaurant");

        adminUser = new User();
        adminUser.setId(10L);
        adminUser.setName("Admin User");
        adminUser.setRestaurant(restaurant);

        waiter1 = new User();
        waiter1.setId(101L);
        waiter1.setName("Waiter One");
        waiter1.setRestaurant(restaurant);

        waiter2 = new User();
        waiter2.setId(102L);
        waiter2.setName("Waiter Two");
        waiter2.setRestaurant(restaurant);

        table1 = new RestaurantTable();
        table1.setId(15L);
        table1.setTableNumber(5);
        table1.setRestaurant(restaurant);
        table1.setStatus("OCCUPIED");

        kdsEnabledSettings = new RestaurantSettings();
        kdsEnabledSettings.setId(1L);
        kdsEnabledSettings.setRestaurant(restaurant);
        kdsEnabledSettings.setKitchenEnabled(true);

        kdsDisabledSettings = new RestaurantSettings();
        kdsDisabledSettings.setId(2L);
        kdsDisabledSettings.setRestaurant(restaurant);
        kdsDisabledSettings.setKitchenEnabled(false);
    }

    private Order buildOrder(Long orderId, String status, User waiter, RestaurantTable table, String orderType) {
        Order order = new Order();
        order.setId(orderId);
        order.setRestaurant(restaurant);
        order.setWaiter(waiter);
        order.setTable(table);
        order.setOrderType(orderType);
        order.setStatus(status);
        order.setSubtotal(new BigDecimal("100.00"));
        order.setTaxAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("100.00"));
        return order;
    }

    @Nested
    @DisplayName("1. KDS Enabled Cancellation Rules")
    class KdsEnabledRules {

        @Test
        @DisplayName("Waiter cancels their own NEW order successfully")
        void testWaiterCancelsOwnNewOrderSuccess() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsEnabledSettings));

            Order order = buildOrder(201L, "NEW", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(201L, 1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.getReferenceById(101L)).thenReturn(waiter1);
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(15L), eq("DINE_IN"), anyList()))
                    .thenReturn(false);

            OrderResponse response = orderService.cancelOrder(201L);

            assertNotNull(response);
            assertEquals("CANCELLED", response.getStatus());
            assertEquals("AVAILABLE", table1.getStatus());
            verify(tableRepository).save(table1);
            verify(orderStatusHistoryRepository).save(any());
            verify(eventPublisher).publishOrderStatusChanged(order);
            verify(notificationService).notifyOrderCancelled(order);
        }

        @Test
        @DisplayName("Waiter cannot cancel ACCEPTED order when KDS enabled")
        void testWaiterCannotCancelAcceptedOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsEnabledSettings));

            Order order = buildOrder(202L, "ACCEPTED", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(202L, 1L)).thenReturn(Optional.of(order));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.cancelOrder(202L));
            assertTrue(ex.getMessage().contains("Invalid cancellation status"));
            assertEquals("ACCEPTED", order.getStatus());
            verify(tableRepository, never()).save(any());
            verify(orderStatusHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Waiter cannot cancel PREPARING or READY order when KDS enabled")
        void testWaiterCannotCancelPreparingOrReadyOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsEnabledSettings));

            Order preparingOrder = buildOrder(203L, "PREPARING", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(203L, 1L)).thenReturn(Optional.of(preparingOrder));

            assertThrows(RuntimeException.class, () -> orderService.cancelOrder(203L));

            Order readyOrder = buildOrder(204L, "READY", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(204L, 1L)).thenReturn(Optional.of(readyOrder));

            assertThrows(RuntimeException.class, () -> orderService.cancelOrder(204L));
        }

        @Test
        @DisplayName("Waiter cannot cancel NEW order if a bill already exists (even if KDS enabled)")
        void testWaiterCannotCancelOrderWithBillEvenIfKdsEnabled() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);

            Order order = buildOrder(205L, "NEW", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(205L, 1L)).thenReturn(Optional.of(order));
            when(billRepository.existsByRestaurantIdAndOrderId(1L, 205L)).thenReturn(true);

            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> orderService.cancelOrder(205L));
            assertTrue(ex.getMessage().contains("bill has already been generated"));
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("2. KDS Disabled Cancellation Rules")
    class KdsDisabledRules {

        @Test
        @DisplayName("Waiter can cancel an unbilled order (NEW status)")
        void testWaiterCanCancelUnbilledNewOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsDisabledSettings));

            Order order = buildOrder(301L, "NEW", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(301L, 1L)).thenReturn(Optional.of(order));
            when(billRepository.existsByRestaurantIdAndOrderId(1L, 301L)).thenReturn(false);
            when(billRepository.existsByOrderId(301L)).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.getReferenceById(101L)).thenReturn(waiter1);
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(15L), eq("DINE_IN"), anyList()))
                    .thenReturn(false);

            OrderResponse response = orderService.cancelOrder(301L);

            assertNotNull(response);
            assertEquals("CANCELLED", response.getStatus());
            assertEquals("AVAILABLE", table1.getStatus());
            verify(tableRepository).save(table1);
            verify(orderStatusHistoryRepository).save(any());
        }

        @Test
        @DisplayName("Waiter can cancel an unbilled order in another active status (ACCEPTED)")
        void testWaiterCanCancelUnbilledAcceptedOrderInKitchenFreeMode() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsDisabledSettings));

            Order order = buildOrder(302L, "ACCEPTED", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(302L, 1L)).thenReturn(Optional.of(order));
            when(billRepository.existsByRestaurantIdAndOrderId(1L, 302L)).thenReturn(false);
            when(billRepository.existsByOrderId(302L)).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.getReferenceById(101L)).thenReturn(waiter1);
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(15L), eq("DINE_IN"), anyList()))
                    .thenReturn(false);

            OrderResponse response = orderService.cancelOrder(302L);

            assertNotNull(response);
            assertEquals("CANCELLED", response.getStatus());
            assertEquals("AVAILABLE", table1.getStatus());
            verify(tableRepository).save(table1);
        }

        @Test
        @DisplayName("Waiter cannot cancel an order with a saved bill (even if status is NEW)")
        void testWaiterCannotCancelBilledOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsDisabledSettings));

            Order order = buildOrder(303L, "NEW", waiter1, table1, "DINE_IN");
            table1.setStatus("BILLING");
            when(orderRepository.findByIdAndRestaurantId(303L, 1L)).thenReturn(Optional.of(order));
            when(billRepository.existsByRestaurantIdAndOrderId(1L, 303L)).thenReturn(true);

            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> orderService.cancelOrder(303L));

            assertTrue(ex.getMessage().contains("bill has already been generated"));
            assertEquals("NEW", order.getStatus());
            assertEquals("BILLING", table1.getStatus());
            verify(tableRepository, never()).save(any());
            verify(orderStatusHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("3. Security, Ownership & Tenant Isolation")
    class SecurityAndTenantIsolationRules {

        @Test
        @DisplayName("Waiter cannot cancel another waiter's order")
        void testWaiterCannotCancelOtherWaitersOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L); // Waiter 1

            Order order = buildOrder(401L, "NEW", waiter2, table1, "DINE_IN"); // Belonging to Waiter 2
            when(orderRepository.findByIdAndRestaurantId(401L, 1L)).thenReturn(Optional.of(order));

            AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                    () -> orderService.cancelOrder(401L));

            assertTrue(ex.getMessage().contains("You can only cancel your own orders"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Waiter cannot cancel an order belonging to another restaurant")
        void testWaiterCannotCancelOtherRestaurantOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

            Order otherTenantOrder = new Order();
            otherTenantOrder.setId(402L);
            otherTenantOrder.setRestaurant(otherRestaurant);
            otherTenantOrder.setStatus("NEW");

            when(orderRepository.findByIdAndRestaurantId(402L, 1L)).thenReturn(Optional.empty());
            when(orderRepository.findById(402L)).thenReturn(Optional.of(otherTenantOrder));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.cancelOrder(402L));
            assertTrue(ex.getMessage().contains("Order not found with id"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Kitchen role cannot cancel orders")
        void testKitchenRoleCannotCancelOrders() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");

            assertThrows(AccessDeniedException.class, () -> orderService.cancelOrder(403L));
            verifyNoInteractions(orderRepository);
        }
    }

    @Nested
    @DisplayName("4. ADMIN Cancellation Permissions")
    class AdminPermissionsRules {

        @Test
        @DisplayName("Admin cancels NEW order successfully")
        void testAdminCancelsNewOrderSuccess() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
            when(securityUtils.getCurrentUserId()).thenReturn(10L);

            Order order = buildOrder(501L, "NEW", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(501L, 1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.getReferenceById(10L)).thenReturn(adminUser);
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(15L), eq("DINE_IN"), anyList()))
                    .thenReturn(false);

            OrderResponse response = orderService.cancelOrder(501L);

            assertNotNull(response);
            assertEquals("CANCELLED", response.getStatus());
            assertEquals("AVAILABLE", table1.getStatus());
            verify(tableRepository).save(table1);
        }

        @Test
        @DisplayName("Admin cancels ACCEPTED order successfully and releases table")
        void testAdminCancelsAcceptedOrderSuccess() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
            when(securityUtils.getCurrentUserId()).thenReturn(10L);

            Order order = buildOrder(502L, "ACCEPTED", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(502L, 1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.getReferenceById(10L)).thenReturn(adminUser);
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(15L), eq("DINE_IN"), anyList()))
                    .thenReturn(false);

            OrderResponse response = orderService.cancelOrder(502L);

            assertNotNull(response);
            assertEquals("CANCELLED", response.getStatus());
            assertEquals("AVAILABLE", table1.getStatus());
            verify(tableRepository).save(table1);
        }

        @Test
        @DisplayName("Admin cannot cancel PREPARING or READY orders (documented policy preserved)")
        void testAdminCannotCancelPreparingOrReadyOrders() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

            Order preparingOrder = buildOrder(503L, "PREPARING", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(503L, 1L)).thenReturn(Optional.of(preparingOrder));

            assertThrows(RuntimeException.class, () -> orderService.cancelOrder(503L));

            Order readyOrder = buildOrder(504L, "READY", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(504L, 1L)).thenReturn(Optional.of(readyOrder));

            assertThrows(RuntimeException.class, () -> orderService.cancelOrder(504L));
        }

        @Test
        @DisplayName("Admin cannot cancel order when bill already exists")
        void testAdminCannotCancelOrderWithExistingBill() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

            Order order = buildOrder(505L, "NEW", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(505L, 1L)).thenReturn(Optional.of(order));
            when(billRepository.existsByRestaurantIdAndOrderId(1L, 505L)).thenReturn(true);

            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> orderService.cancelOrder(505L));
            assertTrue(ex.getMessage().contains("bill has already been generated"));
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("5. Table Availability & Multi-Order Conflict Prevention")
    class TableAvailabilityRules {

        @Test
        @DisplayName("Cancelling order releases table ONLY when no other active order uses it")
        void testTableNotReleasedWhenOtherActiveOrderRemains() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsEnabledSettings));

            Order orderA = buildOrder(601L, "NEW", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(601L, 1L)).thenReturn(Optional.of(orderA));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.getReferenceById(101L)).thenReturn(waiter1);

            // Another order still exists on table 1!
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(eq(1L), eq(15L), eq("DINE_IN"), anyList()))
                    .thenReturn(true);

            OrderResponse response = orderService.cancelOrder(601L);

            assertNotNull(response);
            assertEquals("CANCELLED", response.getStatus());
            // Table must REMAIN OCCUPIED because another active order is on it!
            assertEquals("OCCUPIED", table1.getStatus());
            verify(tableRepository, never()).save(table1);
        }

        @Test
        @DisplayName("Takeaway orders cancel without attempting table release")
        void testTakeawayOrderCancellation() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
            when(securityUtils.getCurrentUserId()).thenReturn(101L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kdsEnabledSettings));

            Order takeawayOrder = buildOrder(602L, "NEW", waiter1, null, "TAKEAWAY");
            when(orderRepository.findByIdAndRestaurantId(602L, 1L)).thenReturn(Optional.of(takeawayOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.getReferenceById(101L)).thenReturn(waiter1);

            OrderResponse response = orderService.cancelOrder(602L);

            assertNotNull(response);
            assertEquals("CANCELLED", response.getStatus());
            verifyNoInteractions(tableRepository);
        }
    }

    @Nested
    @DisplayName("6. Duplicate & Concurrent Cancellation Prevention")
    class ConcurrencyAndIdempotencyRules {

        @Test
        @DisplayName("Cancelling an already CANCELLED order is rejected safely with conflict")
        void testDuplicateCancellationRejected() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            Order cancelledOrder = buildOrder(701L, "CANCELLED", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(701L, 1L)).thenReturn(Optional.of(cancelledOrder));

            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> orderService.cancelOrder(701L));

            assertTrue(ex.getMessage().contains("Order is already cancelled"));
            verify(orderRepository, never()).save(any());
            verify(tableRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cancelling a COMPLETED order is rejected safely")
        void testCompletedOrderCancellationRejected() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            Order completedOrder = buildOrder(702L, "COMPLETED", waiter1, table1, "DINE_IN");
            when(orderRepository.findByIdAndRestaurantId(702L, 1L)).thenReturn(Optional.of(completedOrder));

            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> orderService.cancelOrder(702L));

            assertTrue(ex.getMessage().contains("Completed order cannot be cancelled"));
            verify(orderRepository, never()).save(any());
        }
    }
}
