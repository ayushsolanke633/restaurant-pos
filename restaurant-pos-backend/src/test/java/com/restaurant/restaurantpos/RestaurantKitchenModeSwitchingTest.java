package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.BillRequest;
import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.PaymentRequest;
import com.restaurant.restaurantpos.dto.PaymentResponse;
import com.restaurant.restaurantpos.dto.RestaurantSettingsRequest;
import com.restaurant.restaurantpos.dto.RestaurantSettingsResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Payment;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.exception.KitchenWorkflowDisabledException;
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
import com.restaurant.restaurantpos.service.KitchenService;
import com.restaurant.restaurantpos.service.NotificationService;
import com.restaurant.restaurantpos.service.OrderService;
import com.restaurant.restaurantpos.service.RestaurantSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RestaurantKitchenModeSwitchingTest {

    @Mock private RestaurantSettingsRepository settingsRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private UserRepository userRepository;
    @Mock private BillRepository billRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private KitchenOrderEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;

    private RestaurantSettingsService settingsService;
    private BillService billService;
    private KitchenService kitchenService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private User adminA;
    private User waiterA;
    private User kitchenA;
    private RestaurantSettings settingsA;
    private RestaurantSettings settingsB;
    private RestaurantTable table1;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Spice Garden");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Ocean Breeze");

        adminA = new User();
        adminA.setId(10L);
        adminA.setName("Admin A");
        adminA.setRestaurant(restaurantA);

        waiterA = new User();
        waiterA.setId(20L);
        waiterA.setName("Waiter A");
        waiterA.setRestaurant(restaurantA);

        kitchenA = new User();
        kitchenA.setId(30L);
        kitchenA.setName("Kitchen A");
        kitchenA.setRestaurant(restaurantA);

        settingsA = new RestaurantSettings();
        settingsA.setId(100L);
        settingsA.setRestaurant(restaurantA);
        settingsA.setKitchenEnabled(true);

        settingsB = new RestaurantSettings();
        settingsB.setId(200L);
        settingsB.setRestaurant(restaurantB);
        settingsB.setKitchenEnabled(true);

        table1 = new RestaurantTable();
        table1.setId(15L);
        table1.setRestaurant(restaurantA);
        table1.setTableNumber(1);
        table1.setStatus("OCCUPIED");

        settingsService = new RestaurantSettingsService(settingsRepository, restaurantRepository, securityUtils);
        billService = new BillService(
                billRepository, paymentRepository, orderRepository, orderStatusHistoryRepository,
                tableRepository, userRepository, securityUtils, eventPublisher, notificationService,
                settingsRepository
        );
        kitchenService = new KitchenService(
                orderRepository, orderItemRepository, orderStatusHistoryRepository,
                userRepository, securityUtils, eventPublisher, notificationService,
                settingsRepository
        );
    }

    @Nested
    @DisplayName("1. Mode Switching Tests (Both Directions)")
    class ModeSwitchingTests {

        @Test
        @DisplayName("Switch existing restaurant from Kitchen-Enabled (true) to Kitchen-Free (false), GET returns false, remains false on reload")
        void testSwitchFromKitchenEnabledToKitchenFree() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));
            when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                    .kitchenEnabled(false)
                    .build();

            RestaurantSettingsResponse response = settingsService.updateSettings(req);
            assertNotNull(response);
            assertFalse(response.getKitchenEnabled());
            assertFalse(settingsA.getKitchenEnabled());
            verify(settingsRepository).save(settingsA);

            // GET settings returns false after the update
            RestaurantSettingsResponse getRes = settingsService.getSettings();
            assertNotNull(getRes);
            assertFalse(getRes.getKitchenEnabled());

            // The disabled setting remains false after reloading settings
            RestaurantSettingsResponse reloadRes = settingsService.getSettings();
            assertNotNull(reloadRes);
            assertFalse(reloadRes.getKitchenEnabled());
        }

        @Test
        @DisplayName("Switch restaurant from Kitchen-Free (false) back to Kitchen-Enabled (true)")
        void testSwitchFromKitchenFreeToKitchenEnabled() {
            settingsA.setKitchenEnabled(false);

            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));
            when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                    .kitchenEnabled(true)
                    .build();

            RestaurantSettingsResponse response = settingsService.updateSettings(req);
            assertNotNull(response);
            assertTrue(response.getKitchenEnabled());
            assertTrue(settingsA.getKitchenEnabled());
            verify(settingsRepository).save(settingsA);
        }

        @Test
        @DisplayName("Preserve existing default: restaurants default to kitchenEnabled = true")
        void testExistingRestaurantsDefaultToKitchenEnabledTrue() {
            when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.empty());
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurantA));
            when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(inv -> {
                RestaurantSettings s = inv.getArgument(0);
                s.setId(200L);
                return s;
            });

            RestaurantSettingsResponse defaults = settingsService.getSettings();
            assertNotNull(defaults);
            assertTrue(defaults.getKitchenEnabled());
        }
    }

    @Nested
    @DisplayName("2. Billing in Both Modes")
    class BillingInBothModesTests {

        @Test
        @DisplayName("Kitchen-Enabled: READY order is billed; non-READY throws OrderWorkflowConflictException")
        void testKitchenEnabledBillingRequirements() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(10L);
            settingsA.setKitchenEnabled(true);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));

            // READY order -> Bill succeeds
            Order readyOrder = createTestOrder(501L, "READY");
            when(orderRepository.findByIdAndRestaurantId(501L, 1L)).thenReturn(Optional.of(readyOrder));
            when(billRepository.existsByOrderId(501L)).thenReturn(false);
            when(userRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(adminA));
            when(billRepository.save(any(Bill.class))).thenAnswer(inv -> {
                Bill b = inv.getArgument(0);
                b.setId(901L);
                return b;
            });

            BillResponse billResponse = billService.createBill(new BillRequest(501L));
            assertNotNull(billResponse);
            assertEquals("BILLING", table1.getStatus());

            // NEW order -> Throws OrderWorkflowConflictException
            Order newOrder = createTestOrder(502L, "NEW");
            when(orderRepository.findByIdAndRestaurantId(502L, 1L)).thenReturn(Optional.of(newOrder));

            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> billService.createBill(new BillRequest(502L)));
            assertTrue(ex.getMessage().contains("Order is not in READY status"));
        }

        @Test
        @DisplayName("Kitchen-Free: Active orders (NEW, ACCEPTED, PREPARING) can be billed directly")
        void testKitchenFreeBillingAllowsActiveOrders() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(10L);
            settingsA.setKitchenEnabled(false);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));

            Order preparingOrder = createTestOrder(503L, "PREPARING");
            when(orderRepository.findByIdAndRestaurantId(503L, 1L)).thenReturn(Optional.of(preparingOrder));
            when(billRepository.existsByOrderId(503L)).thenReturn(false);
            when(userRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(adminA));
            when(billRepository.save(any(Bill.class))).thenAnswer(inv -> {
                Bill b = inv.getArgument(0);
                b.setId(902L);
                return b;
            });

            BillResponse response = billService.createBill(new BillRequest(503L));
            assertNotNull(response);
            assertEquals(new BigDecimal("150.00"), response.getTotalAmount());
        }
    }

    @Nested
    @DisplayName("3. In-Progress Orders Safety During Mode Switch")
    class InProgressOrdersSafetyTests {

        @Test
        @DisplayName("Order in PREPARING when switching from Enabled to Free can be billed without kitchen transition")
        void testOrderInProgressWhenSwitchingToKitchenFreeCanBeBilled() {
            // Order was created while restaurant was Kitchen-Enabled and is currently PREPARING
            Order inProgressOrder = createTestOrder(601L, "PREPARING");

            // Mode switch occurs: Admin switches restaurant to Kitchen-Free (false)
            settingsA.setKitchenEnabled(false);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));

            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(securityUtils.getCurrentUserId()).thenReturn(10L);
            when(orderRepository.findByIdAndRestaurantId(601L, 1L)).thenReturn(Optional.of(inProgressOrder));
            when(billRepository.existsByOrderId(601L)).thenReturn(false);
            when(userRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(adminA));
            when(billRepository.save(any(Bill.class))).thenAnswer(inv -> {
                Bill b = inv.getArgument(0);
                b.setId(950L);
                return b;
            });

            // Order is NOT cancelled or reset; it is preserved as PREPARING and can be billed immediately
            assertEquals("PREPARING", inProgressOrder.getStatus());
            BillResponse bill = billService.createBill(new BillRequest(601L));
            assertNotNull(bill);

            // Payment completes the order
            Bill generatedBill = new Bill();
            generatedBill.setId(950L);
            generatedBill.setRestaurant(restaurantA);
            generatedBill.setOrder(inProgressOrder);
            generatedBill.setTotalAmount(new BigDecimal("150.00"));

            when(billRepository.findByIdAndRestaurantId(950L, 1L)).thenReturn(Optional.of(generatedBill));
            when(paymentRepository.existsByBillIdAndPaymentStatus(950L, "PAID")).thenReturn(false);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentRequest paymentRequest = new PaymentRequest(new BigDecimal("150.00"), "CASH", null);
            PaymentResponse payment = billService.processPayment(950L, paymentRequest);
            assertNotNull(payment);
            assertEquals("COMPLETED", inProgressOrder.getStatus());
        }

        @Test
        @DisplayName("Order in NEW when switching from Free to Enabled requires READY status before billing")
        void testOrderInProgressWhenSwitchingToKitchenEnabledRequiresReady() {
            Order newOrder = createTestOrder(602L, "NEW");

            // Mode switch occurs: Admin switches restaurant to Kitchen-Enabled (true)
            settingsA.setKitchenEnabled(true);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));

            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(orderRepository.findByIdAndRestaurantId(602L, 1L)).thenReturn(Optional.of(newOrder));

            // Billed attempt fails because KDS workflow is now enabled
            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> billService.createBill(new BillRequest(602L)));
            assertTrue(ex.getMessage().contains("is not in READY status"));
        }
    }

    @Nested
    @DisplayName("4. Kitchen Staff Operations in Disabled Mode")
    class KitchenStaffOperationsTests {

        @Test
        @DisplayName("Kitchen transitions rejected with KitchenWorkflowDisabledException when kitchen mode is disabled")
        void testKitchenTransitionsRejectedWhenKitchenModeDisabled() {
            settingsA.setKitchenEnabled(false);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));

            when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

            KitchenWorkflowDisabledException ex = assertThrows(KitchenWorkflowDisabledException.class,
                    () -> kitchenService.acceptOrder(701L));
            assertTrue(ex.getMessage().contains("Kitchen workflow is disabled for this restaurant"));

            assertThrows(KitchenWorkflowDisabledException.class, () -> kitchenService.prepareOrder(701L));
            assertThrows(KitchenWorkflowDisabledException.class, () -> kitchenService.readyOrder(701L));
        }

        @Test
        @DisplayName("Kitchen queue getKitchenOrders returns empty list when kitchen mode is disabled")
        void testKitchenQueueReturnsEmptyListWhenDisabled() {
            settingsA.setKitchenEnabled(false);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));

            when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

            List<?> queue = kitchenService.getKitchenOrders(null);
            assertNotNull(queue);
            assertTrue(queue.isEmpty());
        }
    }

    @Nested
    @DisplayName("5. Concurrency & Duplicate Prevention (Idempotency)")
    class ConcurrencyAndDuplicatePreventionTests {

        @Test
        @DisplayName("Duplicate bill generation for same order is rejected")
        void testDuplicateBillGenerationRejected() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            settingsA.setKitchenEnabled(false);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));

            Order order = createTestOrder(801L, "NEW");
            when(orderRepository.findByIdAndRestaurantId(801L, 1L)).thenReturn(Optional.of(order));
            when(billRepository.existsByOrderId(801L)).thenReturn(true);

            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> billService.createBill(new BillRequest(801L)));
            assertTrue(ex.getMessage().contains("Bill already exists for this order"));
        }

        @Test
        @DisplayName("Duplicate payment for same bill is rejected")
        void testDuplicatePaymentRejected() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            settingsA.setKitchenEnabled(false);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));

            Order order = createTestOrder(802L, "NEW");
            Bill bill = new Bill();
            bill.setId(990L);
            bill.setRestaurant(restaurantA);
            bill.setOrder(order);
            bill.setTotalAmount(new BigDecimal("150.00"));

            when(billRepository.findByIdAndRestaurantId(990L, 1L)).thenReturn(Optional.of(bill));
            when(paymentRepository.existsByBillIdAndPaymentStatus(990L, "PAID")).thenReturn(true);

            PaymentRequest paymentRequest = new PaymentRequest(new BigDecimal("150.00"), "CASH", null);
            OrderWorkflowConflictException ex = assertThrows(OrderWorkflowConflictException.class,
                    () -> billService.processPayment(990L, paymentRequest));
            assertTrue(ex.getMessage().contains("Successful payment already exists for this bill"));
        }
    }

    @Nested
    @DisplayName("6. Role Permissions and Tenant Isolation")
    class RolePermissionsAndTenantIsolationTests {

        @Test
        @DisplayName("Waiter cannot update restaurant settings")
        void testWaiterCannotUpdateSettings() {
            when(securityUtils.isAdmin()).thenReturn(false);

            RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                    .kitchenEnabled(false)
                    .build();

            assertThrows(AccessDeniedException.class, () -> settingsService.updateSettings(req));
        }

        @Test
        @DisplayName("Waiter cannot create bill or record payments in either mode")
        void testWaiterCannotCreateBillOrPay() {
            when(securityUtils.isAdmin()).thenReturn(false);

            assertThrows(AccessDeniedException.class, () -> billService.createBill(new BillRequest(100L)));
            assertThrows(AccessDeniedException.class, () -> billService.processPayment(100L, new PaymentRequest()));
        }

        @Test
        @DisplayName("Tenant Isolation: Admin A updates settings only for Restaurant A, Restaurant B is unaffected")
        void testTenantIsolationOnSettingsUpdate() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
            when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
            when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settingsA));
            when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                    .kitchenEnabled(false)
                    .build();

            settingsService.updateSettings(req);
            assertFalse(settingsA.getKitchenEnabled());
            assertEquals(1L, settingsA.getRestaurant().getId());

            // Verify Restaurant A GET returns false
            RestaurantSettingsResponse resA = settingsService.getSettings();
            assertFalse(resA.getKitchenEnabled());

            // Verify Restaurant B settings remain unaffected (true)
            when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);
            when(settingsRepository.findByRestaurantId(2L)).thenReturn(Optional.of(settingsB));
            RestaurantSettingsResponse resB = settingsService.getSettings();
            assertTrue(resB.getKitchenEnabled());
            assertTrue(settingsB.getKitchenEnabled());
        }
    }

    private Order createTestOrder(Long orderId, String status) {
        Order order = new Order();
        order.setId(orderId);
        order.setRestaurant(restaurantA);
        order.setTable(table1);
        order.setOrderType("DINE_IN");
        order.setStatus(status);
        order.setSubtotal(new BigDecimal("150.00"));
        order.setTaxAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("150.00"));
        return order;
    }
}
