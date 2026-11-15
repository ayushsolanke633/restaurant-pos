package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.report.DashboardResponse;
import com.restaurant.restaurantpos.dto.report.ReportBillResponse;
import com.restaurant.restaurantpos.dto.report.SalesReportResponse;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.repository.*;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OrderSettlementInvestigationTest {

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
    private BillRepository billRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private ReportService reportService;

    private Restaurant restaurant;
    private Order orderA; // Created 2 days ago, settled today
    private Order orderB; // Created today, not settled
    private Order orderC; // Created today, settled today

    private Bill billA;
    private Bill billC;

    private Payment paymentA;
    private Payment paymentC;

    private final LocalDate today = LocalDate.of(2026, 10, 27);
    private final LocalDate twoDaysAgo = LocalDate.of(2026, 10, 25);

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Biryani House");

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        RestaurantSettings settings = new RestaurantSettings();
        settings.setTimezone("Asia/Kolkata");
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        // Order A: Created 2 days ago (2026-10-25 14:00), settled today (2026-10-27 11:30)
        orderA = new Order();
        orderA.setId(101L);
        orderA.setRestaurant(restaurant);
        orderA.setStatus("COMPLETED");
        orderA.setOrderType("DINE_IN");
        orderA.setSubtotal(new BigDecimal("450.00"));
        orderA.setTaxAmount(new BigDecimal("50.00"));
        orderA.setCgstAmount(new BigDecimal("25.00"));
        orderA.setSgstAmount(new BigDecimal("25.00"));
        orderA.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        orderA.setTotalAmount(new BigDecimal("500.00"));
        orderA.setCreatedAt(twoDaysAgo.atTime(14, 0));
        orderA.setUpdatedAt(today.atTime(11, 30));

        billA = new Bill();
        billA.setId(1001L);
        billA.setRestaurant(restaurant);
        billA.setOrder(orderA);
        billA.setBillNumber("BILL-001001");
        billA.setSubtotal(orderA.getSubtotal());
        billA.setTaxAmount(orderA.getTaxAmount());
        billA.setCgstAmount(orderA.getCgstAmount());
        billA.setSgstAmount(orderA.getSgstAmount());
        billA.setDiscountAmount(orderA.getDiscountAmount());
        billA.setTotalAmount(orderA.getTotalAmount());
        billA.setCreatedAt(today.atTime(11, 0)); // Billed today

        paymentA = new Payment();
        paymentA.setId(5001L);
        paymentA.setRestaurant(restaurant);
        paymentA.setBill(billA);
        paymentA.setAmount(new BigDecimal("500.00"));
        paymentA.setPaymentMethod("UPI");
        paymentA.setPaymentStatus("PAID");
        paymentA.setPaidAt(today.atTime(11, 30)); // Settled today
        paymentA.setCreatedAt(today.atTime(11, 30));

        // Order B: Created today (2026-10-27 12:00), NOT settled (status NEW or READY, no payment)
        orderB = new Order();
        orderB.setId(102L);
        orderB.setRestaurant(restaurant);
        orderB.setStatus("NEW");
        orderB.setOrderType("TAKEAWAY");
        orderB.setSubtotal(new BigDecimal("300.00"));
        orderB.setTaxAmount(BigDecimal.ZERO.setScale(2));
        orderB.setCgstAmount(BigDecimal.ZERO.setScale(2));
        orderB.setSgstAmount(BigDecimal.ZERO.setScale(2));
        orderB.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        orderB.setTotalAmount(new BigDecimal("300.00"));
        orderB.setCreatedAt(today.atTime(12, 0)); // Created today
        orderB.setUpdatedAt(today.atTime(12, 0));

        // Order C: Created today (2026-10-27 12:15), settled today (2026-10-27 13:00)
        orderC = new Order();
        orderC.setId(103L);
        orderC.setRestaurant(restaurant);
        orderC.setStatus("COMPLETED");
        orderC.setOrderType("DINE_IN");
        orderC.setSubtotal(new BigDecimal("650.00"));
        orderC.setTaxAmount(new BigDecimal("50.00"));
        orderC.setCgstAmount(new BigDecimal("25.00"));
        orderC.setSgstAmount(new BigDecimal("25.00"));
        orderC.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        orderC.setTotalAmount(new BigDecimal("700.00"));
        orderC.setCreatedAt(today.atTime(12, 15)); // Created today
        orderC.setUpdatedAt(today.atTime(13, 0));

        billC = new Bill();
        billC.setId(1003L);
        billC.setRestaurant(restaurant);
        billC.setOrder(orderC);
        billC.setBillNumber("BILL-001003");
        billC.setSubtotal(orderC.getSubtotal());
        billC.setTaxAmount(orderC.getTaxAmount());
        billC.setCgstAmount(orderC.getCgstAmount());
        billC.setSgstAmount(orderC.getSgstAmount());
        billC.setDiscountAmount(orderC.getDiscountAmount());
        billC.setTotalAmount(orderC.getTotalAmount());
        billC.setCreatedAt(today.atTime(12, 45)); // Billed today

        paymentC = new Payment();
        paymentC.setId(5003L);
        paymentC.setRestaurant(restaurant);
        paymentC.setBill(billC);
        paymentC.setAmount(new BigDecimal("700.00"));
        paymentC.setPaymentMethod("CASH");
        paymentC.setPaymentStatus("PAID");
        paymentC.setPaidAt(today.atTime(13, 0)); // Settled today
        paymentC.setCreatedAt(today.atTime(13, 0));
    }

    @Test
    @DisplayName("Expected Bug 1 Scenario: Order A (2 days ago, settled today), Order B (today, unpaid), Order C (today, settled today)")
    void testExpectedScenario_OrdersCreatedVsOrdersSettledVsBillsGenerated() {
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        // Orders created today: Order B and Order C
        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(Arrays.asList(orderB, orderC));

        // Payments settled today: Payment A (for Order A) and Payment C (for Order C)
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), eq(todayStart), eq(todayEnd)))
                .thenReturn(Arrays.asList(paymentA, paymentC));

        // Bills generated today: Bill A and Bill C
        when(billRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(2L);

        SalesReportResponse report = reportService.getSalesReport(today, today);

        // Required assertions per prompt:
        // - Orders Created: 2 (Order B and Order C)
        assertEquals(2L, report.getTotalOrders(), "Orders Created must be 2");

        // - Orders Settled: 2 (Order A and Order C)
        assertEquals(2L, report.getOrdersSettled(), "Orders Settled must be 2");

        // - Bills Generated: 2 (Bill A and Bill C generated today)
        assertEquals(2L, report.getBillsGenerated(), "Bills Generated must be 2");

        // - Sales Revenue: 1,200.00 (500 + 700)
        assertEquals(new BigDecimal("1200.00"), report.getTotalRevenue(), "Sales Revenue must be 1200.00");

        // - Average Order Value: 600.00 (1200.00 / 2 orders settled)
        assertEquals(new BigDecimal("600.00"), report.getAverageOrderValue(), "Average Order Value must be 600.00");

        // - Backward compatibility: completedOrders is preserved
        assertEquals(1L, report.getCompletedOrders());
    }

    @Test
    @DisplayName("Multiple payment records for one order do not inflate ordersSettled")
    void testMultiplePaymentsForSingleOrder_DoesNotInflateOrdersSettled() {
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        // Split payment for Order C: Payment 1 (UPI 400.00) + Payment 2 (CASH 300.00)
        Payment paymentC1 = new Payment();
        paymentC1.setId(6001L);
        paymentC1.setRestaurant(restaurant);
        paymentC1.setBill(billC);
        paymentC1.setAmount(new BigDecimal("400.00"));
        paymentC1.setPaymentMethod("UPI");
        paymentC1.setPaymentStatus("PAID");
        paymentC1.setPaidAt(today.atTime(12, 50));

        Payment paymentC2 = new Payment();
        paymentC2.setId(6002L);
        paymentC2.setRestaurant(restaurant);
        paymentC2.setBill(billC);
        paymentC2.setAmount(new BigDecimal("300.00"));
        paymentC2.setPaymentMethod("CASH");
        paymentC2.setPaymentStatus("PAID");
        paymentC2.setPaidAt(today.atTime(13, 0));

        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.singletonList(orderC));
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), eq(todayStart), eq(todayEnd)))
                .thenReturn(Arrays.asList(paymentC1, paymentC2));
        when(billRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(1L);

        SalesReportResponse report = reportService.getSalesReport(today, today);

        // Multiple payments for Order C must not inflate ordersSettled beyond 1
        assertEquals(1L, report.getOrdersSettled(), "Orders settled must be deduplicated to 1");
        assertEquals(1L, report.getTotalOrders());
        assertEquals(1L, report.getBillsGenerated());
        assertEquals(new BigDecimal("700.00"), report.getTotalRevenue());
        assertEquals(new BigDecimal("700.00"), report.getAverageOrderValue());
    }

    @Test
    @DisplayName("Zero settlements and zero revenue safely return 0.00 without divide-by-zero")
    void testZeroSettlementsAndZeroRevenue_ReturnsSafeDefaults() {
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.emptyList());
        when(billRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(0L);

        SalesReportResponse report = reportService.getSalesReport(today, today);

        assertEquals(0L, report.getTotalOrders());
        assertEquals(0L, report.getOrdersSettled());
        assertEquals(0L, report.getBillsGenerated());
        assertEquals(new BigDecimal("0.00"), report.getTotalRevenue());
        assertEquals(new BigDecimal("0.00"), report.getAverageOrderValue());
    }

    @Test
    @DisplayName("Dashboard Summary and Sales Report consistency for the same period")
    void testDashboardAndReportsConsistencyForSamePeriod() {
        LocalDate localToday = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDateTime todayStart = localToday.atStartOfDay();
        LocalDateTime todayEnd = localToday.plusDays(1).atStartOfDay();

        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(Arrays.asList(orderB, orderC));
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), eq(todayStart), eq(todayEnd)))
                .thenReturn(Arrays.asList(paymentA, paymentC));
        when(billRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(2L);

        DashboardResponse dashboard = reportService.getDashboardSummary();
        SalesReportResponse salesReport = reportService.getSalesReport(localToday, localToday);

        // Verify exact metric consistency between Dashboard and Sales Report
        assertEquals(salesReport.getTotalOrders(), dashboard.getTotalOrders(), "Total orders created must match");
        assertEquals(salesReport.getOrdersSettled(), dashboard.getOrdersSettled(), "Orders settled must match");
        assertEquals(salesReport.getBillsGenerated(), dashboard.getBillsGenerated(), "Bills generated must match");
        assertEquals(salesReport.getTotalRevenue(), dashboard.getTotalRevenue(), "Total revenue must match");
        assertEquals(salesReport.getAverageOrderValue(), dashboard.getAverageOrderValue(), "Average order value must match");
    }

    @Test
    @DisplayName("Tenant isolation: reports strictly filter by authenticated restaurant ID")
    void testTenantIsolation() {
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        // Restaurant 2 data
        Restaurant restaurant2 = new Restaurant();
        restaurant2.setId(2L);
        Order orderR2 = new Order();
        orderR2.setId(201L);
        orderR2.setRestaurant(restaurant2);
        orderR2.setTotalAmount(new BigDecimal("999.00"));
        orderR2.setCreatedAt(today.atTime(10, 0));

        // When queried for Restaurant 1, return empty (simulating repo's WHERE restaurant_id = 1)
        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.emptyList());
        when(billRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(0L);

        SalesReportResponse reportR1 = reportService.getSalesReport(today, today);
        assertEquals(0L, reportR1.getTotalOrders());
        assertEquals(0L, reportR1.getOrdersSettled());
        assertEquals(new BigDecimal("0.00"), reportR1.getTotalRevenue());

        // Now simulate user switched to Restaurant 2
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);
        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(2L), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.singletonList(orderR2));
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(2L), eq("PAID"), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.emptyList());
        when(billRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(2L), eq(todayStart), eq(todayEnd)))
                .thenReturn(0L);

        SalesReportResponse reportR2 = reportService.getSalesReport(today, today);
        assertEquals(1L, reportR2.getTotalOrders());
        assertEquals(0L, reportR2.getOrdersSettled());
    }

    @Test
    @DisplayName("Date boundary verification: half-open interval [start, end)")
    void testDateBoundaryHalfOpenInterval() {
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        // Exact start boundary payment (00:00:00) should be included in [start, end)
        Payment startBoundaryPayment = new Payment();
        startBoundaryPayment.setId(7001L);
        startBoundaryPayment.setRestaurant(restaurant);
        startBoundaryPayment.setBill(billC);
        startBoundaryPayment.setAmount(new BigDecimal("100.00"));
        startBoundaryPayment.setPaymentStatus("PAID");
        startBoundaryPayment.setPaidAt(todayStart);

        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.singletonList(orderC));
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), eq(todayStart), eq(todayEnd)))
                .thenReturn(Collections.singletonList(startBoundaryPayment));
        when(billRepository.countByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), eq(todayStart), eq(todayEnd)))
                .thenReturn(1L);

        SalesReportResponse report = reportService.getSalesReport(today, today);
        assertEquals(1L, report.getOrdersSettled());
        assertEquals(new BigDecimal("100.00"), report.getTotalRevenue());
    }
}
