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
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.PaymentRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.BillService;
import com.restaurant.restaurantpos.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Stage3D3BillingGstSnapshotUnitTest {

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

    @Mock
    private RestaurantSettingsRepository settingsRepository;

    @InjectMocks
    private BillService billService;

    private Restaurant restaurant;
    private RestaurantTable table;
    private User adminUser;
    private RestaurantSettings settings;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Dhaba Central");

        table = new RestaurantTable();
        table.setId(10L);
        table.setTableNumber(5);
        table.setRestaurant(restaurant);
        table.setStatus("AVAILABLE");

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
        settings.setKitchenEnabled(true);

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(userRepository.findByIdAndRestaurantId(99L, 1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill b = invocation.getArgument(0);
            if (b.getId() == null) {
                b.setId(5001L);
            }
            if (b.getCreatedAt() == null) {
                b.setCreatedAt(LocalDateTime.now());
            }
            return b;
        });
    }

    @Test
    @DisplayName("1. Bill generation captures GST_EXCLUDED snapshot from order")
    void testGstExcludedBillSnapshot() {
        Order order = new Order();
        order.setId(101L);
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setOrderType("DINE_IN");
        order.setStatus("READY");
        order.setSubtotal(new BigDecimal("200.00"));
        order.setDiscountAmount(new BigDecimal("20.00"));
        order.setGstMode("GST_EXCLUDED");
        order.setCgstRate(new BigDecimal("2.50"));
        order.setSgstRate(new BigDecimal("2.50"));
        order.setCgstAmount(new BigDecimal("4.50"));
        order.setSgstAmount(new BigDecimal("4.50"));
        order.setTaxAmount(new BigDecimal("9.00"));
        order.setTotalAmount(new BigDecimal("189.00"));

        when(orderRepository.findByIdAndRestaurantId(101L, 1L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(101L)).thenReturn(false);
        when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());

        BillRequest request = new BillRequest(101L);
        BillResponse response = billService.createBill(request);

        assertNotNull(response);
        assertEquals(101L, response.getOrderId());
        assertEquals("BILL-000001", response.getBillNumber());
        assertEquals(new BigDecimal("200.00"), response.getSubtotal());
        assertEquals(new BigDecimal("20.00"), response.getDiscountAmount());
        assertEquals("GST_EXCLUDED", response.getGstMode());
        assertEquals(new BigDecimal("2.50"), response.getCgstRate());
        assertEquals(new BigDecimal("2.50"), response.getSgstRate());
        assertEquals(new BigDecimal("4.50"), response.getCgstAmount());
        assertEquals(new BigDecimal("4.50"), response.getSgstAmount());
        assertEquals(new BigDecimal("9.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("189.00"), response.getTotalAmount());

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).save(captor.capture());
        Bill saved = captor.getValue();
        assertEquals("GST_EXCLUDED", saved.getGstMode());
        assertEquals(new BigDecimal("4.50"), saved.getCgstAmount());
        assertEquals(new BigDecimal("4.50"), saved.getSgstAmount());
        assertEquals(new BigDecimal("9.00"), saved.getTaxAmount());
        assertEquals(new BigDecimal("189.00"), saved.getTotalAmount());
    }

    @Test
    @DisplayName("2. Bill generation captures GST_INCLUDED snapshot from order")
    void testGstIncludedBillSnapshot() {
        Order order = new Order();
        order.setId(102L);
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setOrderType("DINE_IN");
        order.setStatus("READY");
        order.setSubtotal(new BigDecimal("210.00"));
        order.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setGstMode("GST_INCLUDED");
        order.setCgstRate(new BigDecimal("2.50"));
        order.setSgstRate(new BigDecimal("2.50"));
        order.setCgstAmount(new BigDecimal("5.00"));
        order.setSgstAmount(new BigDecimal("5.00"));
        order.setTaxAmount(new BigDecimal("10.00"));
        order.setTotalAmount(new BigDecimal("210.00"));

        when(orderRepository.findByIdAndRestaurantId(102L, 1L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(102L)).thenReturn(false);
        when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());

        BillRequest request = new BillRequest(102L);
        BillResponse response = billService.createBill(request);

        assertNotNull(response);
        assertEquals("GST_INCLUDED", response.getGstMode());
        assertEquals(new BigDecimal("2.50"), response.getCgstRate());
        assertEquals(new BigDecimal("2.50"), response.getSgstRate());
        assertEquals(new BigDecimal("5.00"), response.getCgstAmount());
        assertEquals(new BigDecimal("5.00"), response.getSgstAmount());
        assertEquals(new BigDecimal("10.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("210.00"), response.getTotalAmount());
    }

    @Test
    @DisplayName("3. Exact equality between component tax amounts and combined tax on bill")
    void testExactEqualityComponentTaxAndCombinedTax() {
        Order order = new Order();
        order.setId(103L);
        order.setRestaurant(restaurant);
        order.setStatus("READY");
        order.setSubtotal(new BigDecimal("100.00"));
        order.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setGstMode("GST_EXCLUDED");
        order.setCgstRate(new BigDecimal("2.53"));
        order.setSgstRate(new BigDecimal("2.52"));
        order.setCgstAmount(new BigDecimal("2.53"));
        order.setSgstAmount(new BigDecimal("2.52"));
        order.setTaxAmount(new BigDecimal("5.05"));
        order.setTotalAmount(new BigDecimal("105.05"));

        when(orderRepository.findByIdAndRestaurantId(103L, 1L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(103L)).thenReturn(false);
        when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());

        BillResponse response = billService.createBill(new BillRequest(103L));

        assertEquals(new BigDecimal("5.05"), response.getTaxAmount());
        assertEquals(response.getTaxAmount(), response.getCgstAmount().add(response.getSgstAmount()));
    }

    @Test
    @DisplayName("4. Bill totals and GST fields remain unchanged after order and settings changes")
    void testBillTotalsRemainUnchangedAfterOrderAndSettingsChanges() {
        Order order = new Order();
        order.setId(104L);
        order.setRestaurant(restaurant);
        order.setStatus("READY");
        order.setSubtotal(new BigDecimal("200.00"));
        order.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setGstMode("GST_EXCLUDED");
        order.setCgstRate(new BigDecimal("2.50"));
        order.setSgstRate(new BigDecimal("2.50"));
        order.setCgstAmount(new BigDecimal("5.00"));
        order.setSgstAmount(new BigDecimal("5.00"));
        order.setTaxAmount(new BigDecimal("10.00"));
        order.setTotalAmount(new BigDecimal("210.00"));

        when(orderRepository.findByIdAndRestaurantId(104L, 1L)).thenReturn(Optional.of(order));
        when(billRepository.existsByOrderId(104L)).thenReturn(false);
        when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());

        BillResponse createdBill = billService.createBill(new BillRequest(104L));
        assertEquals(new BigDecimal("210.00"), createdBill.getTotalAmount());
        assertEquals(new BigDecimal("10.00"), createdBill.getTaxAmount());

        // Construct persisted bill entity as saved in DB
        Bill persistedBill = new Bill();
        persistedBill.setId(5001L);
        persistedBill.setRestaurant(restaurant);
        persistedBill.setOrder(order);
        persistedBill.setBillNumber("BILL-000001");
        persistedBill.setSubtotal(new BigDecimal("200.00"));
        persistedBill.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        persistedBill.setTaxAmount(new BigDecimal("10.00"));
        persistedBill.setTotalAmount(new BigDecimal("210.00"));
        persistedBill.setGstMode("GST_EXCLUDED");
        persistedBill.setCgstRate(new BigDecimal("2.50"));
        persistedBill.setSgstRate(new BigDecimal("2.50"));
        persistedBill.setCgstAmount(new BigDecimal("5.00"));
        persistedBill.setSgstAmount(new BigDecimal("5.00"));
        persistedBill.setGeneratedBy(adminUser);
        persistedBill.setCreatedAt(LocalDateTime.now());

        when(billRepository.findByIdAndRestaurantId(5001L, 1L)).thenReturn(Optional.of(persistedBill));

        // Simulate subsequent changes to order and restaurant settings
        order.setTotalAmount(new BigDecimal("999.00"));
        order.setTaxAmount(new BigDecimal("99.00"));
        order.setGstMode("GST_INCLUDED");
        settings.setDefaultTaxRate(new BigDecimal("18.00"));
        settings.setTaxEnabled(false);

        // Fetch bill again
        BillResponse fetched = billService.getBillById(5001L);

        assertEquals(new BigDecimal("210.00"), fetched.getTotalAmount(), "Bill total amount must remain immutable");
        assertEquals(new BigDecimal("10.00"), fetched.getTaxAmount(), "Bill tax amount must remain immutable");
        assertEquals("GST_EXCLUDED", fetched.getGstMode(), "Bill GST mode must remain immutable");
        assertEquals(new BigDecimal("2.50"), fetched.getCgstRate());
        assertEquals(new BigDecimal("2.50"), fetched.getSgstRate());
        assertEquals(new BigDecimal("5.00"), fetched.getCgstAmount());
        assertEquals(new BigDecimal("5.00"), fetched.getSgstAmount());
    }

    @Test
    @DisplayName("5. Legacy bill with null GST mode/rates remains unchanged and safe")
    void testLegacyBillWithNullGstModeRemainsUnchangedAndSafe() {
        Order legacyOrder = new Order();
        legacyOrder.setId(105L);
        legacyOrder.setRestaurant(restaurant);
        legacyOrder.setStatus("COMPLETED");

        Bill legacyBill = new Bill();
        legacyBill.setId(7001L);
        legacyBill.setRestaurant(restaurant);
        legacyBill.setOrder(legacyOrder);
        legacyBill.setBillNumber("BILL-000050");
        legacyBill.setSubtotal(new BigDecimal("500.00"));
        legacyBill.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        legacyBill.setTaxAmount(new BigDecimal("25.00"));
        legacyBill.setTotalAmount(new BigDecimal("525.00"));
        legacyBill.setGstMode(null);
        legacyBill.setCgstRate(null);
        legacyBill.setSgstRate(null);
        legacyBill.setCgstAmount(new BigDecimal("0.00"));
        legacyBill.setSgstAmount(new BigDecimal("0.00"));
        legacyBill.setGeneratedBy(adminUser);
        legacyBill.setCreatedAt(LocalDateTime.now().minusDays(10));

        when(billRepository.findByIdAndRestaurantId(7001L, 1L)).thenReturn(Optional.of(legacyBill));
        when(orderRepository.findByIdAndRestaurantId(105L, 1L)).thenReturn(Optional.of(legacyOrder));
        when(billRepository.findByRestaurantIdAndOrderId(1L, 105L)).thenReturn(Optional.of(legacyBill));

        BillResponse byId = billService.getBillById(7001L);
        assertNull(byId.getGstMode(), "Legacy bill must retain null gstMode");
        assertNull(byId.getCgstRate(), "Legacy bill must retain null cgstRate");
        assertNull(byId.getSgstRate(), "Legacy bill must retain null sgstRate");
        assertEquals(new BigDecimal("25.00"), byId.getTaxAmount());
        assertEquals(new BigDecimal("525.00"), byId.getTotalAmount());

        BillResponse byOrderId = billService.getBillByOrderId(105L);
        assertNull(byOrderId.getGstMode());
        assertNull(byOrderId.getCgstRate());
        assertNull(byOrderId.getSgstRate());
        assertEquals(new BigDecimal("25.00"), byOrderId.getTaxAmount());
        assertEquals(new BigDecimal("525.00"), byOrderId.getTotalAmount());
    }

    @Test
    @DisplayName("6. Legacy order snapshot produces bill with null GST mode and rates")
    void testLegacyOrderSnapshotProducesNullGstModeAndRates() {
        Order legacyOrder = new Order();
        legacyOrder.setId(106L);
        legacyOrder.setRestaurant(restaurant);
        legacyOrder.setStatus("READY");
        legacyOrder.setSubtotal(new BigDecimal("150.00"));
        legacyOrder.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        legacyOrder.setTaxAmount(new BigDecimal("15.00"));
        legacyOrder.setTotalAmount(new BigDecimal("165.00"));
        legacyOrder.setGstMode(null);
        legacyOrder.setCgstRate(null);
        legacyOrder.setSgstRate(null);
        legacyOrder.setCgstAmount(new BigDecimal("0.00"));
        legacyOrder.setSgstAmount(new BigDecimal("0.00"));

        when(orderRepository.findByIdAndRestaurantId(106L, 1L)).thenReturn(Optional.of(legacyOrder));
        when(billRepository.existsByOrderId(106L)).thenReturn(false);
        when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenReturn(Optional.empty());

        BillResponse response = billService.createBill(new BillRequest(106L));

        assertNotNull(response);
        assertNull(response.getGstMode(), "Created bill must retain null gstMode for legacy order");
        assertNull(response.getCgstRate(), "Created bill must retain null cgstRate for legacy order");
        assertNull(response.getSgstRate(), "Created bill must retain null sgstRate for legacy order");
        assertEquals(new BigDecimal("15.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("165.00"), response.getTotalAmount());
    }

    @Test
    @DisplayName("7. Payment workflow preserved with GST bill")
    void testPaymentWorkflowPreservedWithGstBill() {
        Order order = new Order();
        order.setId(107L);
        order.setRestaurant(restaurant);
        order.setStatus("READY");

        Bill bill = new Bill();
        bill.setId(8001L);
        bill.setRestaurant(restaurant);
        bill.setOrder(order);
        bill.setBillNumber("BILL-000088");
        bill.setSubtotal(new BigDecimal("200.00"));
        bill.setTaxAmount(new BigDecimal("10.00"));
        bill.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        bill.setTotalAmount(new BigDecimal("210.00"));
        bill.setGstMode("GST_EXCLUDED");
        bill.setCgstRate(new BigDecimal("2.50"));
        bill.setSgstRate(new BigDecimal("2.50"));
        bill.setCgstAmount(new BigDecimal("5.00"));
        bill.setSgstAmount(new BigDecimal("5.00"));

        when(billRepository.findByIdAndRestaurantId(8001L, 1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndPaymentStatus(8001L, "PAID")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(9001L);
            return p;
        });

        // 1. Invalid payment amount rejected
        PaymentRequest invalidReq = new PaymentRequest(new BigDecimal("200.00"), "UPI", "TXN-123");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> billService.processPayment(8001L, invalidReq));
        assertEquals("Payment amount must equal bill total amount", ex.getMessage());

        // 2. Exact payment succeeds
        PaymentRequest validReq = new PaymentRequest(new BigDecimal("210.00"), "UPI", "TXN-123");
        PaymentResponse payRes = billService.processPayment(8001L, validReq);

        assertNotNull(payRes);
        assertEquals(new BigDecimal("210.00"), payRes.getAmount());
        assertEquals("PAID", payRes.getPaymentStatus());
        assertEquals("COMPLETED", order.getStatus());
        verify(orderRepository).save(order);
        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
        verify(eventPublisher).publishOrderStatusChanged(order);
        verify(notificationService).notifyPaymentCompleted(eq(order), any(Payment.class));
    }

    @Test
    @DisplayName("8. Non-admin users cannot create bills or process payments")
    void testNonAdminCannotCreateBillOrProcessPayment() {
        when(securityUtils.isAdmin()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> billService.createBill(new BillRequest(101L)));
        assertThrows(AccessDeniedException.class, () -> billService.processPayment(8001L, new PaymentRequest(new BigDecimal("100.00"), "CASH", null)));
        assertThrows(AccessDeniedException.class, () -> billService.getAllBills());
        assertThrows(AccessDeniedException.class, () -> billService.getBillById(8001L));
        assertThrows(AccessDeniedException.class, () -> billService.getBillByOrderId(101L));
    }
}
