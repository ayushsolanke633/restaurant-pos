package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Restaurant;
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
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminBillSortingTest {

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

    private Restaurant mockRestaurant;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockRestaurant = new Restaurant();
        mockRestaurant.setId(1L);

        mockUser = new User();
        mockUser.setId(10L);
        mockUser.setName("Admin User");
    }

    @Test
    @DisplayName("getAllBills returns bills in reverse chronological order (newest first, createdAt DESC)")
    void getAllBills_ReturnsNewestFirst_UsingCreatedAtDesc() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDateTime now = LocalDateTime.now();

        // Bill A: ID = 100, Order ID = 500, created 2 hours ago
        Bill billA = new Bill();
        billA.setId(100L);
        billA.setRestaurant(mockRestaurant);
        billA.setBillNumber("BILL-000001");
        billA.setSubtotal(new BigDecimal("100.00"));
        billA.setTaxAmount(BigDecimal.ZERO);
        billA.setDiscountAmount(BigDecimal.ZERO);
        billA.setTotalAmount(new BigDecimal("100.00"));
        billA.setGeneratedBy(mockUser);
        billA.setCreatedAt(now.minusHours(2));

        // Bill B: ID = 20, Order ID = 15, created 10 minutes ago (NEWER than A)
        Bill billB = new Bill();
        billB.setId(20L);
        billB.setRestaurant(mockRestaurant);
        billB.setBillNumber("BILL-000002");
        billB.setSubtotal(new BigDecimal("200.00"));
        billB.setTaxAmount(BigDecimal.ZERO);
        billB.setDiscountAmount(BigDecimal.ZERO);
        billB.setTotalAmount(new BigDecimal("200.00"));
        billB.setGeneratedBy(mockUser);
        billB.setCreatedAt(now.minusMinutes(10));

        // Mock repository returning Bill B first, then Bill A (createdAt DESC)
        when(billRepository.findByRestaurantIdOrderByCreatedAtDesc(1L))
                .thenReturn(Arrays.asList(billB, billA));

        List<BillResponse> result = billService.getAllBills();

        assertNotNull(result);
        assertEquals(2, result.size());
        // First item must be Bill B (ID 20, created 10 mins ago)
        assertEquals(20L, result.get(0).getId());
        assertEquals("BILL-000002", result.get(0).getBillNumber());
        assertEquals(now.minusMinutes(10), result.get(0).getCreatedAt());

        // Second item must be Bill A (ID 100, created 2 hours ago)
        assertEquals(100L, result.get(1).getId());
        assertEquals("BILL-000001", result.get(1).getBillNumber());
        assertEquals(now.minusHours(2), result.get(1).getCreatedAt());

        verify(billRepository).findByRestaurantIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("Bill ID and Order ID do NOT dictate sorting order")
    void getAllBills_IdDoesNotDictateSorting() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        LocalDateTime now = LocalDateTime.now();

        // Bill 1: High ID (999), created yesterday
        Bill oldBillHighId = new Bill();
        oldBillHighId.setId(999L);
        oldBillHighId.setRestaurant(mockRestaurant);
        oldBillHighId.setBillNumber("BILL-000001");
        oldBillHighId.setSubtotal(BigDecimal.TEN);
        oldBillHighId.setTaxAmount(BigDecimal.ZERO);
        oldBillHighId.setDiscountAmount(BigDecimal.ZERO);
        oldBillHighId.setTotalAmount(BigDecimal.TEN);
        oldBillHighId.setCreatedAt(now.minusDays(1));

        // Bill 2: Low ID (3), created today
        Bill newBillLowId = new Bill();
        newBillLowId.setId(3L);
        newBillLowId.setRestaurant(mockRestaurant);
        newBillLowId.setBillNumber("BILL-000002");
        newBillLowId.setSubtotal(BigDecimal.TEN);
        newBillLowId.setTaxAmount(BigDecimal.ZERO);
        newBillLowId.setDiscountAmount(BigDecimal.ZERO);
        newBillLowId.setTotalAmount(BigDecimal.TEN);
        newBillLowId.setCreatedAt(now);

        when(billRepository.findByRestaurantIdOrderByCreatedAtDesc(1L))
                .thenReturn(Arrays.asList(newBillLowId, oldBillHighId));

        List<BillResponse> result = billService.getAllBills();

        assertEquals(3L, result.get(0).getId());
        assertEquals(999L, result.get(1).getId());
    }

    @Test
    @DisplayName("Tenant isolation remains intact when fetching bills")
    void getAllBills_PreservesTenantIsolation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);

        when(billRepository.findByRestaurantIdOrderByCreatedAtDesc(2L))
                .thenReturn(Collections.emptyList());

        List<BillResponse> result = billService.getAllBills();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(billRepository).findByRestaurantIdOrderByCreatedAtDesc(2L);
        verify(billRepository, never()).findByRestaurantIdOrderByCreatedAtDesc(1L);
    }
}
