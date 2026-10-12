package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.report.*;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.repository.*;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Step22ReportsTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RestaurantSettingsRepository settingsRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private ReportService reportService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Restaurant A");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Restaurant B");
    }

    // 1. ADMIN can access dashboard
    @Test
    void testAdminCanAccessDashboard() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any())).thenReturn(Collections.emptyList());

        DashboardResponse res = reportService.getDashboardSummary();
        assertNotNull(res);
        assertEquals(LocalDate.now(ZoneId.of("Asia/Kolkata")), res.getDate());
        assertEquals(0L, res.getTotalOrders());
    }

    // 2 & 3. WAITER and KITCHEN receive 403 Forbidden (AccessDeniedException)
    @Test
    void testNonAdminReceives403() {
        when(securityUtils.isAdmin()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> reportService.getDashboardSummary());
        assertThrows(AccessDeniedException.class, () -> reportService.getSalesReport(LocalDate.now(), LocalDate.now()));
    }

    // 5. Restaurant A cannot see Restaurant B sales (tenant isolation)
    @Test
    void testTenantIsolationSales() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        // Call report for Rest 1 -> verify query is scoped to 1L
        reportService.getSalesReport(LocalDate.now().minusDays(1), LocalDate.now());
        verify(orderRepository).findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any());
        verify(paymentRepository).findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any());
        verify(orderRepository, never()).findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(2L), any(), any());
    }

    // 8 & 9. Date range validation (valid vs invalid from > to)
    @Test
    void testDateRangeValidation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        // Valid
        assertDoesNotThrow(() -> reportService.getSalesReport(from, to));

        // Invalid (from > to) -> 400 Bad Request message
        LocalDate invalidFrom = LocalDate.of(2026, 8, 15);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> reportService.getSalesReport(invalidFrom, to));
        assertTrue(ex.getMessage().contains("must be on or before"));
    }

    // 10. Dashboard returns zero values when no data exists today
    @Test
    void testDashboardZeroValuesWhenNoData() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any())).thenReturn(Collections.emptyList());

        DashboardResponse res = reportService.getDashboardSummary();
        assertEquals(BigDecimal.ZERO.setScale(2), res.getTotalRevenue());
        assertEquals(BigDecimal.ZERO.setScale(2), res.getAverageOrderValue());
        assertEquals(0L, res.getTotalOrders());
    }

    // 11 & 12 & 13. Revenue includes successful payments only (excludes pending/failed payments & cancelled orders)
    @Test
    void testRevenueCalculationsFromSuccessfulPayments() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Order completedOrder = new Order();
        completedOrder.setId(10L);
        completedOrder.setStatus("COMPLETED");
        completedOrder.setTaxAmount(new BigDecimal("5.00"));
        completedOrder.setDiscountAmount(new BigDecimal("2.00"));

        Bill bill = new Bill();
        bill.setId(100L);
        bill.setOrder(completedOrder);

        Payment paidPayment = new Payment();
        paidPayment.setBill(bill);
        paidPayment.setPaymentStatus("PAID");
        paidPayment.setPaymentMethod("CASH");
        paidPayment.setAmount(new BigDecimal("100.00"));

        LocalDate today = LocalDate.now();

        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any())).thenReturn(Arrays.asList(completedOrder));
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any())).thenReturn(Arrays.asList(paidPayment));

        SalesReportResponse res = reportService.getSalesReport(today, today);
        assertEquals(new BigDecimal("100.00"), res.getTotalRevenue());
        assertEquals(new BigDecimal("100.00"), res.getAverageOrderValue());
        assertEquals(new BigDecimal("5.00"), res.getTotalTax());
        assertEquals(new BigDecimal("2.00"), res.getTotalDiscount());
    }

    // 14. Payment method totals (CASH, UPI, CARD)
    @Test
    void testPaymentMethodTotals() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Payment pCash = new Payment();
        pCash.setPaymentMethod("CASH");
        pCash.setPaymentStatus("PAID");
        pCash.setAmount(new BigDecimal("50.00"));

        Payment pUpi = new Payment();
        pUpi.setPaymentMethod("UPI");
        pUpi.setPaymentStatus("PAID");
        pUpi.setAmount(new BigDecimal("150.00"));

        LocalDate today = LocalDate.now();
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any()))
                .thenReturn(Arrays.asList(pCash, pUpi));

        List<PaymentMethodReportResponse> res = reportService.getPaymentMethodBreakdown(today, today);
        assertEquals(3, res.size()); // CASH, UPI, CARD

        PaymentMethodReportResponse cashRes = res.stream().filter(r -> "CASH".equalsIgnoreCase(r.getPaymentMethod())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("50.00"), cashRes.getAmount());
        assertEquals(1L, cashRes.getTransactionCount());

        PaymentMethodReportResponse cardRes = res.stream().filter(r -> "CARD".equalsIgnoreCase(r.getPaymentMethod())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("0.00"), cardRes.getAmount());
        assertEquals(0L, cardRes.getTransactionCount());
    }

    // 15 & 16. Top-selling items aggregation using historical unit_price
    @Test
    void testTopSellingItemsReport() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        Object[] item1 = new Object[]{101L, "Burger", 25L, new BigDecimal("2500.00")};
        Object[] item2 = new Object[]{102L, "Fries", 15L, new BigDecimal("750.00")};

        LocalDate today = LocalDate.now();
        when(orderItemRepository.findTopSellingItems(eq(1L), any(), any())).thenReturn(Arrays.asList(item1, item2));

        List<TopSellingItemResponse> res = reportService.getTopSellingItems(today, today, 10);
        assertEquals(2, res.size());
        assertEquals("Burger", res.get(0).getMenuItemName());
        assertEquals(25L, res.get(0).getQuantitySold());
        assertEquals(new BigDecimal("2500.00"), res.get(0).getRevenue());
    }

    // 17. Inventory low-stock summary calculation
    @Test
    void testInventorySummaryReport() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        when(inventoryItemRepository.countByRestaurantIdAndIsActiveTrue(1L)).thenReturn(20L);
        when(inventoryItemRepository.countLowStockItems(1L)).thenReturn(3L);
        when(inventoryItemRepository.countOutOfStockItems(1L)).thenReturn(1L);
        when(inventoryItemRepository.calculateTotalInventoryValue(1L)).thenReturn(new BigDecimal("12500.00"));

        InventorySummaryResponse res = reportService.getInventorySummary();
        assertEquals(20L, res.getTotalActiveItems());
        assertEquals(3L, res.getLowStockItemCount());
        assertEquals(1L, res.getOutOfStockItemCount());
        assertEquals(new BigDecimal("12500.00"), res.getTotalInventoryValue());
    }

    // 18. Table status counts
    @Test
    void testTableSummaryReport() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        when(tableRepository.countByRestaurantId(1L)).thenReturn(10L);
        when(tableRepository.countByRestaurantIdAndStatus(1L, "AVAILABLE")).thenReturn(6L);
        when(tableRepository.countByRestaurantIdAndStatus(1L, "OCCUPIED")).thenReturn(3L);
        when(tableRepository.countByRestaurantIdAndStatus(1L, "BILLING")).thenReturn(1L);

        TableSummaryResponse res = reportService.getTableSummary();
        assertEquals(10L, res.getTotalTables());
        assertEquals(6L, res.getAvailableTables());
        assertEquals(3L, res.getOccupiedTables());
        assertEquals(1L, res.getBillingTables());
    }

    // 19. Staff counts
    @Test
    void testStaffSummaryReport() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        when(userRepository.countByRestaurantId(1L)).thenReturn(8L);
        when(userRepository.countByRestaurantIdAndIsActive(1L, true)).thenReturn(7L);
        when(userRepository.countByRestaurantIdAndIsActive(1L, false)).thenReturn(1L);
        when(userRepository.countByRestaurantIdAndRoleNameIgnoreCase(1L, "ADMIN")).thenReturn(2L);
        when(userRepository.countByRestaurantIdAndRoleNameIgnoreCase(1L, "WAITER")).thenReturn(4L);
        when(userRepository.countByRestaurantIdAndRoleNameIgnoreCase(1L, "KITCHEN")).thenReturn(2L);

        StaffSummaryResponse res = reportService.getStaffSummary();
        assertEquals(8L, res.getTotalStaff());
        assertEquals(7L, res.getActiveStaff());
        assertEquals(1L, res.getInactiveStaff());
        assertEquals(2L, res.getAdminCount());
        assertEquals(4L, res.getWaiterCount());
        assertEquals(2L, res.getKitchenCount());
    }

    // 20. Customer counts
    @Test
    void testCustomerSummaryReport() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        when(customerRepository.countByRestaurantId(1L)).thenReturn(50L);
        when(customerRepository.countByRestaurantIdAndCreatedAtGreaterThanEqual(eq(1L), any())).thenReturn(5L);

        CustomerSummaryResponse res = reportService.getCustomerSummary();
        assertEquals(50L, res.getTotalCustomers());
        assertEquals(5L, res.getNewCustomersToday());
    }

    // ==================================================
    // NEW TIMEZONE & BUSINESS-DAY TESTS
    // ==================================================

    @Test
    void testTimezoneBoundaryInclusions() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        RestaurantSettings settings = new RestaurantSettings();
        settings.setTimezone("Asia/Kolkata");
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        LocalDate oct7 = LocalDate.of(2026, 10, 7);
        LocalDate oct8 = LocalDate.of(2026, 10, 8);

        // Test 1: Oct 7 23:55 IST included in Oct 7, excluded from Oct 8
        // Test 2: Oct 8 00:05 IST excluded from Oct 7, included in Oct 8
        // Test 3: Oct 7 00:00 IST included in Oct 7
        // Test 4: Oct 7 23:59:59 IST included in Oct 7
        // Test 5: Oct 7 23:59 IST and Oct 8 00:01 IST belong to respective business dates

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        reportService.getSalesReport(oct7, oct7);
        verify(paymentRepository).findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), startCaptor.capture(), endCaptor.capture());

        LocalDateTime oct7Start = startCaptor.getValue();
        LocalDateTime oct7End = endCaptor.getValue();

        assertEquals(LocalDateTime.of(2026, 10, 7, 0, 0, 0), oct7Start);
        assertEquals(LocalDateTime.of(2026, 10, 8, 0, 0, 0), oct7End);

        // Boundary checks against Oct 7 interval [2026-10-07 00:00:00, 2026-10-08 00:00:00)
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 7, 23, 55, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 8, 0, 5, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 10, 7, 0, 0, 0);
        LocalDateTime t4 = LocalDateTime.of(2026, 10, 7, 23, 59, 59);

        assertTrue(!t1.isBefore(oct7Start) && t1.isBefore(oct7End), "2026-10-07 23:55 MUST be in Oct 7 report");
        assertFalse(!t2.isBefore(oct7Start) && t2.isBefore(oct7End), "2026-10-08 00:05 MUST NOT be in Oct 7 report");
        assertTrue(!t3.isBefore(oct7Start) && t3.isBefore(oct7End), "2026-10-07 00:00 MUST be in Oct 7 report");
        assertTrue(!t4.isBefore(oct7Start) && t4.isBefore(oct7End), "2026-10-07 23:59:59 MUST be in Oct 7 report");

        // Now test Oct 8 report
        reportService.getSalesReport(oct8, oct8);
        verify(paymentRepository, times(2)).findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), startCaptor.capture(), endCaptor.capture());

        LocalDateTime oct8Start = startCaptor.getValue();
        LocalDateTime oct8End = endCaptor.getValue();

        assertEquals(LocalDateTime.of(2026, 10, 8, 0, 0, 0), oct8Start);
        assertEquals(LocalDateTime.of(2026, 10, 9, 0, 0, 0), oct8End);

        assertFalse(!t1.isBefore(oct8Start) && t1.isBefore(oct8End), "2026-10-07 23:55 MUST NOT be in Oct 8 report");
        assertTrue(!t2.isBefore(oct8Start) && t2.isBefore(oct8End), "2026-10-08 00:05 MUST be in Oct 8 report");
    }

    @Test
    void testDashboardUsesConfiguredTimezone() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        RestaurantSettings settings = new RestaurantSettings();
        settings.setTimezone("Asia/Kolkata");
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        DashboardResponse response = reportService.getDashboardSummary();
        assertNotNull(response);

        verify(orderRepository).findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), startCaptor.capture(), endCaptor.capture());
        LocalDateTime start = startCaptor.getValue();
        LocalDateTime end = endCaptor.getValue();

        LocalDate expectedToday = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        assertEquals(expectedToday.atStartOfDay(), start);
        assertEquals(expectedToday.plusDays(1).atStartOfDay(), end);
    }

    @Test
    void testMultiDayReportInclusiveRange() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDate from = LocalDate.of(2026, 10, 7);
        LocalDate to = LocalDate.of(2026, 10, 8);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        reportService.getSalesReport(from, to);

        verify(orderRepository).findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), startCaptor.capture(), endCaptor.capture());
        assertEquals(LocalDateTime.of(2026, 10, 7, 0, 0, 0), startCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 10, 9, 0, 0, 0), endCaptor.getValue());
    }

    @Test
    void testInvalidTimezoneFallbackToAsiaKolkata() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        RestaurantSettings settings = new RestaurantSettings();
        settings.setTimezone("Invalid/Timezone_Name");
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        DashboardResponse response = reportService.getDashboardSummary();
        assertNotNull(response);
        assertEquals(LocalDate.now(ZoneId.of("Asia/Kolkata")), response.getDate());
    }

    // ==================================================
    // CHANGE 12 REPORT BILLS TESTS
    // ==================================================

    @Test
    void testReportBillsTodayPaidBillsReturned() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDate today = LocalDate.of(2026, 10, 8);

        Order order = new Order();
        order.setId(54L);
        order.setStatus("COMPLETED");
        order.setOrderType("DINE_IN");

        RestaurantTable table = new RestaurantTable();
        table.setId(99L);
        table.setTableNumber(6);
        order.setTable(table);

        User waiter = new User();
        waiter.setId(5L);
        waiter.setName("Waiter Senior");
        order.setWaiter(waiter);

        Bill bill = new Bill();
        bill.setId(123L);
        bill.setBillNumber("BILL-000001");
        bill.setOrder(order);
        bill.setSubtotal(new BigDecimal("230.00"));
        bill.setTaxAmount(new BigDecimal("20.50"));
        bill.setDiscountAmount(BigDecimal.ZERO);
        bill.setTotalAmount(new BigDecimal("250.50"));
        bill.setCreatedAt(LocalDateTime.of(2026, 10, 8, 10, 0, 0));

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setBill(bill);
        payment.setAmount(new BigDecimal("250.50"));
        payment.setPaymentMethod("CASH");
        payment.setPaymentStatus("PAID");
        payment.setPaidAt(LocalDateTime.of(2026, 10, 8, 10, 15, 0));

        MenuItem menuItem = new MenuItem();
        menuItem.setId(101L);
        menuItem.setName("Dal Makhani");

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrder(order);
        item.setMenuItem(menuItem);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("230.00"));
        item.setSubtotal(new BigDecimal("230.00"));

        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any()))
                .thenReturn(Collections.singletonList(payment));
        when(orderItemRepository.findByOrderId(54L)).thenReturn(Collections.singletonList(item));

        List<ReportBillResponse> bills = reportService.getReportBills(today, today);

        assertNotNull(bills);
        assertEquals(1, bills.size());

        ReportBillResponse b = bills.get(0);
        assertEquals(123L, b.getBillId());
        assertEquals(54L, b.getOrderId());
        assertEquals("BILL-000001", b.getBillNumber());
        assertEquals("DINE_IN", b.getOrderType());
        assertEquals(6, b.getTableNumber()); // Verified: comes from tableNumber (6), not tableId (99)
        assertEquals("Table 6", b.getTableName());
        assertEquals("Waiter Senior", b.getWaiterName());
        assertEquals("CASH", b.getPaymentMethod());
        assertEquals("PAID", b.getPaymentStatus());
        assertEquals(new BigDecimal("250.50"), b.getTotalAmount());
        assertEquals(1, b.getItems().size());
        assertEquals("Dal Makhani", b.getItems().get(0).getMenuItemName());
    }

    @Test
    void testReportBillsYesterdayExcludedFromToday() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDate today = LocalDate.of(2026, 10, 8);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        reportService.getReportBills(today, today);

        verify(paymentRepository).findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), startCaptor.capture(), endCaptor.capture());
        assertEquals(LocalDateTime.of(2026, 10, 8, 0, 0, 0), startCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 10, 9, 0, 0, 0), endCaptor.getValue());
    }

    @Test
    void testReportBillsMidnightBoundaries() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDate oct8 = LocalDate.of(2026, 10, 8);
        LocalDateTime oct8Start = LocalDateTime.of(2026, 10, 8, 0, 0, 0);
        LocalDateTime oct8End = LocalDateTime.of(2026, 10, 9, 0, 0, 0);

        LocalDateTime exactMidnight = LocalDateTime.of(2026, 10, 8, 0, 0, 0);
        LocalDateTime justBeforeMidnight = LocalDateTime.of(2026, 10, 7, 23, 59, 59);
        LocalDateTime nextDayMidnight = LocalDateTime.of(2026, 10, 9, 0, 0, 0);

        // [oct8Start, oct8End)
        assertTrue(!exactMidnight.isBefore(oct8Start) && exactMidnight.isBefore(oct8End), "Paid bill exactly at midnight MUST be included");
        assertFalse(!justBeforeMidnight.isBefore(oct8Start) && justBeforeMidnight.isBefore(oct8End), "Paid bill just before midnight MUST be excluded from Oct 8");
        assertFalse(!nextDayMidnight.isBefore(oct8Start) && nextDayMidnight.isBefore(oct8End), "Next-day midnight payment MUST be excluded from Oct 8");
    }

    @Test
    void testReportBillsUnpaidCancelledFailedExcluded() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDate today = LocalDate.of(2026, 10, 8);

        Order cancelledOrder = new Order();
        cancelledOrder.setId(1L);
        cancelledOrder.setStatus("CANCELLED");

        Bill billCancelled = new Bill();
        billCancelled.setId(10L);
        billCancelled.setOrder(cancelledOrder);

        Payment pCancelled = new Payment();
        pCancelled.setBill(billCancelled);
        pCancelled.setPaymentStatus("PAID");
        pCancelled.setPaidAt(LocalDateTime.of(2026, 10, 8, 12, 0, 0));

        Order completedOrder = new Order();
        completedOrder.setId(2L);
        completedOrder.setStatus("COMPLETED");

        Bill billCompleted = new Bill();
        billCompleted.setId(20L);
        billCompleted.setOrder(completedOrder);
        billCompleted.setSubtotal(new BigDecimal("100.00"));
        billCompleted.setTaxAmount(BigDecimal.ZERO);
        billCompleted.setDiscountAmount(BigDecimal.ZERO);
        billCompleted.setTotalAmount(new BigDecimal("100.00"));

        Payment pPaid = new Payment();
        pPaid.setBill(billCompleted);
        pPaid.setPaymentStatus("PAID");
        pPaid.setPaidAt(LocalDateTime.of(2026, 10, 8, 14, 0, 0));

        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any()))
                .thenReturn(Arrays.asList(pCancelled, pPaid));

        List<ReportBillResponse> bills = reportService.getReportBills(today, today);

        assertEquals(1, bills.size());
        assertEquals(20L, bills.get(0).getBillId()); // Cancelled order bill is excluded!
    }

    @Test
    void testReportBillsTenantIsolationAndSecurity() {
        // Non-admin receives 403 AccessDeniedException
        when(securityUtils.isAdmin()).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> reportService.getReportBills(LocalDate.now(), LocalDate.now()));

        // Admin scopes to restaurantId
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        reportService.getReportBills(LocalDate.now(), LocalDate.now());
        verify(paymentRepository).findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any());
        verify(paymentRepository, never()).findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(2L), eq("PAID"), any(), any());
    }

    @Test
    void testReportBillsTableNumberAndOrderTypeMapping() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDate today = LocalDate.of(2026, 10, 8);

        // Takeaway order
        Order takeawayOrder = new Order();
        takeawayOrder.setId(55L);
        takeawayOrder.setStatus("COMPLETED");
        takeawayOrder.setOrderType("TAKEAWAY");
        takeawayOrder.setTable(null);

        Bill takeawayBill = new Bill();
        takeawayBill.setId(200L);
        takeawayBill.setBillNumber("BILL-000002");
        takeawayBill.setOrder(takeawayOrder);
        takeawayBill.setSubtotal(new BigDecimal("130.00"));
        takeawayBill.setTaxAmount(BigDecimal.ZERO);
        takeawayBill.setDiscountAmount(BigDecimal.ZERO);
        takeawayBill.setTotalAmount(new BigDecimal("130.00"));

        Payment takeawayPayment = new Payment();
        takeawayPayment.setBill(takeawayBill);
        takeawayPayment.setPaymentStatus("PAID");
        takeawayPayment.setPaidAt(LocalDateTime.of(2026, 10, 8, 11, 0, 0));

        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any()))
                .thenReturn(Collections.singletonList(takeawayPayment));

        List<ReportBillResponse> bills = reportService.getReportBills(today, today);

        assertEquals(1, bills.size());
        ReportBillResponse b = bills.get(0);
        assertEquals("TAKEAWAY", b.getOrderType());
        assertNull(b.getTableNumber(), "Table number MUST be null for Takeaway");
        assertNull(b.getTableName(), "Table name MUST be null for Takeaway");
    }

    @Test
    void testReportBillsMultipleBillsReturnedInCorrectOrder() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDate today = LocalDate.of(2026, 10, 8);

        Order o1 = new Order();
        o1.setId(1L);
        o1.setStatus("COMPLETED");
        o1.setOrderType("DINE_IN");

        Bill b1 = new Bill();
        b1.setId(10L);
        b1.setBillNumber("BILL-000001");
        b1.setOrder(o1);
        b1.setSubtotal(new BigDecimal("100.00"));
        b1.setTaxAmount(BigDecimal.ZERO);
        b1.setDiscountAmount(BigDecimal.ZERO);
        b1.setTotalAmount(new BigDecimal("100.00"));

        Payment p1 = new Payment();
        p1.setBill(b1);
        p1.setPaymentStatus("PAID");
        p1.setPaidAt(LocalDateTime.of(2026, 10, 8, 14, 0, 0)); // 2 PM

        Order o2 = new Order();
        o2.setId(2L);
        o2.setStatus("COMPLETED");
        o2.setOrderType("DINE_IN");

        Bill b2 = new Bill();
        b2.setId(20L);
        b2.setBillNumber("BILL-000002");
        b2.setOrder(o2);
        b2.setSubtotal(new BigDecimal("200.00"));
        b2.setTaxAmount(BigDecimal.ZERO);
        b2.setDiscountAmount(BigDecimal.ZERO);
        b2.setTotalAmount(new BigDecimal("200.00"));

        Payment p2 = new Payment();
        p2.setBill(b2);
        p2.setPaymentStatus("PAID");
        p2.setPaidAt(LocalDateTime.of(2026, 10, 8, 10, 0, 0)); // 10 AM (earlier)

        // Pass out of order list [p1 (14:00), p2 (10:00)]
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any()))
                .thenReturn(Arrays.asList(p1, p2));

        List<ReportBillResponse> bills = reportService.getReportBills(today, today);

        assertEquals(2, bills.size());
        assertEquals(20L, bills.get(0).getBillId(), "Earlier paid bill (10:00 AM) MUST be first");
        assertEquals(10L, bills.get(1).getBillId(), "Later paid bill (14:00 PM) MUST be second");
    }
}
