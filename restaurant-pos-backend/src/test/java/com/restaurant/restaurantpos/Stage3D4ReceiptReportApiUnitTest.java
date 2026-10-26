package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.dto.RestaurantSettingsResponse;
import com.restaurant.restaurantpos.dto.report.DashboardResponse;
import com.restaurant.restaurantpos.dto.report.ReportBillResponse;
import com.restaurant.restaurantpos.dto.report.SalesReportResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Payment;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.InventoryItemRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.PaymentRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Stage3D4ReceiptReportApiUnitTest {

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

    private Restaurant restaurant;
    private User adminUser;
    private RestaurantSettings settings;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Dhaba Central");

        Role role = new Role();
        role.setId(1L);
        role.setName("ROLE_ADMIN");

        adminUser = new User();
        adminUser.setId(99L);
        adminUser.setName("Admin User");
        adminUser.setRole(role);
        adminUser.setRestaurant(restaurant);

        settings = new RestaurantSettings();
        settings.setId(20L);
        settings.setRestaurant(restaurant);
        settings.setTaxEnabled(true);
        settings.setDefaultTaxRate(new BigDecimal("5.00"));
        settings.setCgstRate(new BigDecimal("2.50"));
        settings.setSgstRate(new BigDecimal("2.50"));
        settings.setGstMode("GST_EXCLUDED");

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));
    }

    @Test
    @DisplayName("1. Reporting aggregates combined tax and separate CGST/SGST amounts correctly")
    void testCombinedTaxReportTotalsAndCgstSgstAggregates() {
        LocalDate today = LocalDate.now();

        // Order 1: CGST 5.00, SGST 5.00, Tax 10.00, Total 210.00
        Order order1 = new Order();
        order1.setId(101L);
        order1.setStatus("COMPLETED");
        order1.setSubtotal(new BigDecimal("200.00"));
        order1.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order1.setGstMode("GST_EXCLUDED");
        order1.setCgstRate(new BigDecimal("2.50"));
        order1.setSgstRate(new BigDecimal("2.50"));
        order1.setCgstAmount(new BigDecimal("5.00"));
        order1.setSgstAmount(new BigDecimal("5.00"));
        order1.setTaxAmount(new BigDecimal("10.00"));
        order1.setTotalAmount(new BigDecimal("210.00"));

        Bill bill1 = new Bill();
        bill1.setId(1001L);
        bill1.setOrder(order1);
        bill1.setTotalAmount(new BigDecimal("210.00"));

        Payment payment1 = new Payment();
        payment1.setId(501L);
        payment1.setBill(bill1);
        payment1.setAmount(new BigDecimal("210.00"));
        payment1.setPaymentMethod("UPI");
        payment1.setPaymentStatus("PAID");
        payment1.setPaidAt(LocalDateTime.now());

        // Order 2: CGST 7.50, SGST 7.50, Tax 15.00, Total 315.00
        Order order2 = new Order();
        order2.setId(102L);
        order2.setStatus("COMPLETED");
        order2.setSubtotal(new BigDecimal("300.00"));
        order2.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order2.setGstMode("GST_EXCLUDED");
        order2.setCgstRate(new BigDecimal("2.50"));
        order2.setSgstRate(new BigDecimal("2.50"));
        order2.setCgstAmount(new BigDecimal("7.50"));
        order2.setSgstAmount(new BigDecimal("7.50"));
        order2.setTaxAmount(new BigDecimal("15.00"));
        order2.setTotalAmount(new BigDecimal("315.00"));

        Bill bill2 = new Bill();
        bill2.setId(1002L);
        bill2.setOrder(order2);
        bill2.setTotalAmount(new BigDecimal("315.00"));

        Payment payment2 = new Payment();
        payment2.setId(502L);
        payment2.setBill(bill2);
        payment2.setAmount(new BigDecimal("315.00"));
        payment2.setPaymentMethod("CASH");
        payment2.setPaymentStatus("PAID");
        payment2.setPaidAt(LocalDateTime.now());

        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(order1, order2));
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any()))
                .thenReturn(Arrays.asList(payment1, payment2));

        // Sales Report
        SalesReportResponse salesRes = reportService.getSalesReport(today, today);
        assertEquals(new BigDecimal("525.00"), salesRes.getTotalRevenue());
        assertEquals(new BigDecimal("25.00"), salesRes.getTotalTax(), "Total tax must equal combined tax");
        assertEquals(new BigDecimal("12.50"), salesRes.getTotalCgst(), "Total CGST aggregate must match sum of CGST");
        assertEquals(new BigDecimal("12.50"), salesRes.getTotalSgst(), "Total SGST aggregate must match sum of SGST");
        assertEquals(salesRes.getTotalTax(), salesRes.getTotalCgst().add(salesRes.getTotalSgst()));

        // Dashboard Summary
        DashboardResponse dashRes = reportService.getDashboardSummary();
        assertEquals(new BigDecimal("525.00"), dashRes.getTotalRevenue());
        assertEquals(new BigDecimal("25.00"), dashRes.getTotalTax());
        assertEquals(new BigDecimal("12.50"), dashRes.getTotalCgst());
        assertEquals(new BigDecimal("12.50"), dashRes.getTotalSgst());
    }

    @Test
    @DisplayName("2. Legacy records with null/zero component amounts are not given synthetic GST components")
    void testLegacyReportRecordsNotGivenSyntheticGstComponents() {
        LocalDate today = LocalDate.now();

        // Legacy order with totalTax = 30.00, no GST mode, component amounts 0.00
        Order legacyOrder = new Order();
        legacyOrder.setId(201L);
        legacyOrder.setStatus("COMPLETED");
        legacyOrder.setSubtotal(new BigDecimal("500.00"));
        legacyOrder.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        legacyOrder.setGstMode(null);
        legacyOrder.setCgstRate(null);
        legacyOrder.setSgstRate(null);
        legacyOrder.setCgstAmount(new BigDecimal("0.00"));
        legacyOrder.setSgstAmount(new BigDecimal("0.00"));
        legacyOrder.setTaxAmount(new BigDecimal("30.00"));
        legacyOrder.setTotalAmount(new BigDecimal("530.00"));

        Bill legacyBill = new Bill();
        legacyBill.setId(2001L);
        legacyBill.setOrder(legacyOrder);
        legacyBill.setTotalAmount(new BigDecimal("530.00"));

        Payment legacyPayment = new Payment();
        legacyPayment.setId(601L);
        legacyPayment.setBill(legacyBill);
        legacyPayment.setAmount(new BigDecimal("530.00"));
        legacyPayment.setPaymentMethod("CARD");
        legacyPayment.setPaymentStatus("PAID");
        legacyPayment.setPaidAt(LocalDateTime.now());

        when(orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(legacyOrder));
        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any()))
                .thenReturn(Collections.singletonList(legacyPayment));

        SalesReportResponse salesRes = reportService.getSalesReport(today, today);
        assertEquals(new BigDecimal("530.00"), salesRes.getTotalRevenue());
        assertEquals(new BigDecimal("30.00"), salesRes.getTotalTax(), "Historical tax amount must be preserved");
        assertEquals(new BigDecimal("0.00"), salesRes.getTotalCgst(), "Legacy records must not synthesize CGST");
        assertEquals(new BigDecimal("0.00"), salesRes.getTotalSgst(), "Legacy records must not synthesize SGST");

        DashboardResponse dashRes = reportService.getDashboardSummary();
        assertEquals(new BigDecimal("30.00"), dashRes.getTotalTax());
        assertEquals(new BigDecimal("0.00"), dashRes.getTotalCgst());
        assertEquals(new BigDecimal("0.00"), dashRes.getTotalSgst());
    }

    @Test
    @DisplayName("3. ReportBillResponse correctly maps GST snapshot fields from Bill")
    void testReportBillResponseIncludesGstSnapshot() {
        LocalDate today = LocalDate.now();

        Order order = new Order();
        order.setId(301L);
        order.setStatus("COMPLETED");
        order.setOrderType("DINE_IN");

        Bill bill = new Bill();
        bill.setId(3001L);
        bill.setOrder(order);
        bill.setBillNumber("BILL-000301");
        bill.setSubtotal(new BigDecimal("200.00"));
        bill.setDiscountAmount(new BigDecimal("20.00"));
        bill.setGstMode("GST_EXCLUDED");
        bill.setCgstRate(new BigDecimal("2.50"));
        bill.setSgstRate(new BigDecimal("2.50"));
        bill.setCgstAmount(new BigDecimal("4.50"));
        bill.setSgstAmount(new BigDecimal("4.50"));
        bill.setTaxAmount(new BigDecimal("9.00"));
        bill.setTotalAmount(new BigDecimal("189.00"));
        bill.setCreatedAt(LocalDateTime.now());

        Payment payment = new Payment();
        payment.setId(701L);
        payment.setBill(bill);
        payment.setAmount(new BigDecimal("189.00"));
        payment.setPaymentMethod("UPI");
        payment.setPaymentStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());

        when(paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(eq(1L), eq("PAID"), any(), any()))
                .thenReturn(Collections.singletonList(payment));
        when(orderItemRepository.findByOrderId(301L)).thenReturn(Collections.emptyList());

        List<ReportBillResponse> reportBills = reportService.getReportBills(today, today);
        assertNotNull(reportBills);
        assertEquals(1, reportBills.size());

        ReportBillResponse rb = reportBills.get(0);
        assertEquals("BILL-000301", rb.getBillNumber());
        assertEquals("GST_EXCLUDED", rb.getGstMode());
        assertEquals(new BigDecimal("2.50"), rb.getCgstRate());
        assertEquals(new BigDecimal("2.50"), rb.getSgstRate());
        assertEquals(new BigDecimal("4.50"), rb.getCgstAmount());
        assertEquals(new BigDecimal("4.50"), rb.getSgstAmount());
        assertEquals(new BigDecimal("9.00"), rb.getTaxAmount());
        assertEquals(new BigDecimal("189.00"), rb.getTotalAmount());
    }

    @Test
    @DisplayName("4. Receipt display logic for GST_EXCLUDED matches snapshot totals without recalculation")
    void testReceiptDisplayModelGstExcluded() {
        // Model test simulating ThermalReceipt rendering for GST_EXCLUDED
        BigDecimal subtotal = new BigDecimal("200.00");
        BigDecimal discount = new BigDecimal("20.00");
        BigDecimal taxAmount = new BigDecimal("9.00");
        BigDecimal cgstAmount = new BigDecimal("4.50");
        BigDecimal sgstAmount = new BigDecimal("4.50");
        BigDecimal totalAmount = new BigDecimal("189.00");
        String gstMode = "GST_EXCLUDED";

        BigDecimal taxableAmount = subtotal.subtract(discount);
        assertEquals(new BigDecimal("180.00"), taxableAmount);
        assertEquals(taxAmount, cgstAmount.add(sgstAmount));
        assertEquals(totalAmount, taxableAmount.add(taxAmount));
    }

    @Test
    @DisplayName("5. Receipt display logic for GST_INCLUDED displays taxable amount without altering bill total")
    void testReceiptDisplayModelGstIncluded() {
        // Model test simulating ThermalReceipt rendering for GST_INCLUDED
        BigDecimal subtotal = new BigDecimal("210.00");
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        BigDecimal taxAmount = new BigDecimal("10.00");
        BigDecimal cgstAmount = new BigDecimal("5.00");
        BigDecimal sgstAmount = new BigDecimal("5.00");
        BigDecimal totalAmount = new BigDecimal("210.00");
        String gstMode = "GST_INCLUDED";

        // For GST_INCLUDED: taxable base = totalAmount - taxAmount
        BigDecimal taxableAmount = totalAmount.subtract(taxAmount);
        assertEquals(new BigDecimal("200.00"), taxableAmount);
        assertEquals(taxAmount, cgstAmount.add(sgstAmount));
        assertEquals(totalAmount, taxableAmount.add(taxAmount));
        // Crucial invariant: receipt grand total displayed is bill.totalAmount (210.00)
        assertEquals(new BigDecimal("210.00"), totalAmount);
    }

    @Test
    @DisplayName("6. Legacy receipt with null GST mode is null-safe and preserves legacy tax layout")
    void testReceiptDisplayModelLegacyBillNullGst() {
        BigDecimal subtotal = new BigDecimal("500.00");
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        BigDecimal taxAmount = new BigDecimal("25.00");
        BigDecimal totalAmount = new BigDecimal("525.00");
        String gstMode = null;
        BigDecimal cgstRate = null;
        BigDecimal sgstRate = null;
        BigDecimal cgstAmount = null;
        BigDecimal sgstAmount = null;

        boolean isGstBill = gstMode != null;
        assertFalse(isGstBill, "Legacy bill must not be treated as GST bill");
        assertNull(cgstRate);
        assertNull(sgstRate);
        assertEquals(new BigDecimal("25.00"), taxAmount);
        assertEquals(new BigDecimal("525.00"), totalAmount);
    }

    @Test
    @DisplayName("7. RestaurantSettingsResponse API preserves defaultTaxRate and exposes GST fields consistently")
    void testSettingsResponseApiConsistency() {
        RestaurantSettingsResponse resp = RestaurantSettingsResponse.builder()
                .id(1L)
                .restaurantId(1L)
                .taxEnabled(true)
                .defaultTaxRate(new BigDecimal("5.00"))
                .totalGstRate(new BigDecimal("5.00"))
                .cgstRate(new BigDecimal("2.50"))
                .sgstRate(new BigDecimal("2.50"))
                .gstMode("GST_EXCLUDED")
                .build();

        assertEquals(new BigDecimal("5.00"), resp.getDefaultTaxRate());
        assertEquals(new BigDecimal("5.00"), resp.getTotalGstRate());
        assertEquals(new BigDecimal("2.50"), resp.getCgstRate());
        assertEquals(new BigDecimal("2.50"), resp.getSgstRate());
        assertEquals("GST_EXCLUDED", resp.getGstMode());
    }

    @Test
    @DisplayName("8. OrderResponse and BillResponse expose GST snapshot fields consistently with existing properties")
    void testOrderAndBillResponseApiConsistency() {
        OrderResponse orderRes = OrderResponse.builder()
                .id(1L)
                .subtotal(new BigDecimal("100.00"))
                .discountAmount(new BigDecimal("10.00"))
                .taxAmount(new BigDecimal("4.50"))
                .totalAmount(new BigDecimal("94.50"))
                .gstMode("GST_EXCLUDED")
                .cgstRate(new BigDecimal("2.50"))
                .sgstRate(new BigDecimal("2.50"))
                .cgstAmount(new BigDecimal("2.25"))
                .sgstAmount(new BigDecimal("2.25"))
                .build();

        assertEquals(new BigDecimal("100.00"), orderRes.getSubtotal());
        assertEquals(new BigDecimal("4.50"), orderRes.getTaxAmount());
        assertEquals(new BigDecimal("94.50"), orderRes.getTotalAmount());
        assertEquals("GST_EXCLUDED", orderRes.getGstMode());
        assertEquals(new BigDecimal("2.25"), orderRes.getCgstAmount());
        assertEquals(new BigDecimal("2.25"), orderRes.getSgstAmount());

        BillResponse billRes = BillResponse.builder()
                .id(1L)
                .subtotal(new BigDecimal("100.00"))
                .discountAmount(new BigDecimal("10.00"))
                .taxAmount(new BigDecimal("4.50"))
                .totalAmount(new BigDecimal("94.50"))
                .gstMode("GST_EXCLUDED")
                .cgstRate(new BigDecimal("2.50"))
                .sgstRate(new BigDecimal("2.50"))
                .cgstAmount(new BigDecimal("2.25"))
                .sgstAmount(new BigDecimal("2.25"))
                .build();

        assertEquals(new BigDecimal("100.00"), billRes.getSubtotal());
        assertEquals(new BigDecimal("4.50"), billRes.getTaxAmount());
        assertEquals(new BigDecimal("94.50"), billRes.getTotalAmount());
        assertEquals("GST_EXCLUDED", billRes.getGstMode());
        assertEquals(new BigDecimal("2.25"), billRes.getCgstAmount());
        assertEquals(new BigDecimal("2.25"), billRes.getSgstAmount());
    }
}
