package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.BillRequest;
import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.OrderWorkflowConflictException;
import com.restaurant.restaurantpos.repository.BillRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;

@ExtendWith(MockitoExtension.class)
public class ConsecutiveTableBillingRegressionTest {

    @Mock private BillRepository billRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private UserRepository userRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantSettingsRepository settingsRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private KitchenOrderEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;

    private BillService billService;
    private OrderService orderService;

    private Restaurant restaurant;
    private User adminUser;
    private RestaurantTable tableA;
    private RestaurantTable tableB;
    private RestaurantTable tableC;
    private Order orderA;
    private Order orderB;
    private Order orderC;

    private final Map<Long, Bill> billStorage = new HashMap<>();
    private final AtomicLong billIdSeq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        billService = new BillService(
                billRepository,
                paymentRepository,
                orderRepository,
                orderStatusHistoryRepository,
                tableRepository,
                userRepository,
                securityUtils,
                eventPublisher,
                notificationService,
                settingsRepository
        );

        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                orderStatusHistoryRepository,
                restaurantRepository,
                tableRepository,
                null,
                userRepository,
                null,
                securityUtils,
                eventPublisher,
                notificationService,
                billRepository,
                settingsRepository
        );

        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Grand Spice Garden");

        adminUser = new User();
        adminUser.setId(10L);
        adminUser.setName("Admin Manager");
        adminUser.setRestaurant(restaurant);

        // Table A setup
        tableA = new RestaurantTable();
        tableA.setId(1L);
        tableA.setTableNumber(1);
        tableA.setCapacity(4);
        tableA.setStatus("OCCUPIED");
        tableA.setRestaurant(restaurant);

        // Table B setup
        tableB = new RestaurantTable();
        tableB.setId(2L);
        tableB.setTableNumber(2);
        tableB.setCapacity(2);
        tableB.setStatus("OCCUPIED");
        tableB.setRestaurant(restaurant);

        // Table C setup
        tableC = new RestaurantTable();
        tableC.setId(3L);
        tableC.setTableNumber(3);
        tableC.setCapacity(6);
        tableC.setStatus("OCCUPIED");
        tableC.setRestaurant(restaurant);

        // Order A (for Table A)
        orderA = new Order();
        orderA.setId(501L);
        orderA.setRestaurant(restaurant);
        orderA.setTable(tableA);
        orderA.setOrderType("DINE_IN");
        orderA.setStatus("READY");
        orderA.setSubtotal(new BigDecimal("120.00"));
        orderA.setTaxAmount(new BigDecimal("6.00"));
        orderA.setDiscountAmount(BigDecimal.ZERO);
        orderA.setTotalAmount(new BigDecimal("126.00"));

        // Order B (for Table B)
        orderB = new Order();
        orderB.setId(502L);
        orderB.setRestaurant(restaurant);
        orderB.setTable(tableB);
        orderB.setOrderType("DINE_IN");
        orderB.setStatus("READY");
        orderB.setSubtotal(new BigDecimal("80.00"));
        orderB.setTaxAmount(new BigDecimal("4.00"));
        orderB.setDiscountAmount(BigDecimal.ZERO);
        orderB.setTotalAmount(new BigDecimal("84.00"));

        // Order C (for Table C)
        orderC = new Order();
        orderC.setId(503L);
        orderC.setRestaurant(restaurant);
        orderC.setTable(tableC);
        orderC.setOrderType("DINE_IN");
        orderC.setStatus("READY");
        orderC.setSubtotal(new BigDecimal("200.00"));
        orderC.setTaxAmount(new BigDecimal("10.00"));
        orderC.setDiscountAmount(BigDecimal.ZERO);
        orderC.setTotalAmount(new BigDecimal("210.00"));

        billStorage.clear();

        // Standard admin security mocks
        lenient().when(securityUtils.isAdmin()).thenReturn(true);
        lenient().when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        lenient().when(securityUtils.getCurrentUserId()).thenReturn(10L);
        lenient().when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        lenient().when(userRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(adminUser));
        lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));

        // Save bill mock that tracks bills generated in session
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill b = invocation.getArgument(0);
            if (b.getId() == null) {
                b.setId(billIdSeq.incrementAndGet());
            }
            if (b.getOrder() != null) {
                billStorage.put(b.getOrder().getId(), b);
            }
            return b;
        });

        // Bill existence check based on in-session storage
        lenient().when(billRepository.existsByOrderId(anyLong())).thenAnswer(invocation -> {
            Long orderId = invocation.getArgument(0);
            return billStorage.containsKey(orderId);
        });

        // Bill lookup by restaurant and order id
        lenient().when(billRepository.findByRestaurantIdAndOrderId(eq(1L), anyLong())).thenAnswer(invocation -> {
            Long orderId = invocation.getArgument(1);
            return Optional.ofNullable(billStorage.get(orderId));
        });

        // Top bill for sequential bill number generation
        lenient().when(billRepository.findTopByRestaurantIdOrderByIdDesc(1L)).thenAnswer(invocation -> {
            return billStorage.values().stream()
                    .max((b1, b2) -> Long.compare(b1.getId(), b2.getId()));
        });

        // Save table mock updates table status
        lenient().when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Order lookup mocks
        lenient().when(orderRepository.findByIdAndRestaurantId(501L, 1L)).thenReturn(Optional.of(orderA));
        lenient().when(orderRepository.findById(501L)).thenReturn(Optional.of(orderA));
        lenient().when(orderRepository.findByIdAndRestaurantId(502L, 1L)).thenReturn(Optional.of(orderB));
        lenient().when(orderRepository.findById(502L)).thenReturn(Optional.of(orderB));
        lenient().when(orderRepository.findByIdAndRestaurantId(503L, 1L)).thenReturn(Optional.of(orderC));
        lenient().when(orderRepository.findById(503L)).thenReturn(Optional.of(orderC));

        // Table lookup mocks
        lenient().when(tableRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(tableA));
        lenient().when(tableRepository.findByIdAndRestaurantId(2L, 1L)).thenReturn(Optional.of(tableB));
        lenient().when(tableRepository.findByIdAndRestaurantId(3L, 1L)).thenReturn(Optional.of(tableC));

        // Active order query mocks for table selection
        lenient().when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(1L), eq("DINE_IN"), any()
        )).thenReturn(Optional.of(orderA));

        lenient().when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(2L), eq("DINE_IN"), any()
        )).thenReturn(Optional.of(orderB));

        lenient().when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(3L), eq("DINE_IN"), any()
        )).thenReturn(Optional.of(orderC));
    }

    @Test
    @DisplayName("Regression: Generate bill for Table A, then immediately select and bill Table B without page reload (KDS enabled)")
    void testConsecutiveBillingTableAThenTableB_KdsEnabled() {
        // Given KDS is enabled
        RestaurantSettings settings = new RestaurantSettings();
        settings.setKitchenEnabled(true);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        // 1. Generate bill for Table A
        BillRequest requestA = new BillRequest(orderA.getId());
        BillResponse billA = billService.createBill(requestA);

        assertNotNull(billA, "Bill A must be generated successfully");
        assertEquals(orderA.getId(), billA.getOrderId(), "Bill A must belong to Order A");
        assertEquals(new BigDecimal("126.00"), billA.getTotalAmount());
        assertEquals("BILLING", tableA.getStatus(), "Table A status must become BILLING");
        assertTrue(billStorage.containsKey(orderA.getId()), "Bill A must be tracked in storage");

        // 2. Without refreshing the page / resetting session, select Table B via active order lookup
        OrderResponse activeOrderB = orderService.getActiveOrderByTable(tableB.getId());
        assertNotNull(activeOrderB, "Active order for Table B must be found");
        assertEquals(orderB.getId(), activeOrderB.getId(), "Table B's active order must be Order B");
        assertEquals(tableB.getId(), activeOrderB.getTableId());

        // 3. Generate bill for Table B immediately in the same session
        BillRequest requestB = new BillRequest(orderB.getId());
        BillResponse billB = billService.createBill(requestB);

        assertNotNull(billB, "Bill B must be generated successfully without page reload");
        assertEquals(orderB.getId(), billB.getOrderId(), "Bill B must belong to Order B, not Order A");
        assertEquals(new BigDecimal("84.00"), billB.getTotalAmount());
        assertEquals("BILLING", tableB.getStatus(), "Table B status must become BILLING");
        assertTrue(billStorage.containsKey(orderB.getId()), "Bill B must be tracked in storage");

        // 4. Verify isolation and correctness between Table A and Table B bills
        assertNotEquals(billA.getId(), billB.getId(), "Bill A and Bill B must have distinct IDs");
        assertNotEquals(billA.getBillNumber(), billB.getBillNumber(), "Bill numbers must be distinct");
        assertNotEquals(billA.getOrderId(), billB.getOrderId(), "Bills must reference different orders");
    }

    @Test
    @DisplayName("Regression: Consecutive billing for Table A then Table B in KDS-disabled mode (non-READY orders)")
    void testConsecutiveBillingTableAThenTableB_KdsDisabled() {
        // Given KDS is disabled (kitchen-free mode)
        RestaurantSettings settings = new RestaurantSettings();
        settings.setKitchenEnabled(false);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        // In KDS-disabled mode, orders can be billed when in NEW or ACCEPTED status
        orderA.setStatus("NEW");
        orderB.setStatus("ACCEPTED");

        // 1. Generate bill for Table A
        BillRequest requestA = new BillRequest(orderA.getId());
        BillResponse billA = billService.createBill(requestA);

        assertNotNull(billA);
        assertEquals(orderA.getId(), billA.getOrderId());
        assertEquals("BILLING", tableA.getStatus());

        // 2. Immediately select and bill Table B without reload
        BillRequest requestB = new BillRequest(orderB.getId());
        BillResponse billB = billService.createBill(requestB);

        assertNotNull(billB);
        assertEquals(orderB.getId(), billB.getOrderId());
        assertEquals("BILLING", tableB.getStatus());

        // Both tables are now in BILLING status with independent bills
        assertEquals("BILLING", tableA.getStatus());
        assertEquals("BILLING", tableB.getStatus());
        assertNotEquals(billA.getId(), billB.getId());
    }

    @Test
    @DisplayName("Regression: Ineligible Table A fails cleanly and does not block Table B from generating its bill")
    void testIneligibleTableAFailsWithoutBlockingTableB() {
        // Given KDS is enabled
        RestaurantSettings settings = new RestaurantSettings();
        settings.setKitchenEnabled(true);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        // Table A's order is still in PREPARING (not eligible yet)
        orderA.setStatus("PREPARING");
        // Table B's order is READY (eligible)
        orderB.setStatus("READY");

        // 1. Attempting to bill Table A must throw clear error
        BillRequest requestA = new BillRequest(orderA.getId());
        OrderWorkflowConflictException ex = assertThrows(
                OrderWorkflowConflictException.class,
                () -> billService.createBill(requestA)
        );
        assertTrue(ex.getMessage().contains("Order is not in READY status"));
        assertNotEquals("BILLING", tableA.getStatus(), "Table A status must not change on failure");

        // 2. Selecting and billing eligible Table B immediately in the same session must succeed
        BillRequest requestB = new BillRequest(orderB.getId());
        BillResponse billB = billService.createBill(requestB);

        assertNotNull(billB, "Table B billing must succeed despite Table A failure");
        assertEquals(orderB.getId(), billB.getOrderId());
        assertEquals("BILLING", tableB.getStatus());
    }

    @Test
    @DisplayName("Regression: Duplicate bill submission for Table A is rejected while Table B can still be billed")
    void testDuplicateBillForTableARejectedWhileTableBRemainsBillable() {
        RestaurantSettings settings = new RestaurantSettings();
        settings.setKitchenEnabled(true);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        // 1. Generate bill for Table A
        BillResponse billA = billService.createBill(new BillRequest(orderA.getId()));
        assertNotNull(billA);

        // 2. Duplicate submission for Table A must fail
        OrderWorkflowConflictException dupEx = assertThrows(
                OrderWorkflowConflictException.class,
                () -> billService.createBill(new BillRequest(orderA.getId()))
        );
        assertTrue(dupEx.getMessage().contains("already exists"));

        // 3. Table B can still be billed immediately without interference
        BillResponse billB = billService.createBill(new BillRequest(orderB.getId()));
        assertNotNull(billB);
        assertEquals(orderB.getId(), billB.getOrderId());
    }

    @Test
    @DisplayName("Regression: Chain billing across Table A -> Table B -> Table C consecutively in single session")
    void testConsecutiveChainBilling_ThreeTables() {
        RestaurantSettings settings = new RestaurantSettings();
        settings.setKitchenEnabled(true);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        // Bill Table A
        BillResponse billA = billService.createBill(new BillRequest(orderA.getId()));
        assertEquals(orderA.getId(), billA.getOrderId());
        assertEquals("BILLING", tableA.getStatus());

        // Bill Table B
        BillResponse billB = billService.createBill(new BillRequest(orderB.getId()));
        assertEquals(orderB.getId(), billB.getOrderId());
        assertEquals("BILLING", tableB.getStatus());

        // Bill Table C
        BillResponse billC = billService.createBill(new BillRequest(orderC.getId()));
        assertEquals(orderC.getId(), billC.getOrderId());
        assertEquals("BILLING", tableC.getStatus());

        // Verify all 3 bills are unique and assigned correctly
        assertEquals(3, billStorage.size());
        assertEquals("BILLING", tableA.getStatus());
        assertEquals("BILLING", tableB.getStatus());
        assertEquals("BILLING", tableC.getStatus());
    }
}
