package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.BillRequest;
import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.dto.PaymentRequest;
import com.restaurant.restaurantpos.dto.PaymentResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Payment;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.PaymentRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.BillService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderAndBillingPermissionsRegressionTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private BillRepository billRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RestaurantSettingsRepository settingsRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private KitchenOrderEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;

    @InjectMocks private OrderService orderService;
    @InjectMocks private BillService billService;

    private Restaurant restaurant;
    private Restaurant otherRestaurant;
    private User adminUser;
    private User waiterUser;
    private User otherWaiterUser;
    private RestaurantTable table1;
    private MenuItem menuItem;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Main Restaurant");

        otherRestaurant = new Restaurant();
        otherRestaurant.setId(2L);
        otherRestaurant.setName("Other Restaurant");

        adminUser = new User();
        adminUser.setId(10L);
        adminUser.setName("Admin");
        adminUser.setRestaurant(restaurant);

        waiterUser = new User();
        waiterUser.setId(20L);
        waiterUser.setName("Waiter One");
        waiterUser.setRestaurant(restaurant);

        otherWaiterUser = new User();
        otherWaiterUser.setId(21L);
        otherWaiterUser.setName("Waiter Two");
        otherWaiterUser.setRestaurant(restaurant);

        table1 = new RestaurantTable();
        table1.setId(100L);
        table1.setRestaurant(restaurant);
        table1.setTableNumber(5);
        table1.setStatus("AVAILABLE");

        menuItem = new MenuItem();
        menuItem.setId(50L);
        menuItem.setRestaurant(restaurant);
        menuItem.setName("Burger");
        menuItem.setPrice(new BigDecimal("150.00"));
        menuItem.setIsAvailable(true);
    }

    // =========================================================================
    // 1. Order Creation Permissions: Admin vs Waiter
    // =========================================================================
    @Nested
    @DisplayName("1. Order Creation Permissions: Admin vs Waiter")
    class OrderCreationPermissionTests {

        @Test
        @DisplayName("Admin can create both DINE_IN and TAKEAWAY orders")
        void testAdminCanCreateDineInAndTakeawayOrders() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(10L);
            when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

            when(tableRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(table1));
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(anyLong(), anyLong(), any(), any())).thenReturn(false);
            when(menuItemRepository.findByIdAndRestaurantId(50L, 1L)).thenReturn(Optional.of(menuItem));
            when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
            when(orderItemRepository.saveAll(any())).thenReturn(Collections.emptyList());

            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(101L);
                return o;
            });

            // 1. Admin creates DINE_IN order
            OrderRequest dineInRequest = new OrderRequest();
            dineInRequest.setOrderType("DINE_IN");
            dineInRequest.setTableId(100L);
            dineInRequest.setItems(List.of(new OrderItemRequest(50L, 2, "Extra cheese")));

            OrderResponse dineInResponse = orderService.createOrder(dineInRequest);
            assertNotNull(dineInResponse);
            assertEquals("DINE_IN", dineInResponse.getOrderType());
            assertEquals("OCCUPIED", table1.getStatus());

            // 2. Admin creates TAKEAWAY order
            OrderRequest takeawayRequest = new OrderRequest();
            takeawayRequest.setOrderType("TAKEAWAY");
            takeawayRequest.setItems(List.of(new OrderItemRequest(50L, 1, null)));

            OrderResponse takeawayResponse = orderService.createOrder(takeawayRequest);
            assertNotNull(takeawayResponse);
            assertEquals("TAKEAWAY", takeawayResponse.getOrderType());
        }

        @Test
        @DisplayName("Waiter can create DINE_IN orders")
        void testWaiterCanCreateDineInOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(20L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            when(tableRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(table1));
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(anyLong(), anyLong(), any(), any())).thenReturn(false);
            when(userRepository.findByIdAndRestaurantId(20L, 1L)).thenReturn(Optional.of(waiterUser));
            when(menuItemRepository.findByIdAndRestaurantId(50L, 1L)).thenReturn(Optional.of(menuItem));
            when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
            when(orderItemRepository.saveAll(any())).thenReturn(Collections.emptyList());

            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(102L);
                return o;
            });

            OrderRequest dineInRequest = new OrderRequest();
            dineInRequest.setOrderType("DINE_IN");
            dineInRequest.setTableId(100L);
            dineInRequest.setItems(List.of(new OrderItemRequest(50L, 1, null)));

            OrderResponse response = orderService.createOrder(dineInRequest);
            assertNotNull(response);
            assertEquals("DINE_IN", response.getOrderType());
            assertEquals("OCCUPIED", table1.getStatus());
        }

        @Test
        @DisplayName("Waiter CANNOT create TAKEAWAY orders (throws HTTP 403 AccessDeniedException)")
        void testWaiterCannotCreateTakeawayOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(20L);
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            OrderRequest takeawayRequest = new OrderRequest();
            takeawayRequest.setOrderType("TAKEAWAY");
            takeawayRequest.setItems(List.of(new OrderItemRequest(50L, 1, null)));

            AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                    () -> orderService.createOrder(takeawayRequest));
            assertTrue(ex.getMessage().contains("Access denied"));
            verify(orderRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 2. Billing Permissions: Admin in Kitchen-Enabled vs Kitchen-Free Mode
    // =========================================================================
    @Nested
    @DisplayName("2. Billing Permissions: Admin in Kitchen-Enabled vs Kitchen-Free Mode")
    class BillingModeTests {

        @Test
        @DisplayName("Kitchen-Enabled Mode: Admin can generate bill for READY order, but blocked for NEW/PREPARING")
        void testAdminKitchenEnabledBilling() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(10L);

            RestaurantSettings settings = new RestaurantSettings();
            settings.setKitchenEnabled(true);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

            Order readyOrder = new Order();
            readyOrder.setId(201L);
            readyOrder.setRestaurant(restaurant);
            readyOrder.setTable(table1);
            readyOrder.setOrderType("DINE_IN");
            readyOrder.setStatus("READY");
            readyOrder.setSubtotal(new BigDecimal("300.00"));
            readyOrder.setTaxAmount(BigDecimal.ZERO);
            readyOrder.setDiscountAmount(BigDecimal.ZERO);
            readyOrder.setTotalAmount(new BigDecimal("300.00"));

            when(orderRepository.findByIdAndRestaurantId(201L, 1L)).thenReturn(Optional.of(readyOrder));
            when(billRepository.existsByOrderId(201L)).thenReturn(false);
            when(userRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(adminUser));
            when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
            when(billRepository.save(any(Bill.class))).thenAnswer(inv -> {
                Bill b = inv.getArgument(0);
                b.setId(501L);
                return b;
            });

            // 1. Ready order generates bill successfully in kitchen-enabled mode
            BillResponse response = billService.createBill(new BillRequest(201L));
            assertNotNull(response);
            assertEquals("BILLING", table1.getStatus());

            // 2. Non-ready order (e.g. NEW) fails in kitchen-enabled mode
            Order newOrder = new Order();
            newOrder.setId(202L);
            newOrder.setRestaurant(restaurant);
            newOrder.setStatus("NEW");

            when(orderRepository.findByIdAndRestaurantId(202L, 1L)).thenReturn(Optional.of(newOrder));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> billService.createBill(new BillRequest(202L)));
            assertTrue(ex.getMessage().contains("is not in READY status"));
        }

        @Test
        @DisplayName("Kitchen-Free Mode: Admin can generate bill without waiting for READY status (order is NEW)")
        void testAdminKitchenFreeBillingWorksWithoutReadyStatus() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(10L);

            // Restaurant has kitchenEnabled = false -> Kitchen-Free mode
            RestaurantSettings kitchenFreeSettings = new RestaurantSettings();
            kitchenFreeSettings.setKitchenEnabled(false);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kitchenFreeSettings));

            Order newOrder = new Order();
            newOrder.setId(301L);
            newOrder.setRestaurant(restaurant);
            newOrder.setTable(table1);
            newOrder.setOrderType("DINE_IN");
            newOrder.setStatus("NEW"); // Notice: NEW status, no kitchen exists to transition it
            newOrder.setSubtotal(new BigDecimal("200.00"));
            newOrder.setTaxAmount(new BigDecimal("10.00"));
            newOrder.setDiscountAmount(BigDecimal.ZERO);
            newOrder.setTotalAmount(new BigDecimal("210.00"));

            when(orderRepository.findByIdAndRestaurantId(301L, 1L)).thenReturn(Optional.of(newOrder));
            when(billRepository.existsByOrderId(301L)).thenReturn(false);
            when(userRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(adminUser));
            when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
            when(billRepository.save(any(Bill.class))).thenAnswer(inv -> {
                Bill b = inv.getArgument(0);
                b.setId(601L);
                return b;
            });

            // Bill generation succeeds without KDS status transitions
            BillResponse response = billService.createBill(new BillRequest(301L));
            assertNotNull(response);
            assertEquals(new BigDecimal("210.00"), response.getTotalAmount());
            assertEquals("BILLING", table1.getStatus());

            // Process payment in kitchen-free mode succeeds and completes the order
            Bill generatedBill = new Bill();
            generatedBill.setId(601L);
            generatedBill.setRestaurant(restaurant);
            generatedBill.setOrder(newOrder);
            generatedBill.setTotalAmount(new BigDecimal("210.00"));

            when(billRepository.findByIdAndRestaurantId(601L, 1L)).thenReturn(Optional.of(generatedBill));
            when(paymentRepository.existsByBillIdAndPaymentStatus(601L, "PAID")).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(701L);
                return p;
            });

            PaymentRequest paymentRequest = new PaymentRequest(new BigDecimal("210.00"), "CASH", null);
            PaymentResponse paymentResponse = billService.processPayment(601L, paymentRequest);

            assertNotNull(paymentResponse);
            assertEquals("PAID", paymentResponse.getPaymentStatus());
            assertEquals("COMPLETED", newOrder.getStatus());
            assertEquals("AVAILABLE", table1.getStatus());
        }

        @Test
        @DisplayName("Kitchen-Free Mode: Completed or cancelled orders cannot be billed or paid")
        void testKitchenFreeCannotBillCompletedOrCancelledOrder() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

            RestaurantSettings kitchenFreeSettings = new RestaurantSettings();
            kitchenFreeSettings.setKitchenEnabled(false);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(kitchenFreeSettings));

            Order completedOrder = new Order();
            completedOrder.setId(302L);
            completedOrder.setRestaurant(restaurant);
            completedOrder.setStatus("COMPLETED");

            when(orderRepository.findByIdAndRestaurantId(302L, 1L)).thenReturn(Optional.of(completedOrder));

            RuntimeException ex1 = assertThrows(RuntimeException.class,
                    () -> billService.createBill(new BillRequest(302L)));
            assertTrue(ex1.getMessage().contains("Cannot create bill for order with status COMPLETED"));

            Order cancelledOrder = new Order();
            cancelledOrder.setId(303L);
            cancelledOrder.setRestaurant(restaurant);
            cancelledOrder.setStatus("CANCELLED");

            when(orderRepository.findByIdAndRestaurantId(303L, 1L)).thenReturn(Optional.of(cancelledOrder));

            RuntimeException ex2 = assertThrows(RuntimeException.class,
                    () -> billService.createBill(new BillRequest(303L)));
            assertTrue(ex2.getMessage().contains("Cannot create bill for order with status CANCELLED"));
        }
    }

    // =========================================================================
    // 3. Waiter Billing & Payment Prohibition: HTTP 403 AccessDenied
    // =========================================================================
    @Nested
    @DisplayName("3. Waiter Billing & Payment Prohibition")
    class WaiterProhibitionTests {

        @Test
        @DisplayName("Waiter cannot generate or view bills (AccessDeniedException / 403)")
        void testWaiterCannotGenerateOrViewBills() {
            when(securityUtils.isAdmin()).thenReturn(false);

            // Waiter attempts to create bill
            assertThrows(AccessDeniedException.class,
                    () -> billService.createBill(new BillRequest(100L)));

            // Waiter attempts to view all bills
            assertThrows(AccessDeniedException.class,
                    () -> billService.getAllBills());

            // Waiter attempts to view single bill
            assertThrows(AccessDeniedException.class,
                    () -> billService.getBillById(50L));

            // Waiter attempts to view bill by order
            assertThrows(AccessDeniedException.class,
                    () -> billService.getBillByOrderId(100L));
        }

        @Test
        @DisplayName("Waiter cannot process payments or view payment records (AccessDeniedException / 403)")
        void testWaiterCannotProcessPayments() {
            when(securityUtils.isAdmin()).thenReturn(false);

            PaymentRequest paymentRequest = new PaymentRequest(new BigDecimal("100.00"), "CASH", null);

            // Waiter attempts to process payment
            assertThrows(AccessDeniedException.class,
                    () -> billService.processPayment(50L, paymentRequest));

            // Waiter attempts to view payments
            assertThrows(AccessDeniedException.class,
                    () -> billService.getPaymentsByBillId(50L));
        }

        @Test
        @DisplayName("Waiter cannot complete orders through direct API call (AccessDeniedException / 403)")
        void testWaiterCannotCompleteOrderDirectly() {
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            assertThrows(AccessDeniedException.class,
                    () -> orderService.completeOrder(100L));
        }
    }

    // =========================================================================
    // 4. Data Isolation and Waiter Order Ownership Restrictions
    // =========================================================================
    @Nested
    @DisplayName("4. Tenant Isolation and Waiter Order Ownership")
    class TenantAndOwnershipTests {

        @Test
        @DisplayName("Waiter cannot access or view another waiter's active order")
        void testWaiterCannotViewAnotherWaitersOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(20L); // Waiter One
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            Order otherWaitersOrder = new Order();
            otherWaitersOrder.setId(401L);
            otherWaitersOrder.setRestaurant(restaurant);
            otherWaitersOrder.setOrderType("DINE_IN");
            otherWaitersOrder.setWaiter(otherWaiterUser); // Assigned to Waiter Two

            when(orderRepository.findByIdAndRestaurantId(401L, 1L)).thenReturn(Optional.of(otherWaitersOrder));

            assertThrows(AccessDeniedException.class,
                    () -> orderService.getOrderById(401L));
        }

        @Test
        @DisplayName("Admin cannot generate bill for order of another restaurant (cross-tenant rejected)")
        void testAdminCrossTenantBillingBlocked() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

            // Order belongs to other restaurant (id 2)
            when(orderRepository.findByIdAndRestaurantId(999L, 1L)).thenReturn(Optional.empty());
            when(orderRepository.findById(999L)).thenReturn(Optional.of(new Order()));

            assertThrows(RuntimeException.class,
                    () -> billService.createBill(new BillRequest(999L)));
        }
    }
}
