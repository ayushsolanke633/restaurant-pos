package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.BillRequest;
import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.PaymentRequest;
import com.restaurant.restaurantpos.dto.PaymentResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderStatusHistory;
import com.restaurant.restaurantpos.entity.Payment;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.PaymentRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.BillService;
import com.restaurant.restaurantpos.service.NotificationService;
import com.restaurant.restaurantpos.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;

@ExtendWith(MockitoExtension.class)
public class Step19BillingPaymentTest {

    @Mock
    private BillRepository billRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private RestaurantTableRepository tableRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private KitchenOrderEventPublisher eventPublisher;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BillService billService;

    @InjectMocks
    private OrderService orderService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private User adminUser;
    private User waiterUser;
    private RestaurantTable dineInTable;
    private Order dineInOrderReady;
    private Order takeawayOrderReady;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Restaurant A");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Restaurant B");

        adminUser = new User();
        adminUser.setId(10L);
        adminUser.setName("Admin User");
        adminUser.setRestaurant(restaurantA);

        waiterUser = new User();
        waiterUser.setId(20L);
        waiterUser.setName("Waiter User");
        waiterUser.setRestaurant(restaurantA);

        dineInTable = new RestaurantTable();
        dineInTable.setId(100L);
        dineInTable.setRestaurant(restaurantA);
        dineInTable.setTableNumber(1);
        dineInTable.setStatus("OCCUPIED");

        dineInOrderReady = new Order();
        dineInOrderReady.setId(500L);
        dineInOrderReady.setRestaurant(restaurantA);
        dineInOrderReady.setTable(dineInTable);
        dineInOrderReady.setOrderType("DINE_IN");
        dineInOrderReady.setStatus("READY");
        dineInOrderReady.setSubtotal(new BigDecimal("100.00"));
        dineInOrderReady.setTaxAmount(new BigDecimal("0.00"));
        dineInOrderReady.setDiscountAmount(new BigDecimal("0.00"));
        dineInOrderReady.setTotalAmount(new BigDecimal("100.00"));

        takeawayOrderReady = new Order();
        takeawayOrderReady.setId(501L);
        takeawayOrderReady.setRestaurant(restaurantA);
        takeawayOrderReady.setTable(null);
        takeawayOrderReady.setOrderType("TAKEAWAY");
        takeawayOrderReady.setStatus("READY");
        takeawayOrderReady.setSubtotal(new BigDecimal("50.00"));
        takeawayOrderReady.setTaxAmount(new BigDecimal("0.00"));
        takeawayOrderReady.setDiscountAmount(new BigDecimal("0.00"));
        takeawayOrderReady.setTotalAmount(new BigDecimal("50.00"));

        lenient().when(userRepository.countByRestaurantIdAndRoleNameIgnoreCase(any(), any())).thenReturn(1L);
    }

    // 1. ADMIN generates bill for READY DINE_IN order -> Table becomes BILLING
    @Test
    void testAdminGeneratesBillForReadyDineInOrder() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(dineInOrderReady));
        when(billRepository.existsByOrderId(500L)).thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill b = invocation.getArgument(0);
            b.setId(1L);
            return b;
        });

        BillRequest request = new BillRequest(500L);
        BillResponse response = billService.createBill(request);

        assertNotNull(response);
        assertEquals("BILL-000001", response.getBillNumber());
        assertEquals(new BigDecimal("100.00"), response.getTotalAmount());
        assertEquals("BILLING", dineInTable.getStatus());
        verify(tableRepository).save(dineInTable);
    }

    // 2. ADMIN generates bill for READY TAKEAWAY order
    @Test
    void testAdminGeneratesBillForReadyTakeawayOrder() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        when(orderRepository.findById(501L)).thenReturn(Optional.of(takeawayOrderReady));
        when(billRepository.existsByOrderId(501L)).thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> {
            Bill b = i.getArgument(0);
            b.setId(2L);
            return b;
        });

        BillRequest request = new BillRequest(501L);
        BillResponse response = billService.createBill(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.00"), response.getTotalAmount());
        verify(tableRepository, never()).save(any());
    }

    // 3. WAITER cannot generate bill -> 403 Forbidden
    @Test
    void testWaiterCannotGenerateBill() {
        when(securityUtils.isAdmin()).thenReturn(false);

        BillRequest request = new BillRequest(500L);
        assertThrows(AccessDeniedException.class, () -> billService.createBill(request));
    }

    // 4. KITCHEN cannot generate bill -> 403 Forbidden
    @Test
    void testKitchenCannotGenerateBill() {
        when(securityUtils.isAdmin()).thenReturn(false);

        BillRequest request = new BillRequest(500L);
        assertThrows(AccessDeniedException.class, () -> billService.createBill(request));
    }

    // 6-10. Bill cannot be generated for non-READY order statuses
    @Test
    void testBillCannotBeGeneratedForNonReadyOrder() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        String[] invalidStatuses = {"NEW", "ACCEPTED", "PREPARING", "COMPLETED", "CANCELLED"};

        for (String st : invalidStatuses) {
            Order order = new Order();
            order.setId(999L);
            order.setRestaurant(restaurantA);
            order.setStatus(st);

            when(orderRepository.findById(999L)).thenReturn(Optional.of(order));

            BillRequest req = new BillRequest(999L);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> billService.createBill(req));
            assertTrue(ex.getMessage().contains("is not in READY status"));
        }
    }

    // 11. Duplicate bill for same order -> 409 Conflict
    @Test
    void testDuplicateBillForSameOrderRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(dineInOrderReady));
        when(billRepository.existsByOrderId(500L)).thenReturn(true);

        BillRequest request = new BillRequest(500L);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> billService.createBill(request));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    // 12 & 13. Bill snapshots order totals and client cannot override restaurantId
    @Test
    void testBillSnapshotsOrderTotals() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        dineInOrderReady.setSubtotal(new BigDecimal("120.50"));
        dineInOrderReady.setTaxAmount(new BigDecimal("5.00"));
        dineInOrderReady.setDiscountAmount(new BigDecimal("2.00"));
        dineInOrderReady.setTotalAmount(new BigDecimal("123.50"));

        when(orderRepository.findById(500L)).thenReturn(Optional.of(dineInOrderReady));
        when(billRepository.existsByOrderId(500L)).thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        BillResponse res = billService.createBill(new BillRequest(500L));
        assertEquals(new BigDecimal("120.50"), res.getSubtotal());
        assertEquals(new BigDecimal("5.00"), res.getTaxAmount());
        assertEquals(new BigDecimal("2.00"), res.getDiscountAmount());
        assertEquals(new BigDecimal("123.50"), res.getTotalAmount());
        assertEquals(1L, res.getRestaurantId());
    }

    // 14. Cross-tenant bill access rejected
    @Test
    void testCrossTenantBillAccessRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Order tenantBOrder = new Order();
        tenantBOrder.setId(600L);
        tenantBOrder.setRestaurant(restaurantB);

        when(orderRepository.findById(600L)).thenReturn(Optional.of(tenantBOrder));

        BillRequest request = new BillRequest(600L);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> billService.createBill(request));
        assertTrue(ex.getMessage().contains("not found"));
    }

    // 15-18 & 23-25. Payment with exact amount succeeds (CASH, UPI, CARD), changes status to COMPLETED, creates history, releases table to AVAILABLE
    @Test
    void testSuccessfulPaymentFlow() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        Bill bill = new Bill();
        bill.setId(101L);
        bill.setRestaurant(restaurantA);
        bill.setOrder(dineInOrderReady);
        bill.setTotalAmount(new BigDecimal("100.00"));
        dineInTable.setStatus("BILLING");

        when(billRepository.findByIdAndRestaurantId(101L, 1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndPaymentStatus(101L, "PAID")).thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequest req = new PaymentRequest(new BigDecimal("100.00"), "UPI", "TXN12345");
        PaymentResponse response = billService.processPayment(101L, req);

        assertNotNull(response);
        assertEquals("PAID", response.getPaymentStatus());
        assertEquals("COMPLETED", dineInOrderReady.getStatus());
        assertEquals("AVAILABLE", dineInTable.getStatus());

        verify(orderRepository).save(dineInOrderReady);
        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
        verify(tableRepository).save(dineInTable);
    }

    // 19-21. Wrong payment amount rejected (underpaid / overpaid)
    @Test
    void testWrongPaymentAmountRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Bill bill = new Bill();
        bill.setId(101L);
        bill.setRestaurant(restaurantA);
        bill.setOrder(dineInOrderReady);
        bill.setTotalAmount(new BigDecimal("100.00"));

        when(billRepository.findByIdAndRestaurantId(101L, 1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndPaymentStatus(101L, "PAID")).thenReturn(false);

        // Underpayment
        PaymentRequest underpaid = new PaymentRequest(new BigDecimal("90.00"), "CASH", null);
        RuntimeException ex1 = assertThrows(RuntimeException.class, () -> billService.processPayment(101L, underpaid));
        assertTrue(ex1.getMessage().contains("Payment amount must equal bill total amount"));

        // Overpayment
        PaymentRequest overpaid = new PaymentRequest(new BigDecimal("110.00"), "CASH", null);
        RuntimeException ex2 = assertThrows(RuntimeException.class, () -> billService.processPayment(101L, overpaid));
        assertTrue(ex2.getMessage().contains("Payment amount must equal bill total amount"));
    }

    // 22. Duplicate successful payment rejected -> 409 Conflict
    @Test
    void testDuplicateSuccessfulPaymentRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Bill bill = new Bill();
        bill.setId(101L);
        bill.setRestaurant(restaurantA);
        bill.setOrder(dineInOrderReady);
        bill.setTotalAmount(new BigDecimal("100.00"));

        when(billRepository.findByIdAndRestaurantId(101L, 1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndPaymentStatus(101L, "PAID")).thenReturn(true);

        PaymentRequest req = new PaymentRequest(new BigDecimal("100.00"), "CASH", null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> billService.processPayment(101L, req));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    // 26. TAKEAWAY payment does not modify table
    @Test
    void testTakeawayPaymentDoesNotModifyTable() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        Bill bill = new Bill();
        bill.setId(102L);
        bill.setRestaurant(restaurantA);
        bill.setOrder(takeawayOrderReady);
        bill.setTotalAmount(new BigDecimal("50.00"));

        when(billRepository.findByIdAndRestaurantId(102L, 1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndPaymentStatus(102L, "PAID")).thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequest req = new PaymentRequest(new BigDecimal("50.00"), "CARD", "CARD_REF_99");
        PaymentResponse response = billService.processPayment(102L, req);

        assertNotNull(response);
        assertEquals("COMPLETED", takeawayOrderReady.getStatus());
        verify(tableRepository, never()).save(any());
    }

    // 33. Direct /api/orders/{id}/complete cannot bypass payment -> 400 Bad Request
    @Test
    void testDirectOrderCompleteEndpointRejected() {
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.completeOrder(500L));
        assertTrue(ex.getMessage().contains("Order can only be completed after successful payment"));
    }
}
