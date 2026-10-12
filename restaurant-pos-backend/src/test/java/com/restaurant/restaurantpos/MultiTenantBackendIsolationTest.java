package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.*;
import com.restaurant.restaurantpos.dto.notification.NotificationResponse;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.*;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Backend Multi-Tenant Isolation Test Suite.
 * Validates that all 8 required multi-tenant isolation scenarios are strictly enforced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MultiTenantBackendIsolationTest {

    // Common fixtures
    private Restaurant tenantA;
    private Restaurant tenantB;
    private Role adminRole;
    private Role waiterRole;
    private Role kitchenRole;
    private User adminA;
    private User waiterA;
    private User waiterB;
    private RestaurantTable tableA;
    private RestaurantTable tableB;
    private Category categoryA;
    private Category categoryB;
    private MenuItem menuItemA;
    private MenuItem menuItemB;
    private Customer customerA;
    private Customer customerB;
    private Order orderA;
    private Order orderB;
    private Bill billA;
    private Bill billB;
    private InventoryItem inventoryItemA;
    private InventoryItem inventoryItemB;

    @BeforeEach
    void setUpFixtures() {
        tenantA = new Restaurant();
        tenantA.setId(1L);
        tenantA.setName("Restaurant Alpha");

        tenantB = new Restaurant();
        tenantB.setId(2L);
        tenantB.setName("Restaurant Beta");

        adminRole = new Role(1L, "ADMIN");
        kitchenRole = new Role(2L, "KITCHEN");
        waiterRole = new Role(3L, "WAITER");

        adminA = new User();
        adminA.setId(10L);
        adminA.setRestaurant(tenantA);
        adminA.setRole(adminRole);
        adminA.setName("Admin A");
        adminA.setEmail("adminA@alpha.com");
        adminA.setIsActive(true);

        waiterA = new User();
        waiterA.setId(11L);
        waiterA.setRestaurant(tenantA);
        waiterA.setRole(waiterRole);
        waiterA.setName("Waiter A");
        waiterA.setEmail("waiterA@alpha.com");
        waiterA.setIsActive(true);

        waiterB = new User();
        waiterB.setId(21L);
        waiterB.setRestaurant(tenantB);
        waiterB.setRole(waiterRole);
        waiterB.setName("Waiter B");
        waiterB.setEmail("waiterB@beta.com");
        waiterB.setIsActive(true);

        tableA = new RestaurantTable();
        tableA.setId(101L);
        tableA.setRestaurant(tenantA);
        tableA.setTableNumber(1);
        tableA.setCapacity(4);
        tableA.setStatus("AVAILABLE");

        tableB = new RestaurantTable();
        tableB.setId(201L);
        tableB.setRestaurant(tenantB);
        tableB.setTableNumber(1);
        tableB.setCapacity(4);
        tableB.setStatus("AVAILABLE");

        categoryA = new Category();
        categoryA.setId(110L);
        categoryA.setRestaurant(tenantA);
        categoryA.setName("Starters A");
        categoryA.setIsActive(true);

        categoryB = new Category();
        categoryB.setId(210L);
        categoryB.setRestaurant(tenantB);
        categoryB.setName("Starters B");
        categoryB.setIsActive(true);

        menuItemA = new MenuItem();
        menuItemA.setId(120L);
        menuItemA.setRestaurant(tenantA);
        menuItemA.setCategory(categoryA);
        menuItemA.setName("Alpha Pasta");
        menuItemA.setPrice(new BigDecimal("250.00"));
        menuItemA.setIsAvailable(true);

        menuItemB = new MenuItem();
        menuItemB.setId(220L);
        menuItemB.setRestaurant(tenantB);
        menuItemB.setCategory(categoryB);
        menuItemB.setName("Beta Pizza");
        menuItemB.setPrice(new BigDecimal("300.00"));
        menuItemB.setIsAvailable(true);

        customerA = new Customer();
        customerA.setId(130L);
        customerA.setRestaurant(tenantA);
        customerA.setName("Customer Alpha");
        customerA.setPhone("1111111111");

        customerB = new Customer();
        customerB.setId(230L);
        customerB.setRestaurant(tenantB);
        customerB.setName("Customer Beta");
        customerB.setPhone("2222222222");

        orderA = new Order();
        orderA.setId(1001L);
        orderA.setRestaurant(tenantA);
        orderA.setTable(tableA);
        orderA.setWaiter(waiterA);
        orderA.setCustomer(customerA);
        orderA.setOrderType("DINE_IN");
        orderA.setStatus("READY");
        orderA.setSubtotal(new BigDecimal("250.00"));
        orderA.setTaxAmount(new BigDecimal("12.50"));
        orderA.setDiscountAmount(BigDecimal.ZERO);
        orderA.setTotalAmount(new BigDecimal("262.50"));
        orderA.setCreatedAt(LocalDateTime.now().minusMinutes(10));

        orderB = new Order();
        orderB.setId(2001L);
        orderB.setRestaurant(tenantB);
        orderB.setTable(tableB);
        orderB.setWaiter(waiterB);
        orderB.setCustomer(customerB);
        orderB.setOrderType("DINE_IN");
        orderB.setStatus("READY");
        orderB.setSubtotal(new BigDecimal("300.00"));
        orderB.setTaxAmount(new BigDecimal("15.00"));
        orderB.setDiscountAmount(BigDecimal.ZERO);
        orderB.setTotalAmount(new BigDecimal("315.00"));
        orderB.setCreatedAt(LocalDateTime.now().minusMinutes(10));

        billA = new Bill();
        billA.setId(1050L);
        billA.setRestaurant(tenantA);
        billA.setOrder(orderA);
        billA.setBillNumber("BILL-A-001");
        billA.setSubtotal(orderA.getSubtotal());
        billA.setTaxAmount(orderA.getTaxAmount());
        billA.setDiscountAmount(BigDecimal.ZERO);
        billA.setTotalAmount(orderA.getTotalAmount());
        billA.setGeneratedBy(adminA);

        billB = new Bill();
        billB.setId(2050L);
        billB.setRestaurant(tenantB);
        billB.setOrder(orderB);
        billB.setBillNumber("BILL-B-001");
        billB.setSubtotal(orderB.getSubtotal());
        billB.setTaxAmount(orderB.getTaxAmount());
        billB.setDiscountAmount(BigDecimal.ZERO);
        billB.setTotalAmount(orderB.getTotalAmount());
        billB.setGeneratedBy(waiterB);

        inventoryItemA = new InventoryItem();
        inventoryItemA.setId(160L);
        inventoryItemA.setRestaurant(tenantA);
        inventoryItemA.setName("Flour A");
        inventoryItemA.setUnit("KG");
        inventoryItemA.setCurrentQuantity(new BigDecimal("50.00"));
        inventoryItemA.setMinimumQuantity(new BigDecimal("10.00"));
        inventoryItemA.setIsActive(true);

        inventoryItemB = new InventoryItem();
        inventoryItemB.setId(260L);
        inventoryItemB.setRestaurant(tenantB);
        inventoryItemB.setName("Flour B");
        inventoryItemB.setUnit("KG");
        inventoryItemB.setCurrentQuantity(new BigDecimal("40.00"));
        inventoryItemB.setMinimumQuantity(new BigDecimal("10.00"));
        inventoryItemB.setIsActive(true);
    }

    // =========================================================================
    // 1. Restaurant A cannot read, modify, delete, or reference Restaurant B records
    // =========================================================================
    @Nested
    @DisplayName("Requirement 1: Strict Cross-Tenant Entity Access Prevention")
    class CrossTenantAccessPreventionTests {

        @Mock private OrderRepository orderRepository;
        @Mock private OrderItemRepository orderItemRepository;
        @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
        @Mock private RestaurantRepository restaurantRepository;
        @Mock private RestaurantTableRepository tableRepository;
        @Mock private MenuItemRepository menuItemRepository;
        @Mock private CategoryRepository categoryRepository;
        @Mock private CustomerRepository customerRepository;
        @Mock private UserRepository userRepository;
        @Mock private SecurityUtils securityUtils;
        @Mock private KitchenOrderEventPublisher eventPublisher;
        @Mock private NotificationService notificationService;
        @Mock private BillRepository billRepository;

        @InjectMocks private OrderService orderService;
        @InjectMocks private CategoryService categoryService;
        @InjectMocks private MenuItemService menuItemService;

        @Test
        @DisplayName("Order Lookup: Restaurant A user cannot read Restaurant B's order")
        void testRestaurantACannotReadRestaurantBOrder() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.getCurrentUserId()).thenReturn(waiterA.getId());
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            // Direct scoped query returns empty
            when(orderRepository.findByIdAndRestaurantId(orderB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            // Fallback global query finds Tenant B's order
            when(orderRepository.findById(orderB.getId())).thenReturn(Optional.of(orderB));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.getOrderById(orderB.getId()));
            assertTrue(ex.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("Category Modification: Restaurant A cannot modify Restaurant B's category")
        void testRestaurantACannotModifyRestaurantBCategory() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.isAdmin()).thenReturn(true);
            when(categoryRepository.findByIdAndRestaurantId(categoryB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(categoryRepository.findById(categoryB.getId())).thenReturn(Optional.of(categoryB));

            CategoryRequest updateReq = new CategoryRequest("Hacked Category", "Description", true);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> categoryService.updateCategory(categoryB.getId(), updateReq));
            assertTrue(ex.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("MenuItem Modification: Restaurant A cannot modify or disable Restaurant B's menu item")
        void testRestaurantACannotDeleteRestaurantBMenuItem() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.isAdmin()).thenReturn(true);
            when(menuItemRepository.findByIdAndRestaurantId(menuItemB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(menuItemRepository.findById(menuItemB.getId())).thenReturn(Optional.of(menuItemB));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> menuItemService.setUnavailable(menuItemB.getId()));
            assertTrue(ex.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("User Read: Restaurant A cannot read Restaurant B's user")
        void testRestaurantACannotReadRestaurantBUser() {
            UserRepository mockUserRepo = mock(UserRepository.class);
            SecurityUtils mockSecUtils = mock(SecurityUtils.class);
            UserService testUserService = new UserService(mockUserRepo, mock(RestaurantRepository.class), mock(RoleRepository.class), mock(BCryptPasswordEncoder.class), mockSecUtils);

            when(mockSecUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(mockUserRepo.findByIdAndRestaurantId(waiterB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(mockUserRepo.findById(waiterB.getId())).thenReturn(Optional.of(waiterB));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> testUserService.getUserById(waiterB.getId()));
            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    // =========================================================================
    // 2. Client-supplied restaurant IDs cannot override the authenticated tenant
    // =========================================================================
    @Nested
    @DisplayName("Requirement 2: Client-Supplied Tenant ID Override Prevention")
    class TenantOverridePreventionTests {

        @Test
        @DisplayName("User Creation: Admin at Tenant A supplying Tenant B in body is rejected")
        void testCreateUserRejectsCrossTenantIdInBody() {
            UserRepository userRepo = mock(UserRepository.class);
            SecurityUtils secUtils = mock(SecurityUtils.class);
            UserService userService = new UserService(userRepo, mock(RestaurantRepository.class), mock(RoleRepository.class), mock(BCryptPasswordEncoder.class), secUtils);

            // Authenticated as Tenant A
            when(secUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            doThrow(new AccessDeniedException("Access denied: Cannot access tenant 2"))
                    .when(secUtils).validateTenantAccess(tenantB.getId());

            UserRequest request = new UserRequest();
            request.setRestaurantId(tenantB.getId()); // Attacker supplies Tenant B ID
            request.setRoleId(adminRole.getId());
            request.setName("Attacker");
            request.setEmail("attacker@beta.com");
            request.setPassword("Secret123!");

            assertThrows(AccessDeniedException.class, () -> userService.createUser(request));
            verify(userRepo, never()).save(any());
        }

        @Test
        @DisplayName("User Update: Admin cannot reassign a user's restaurant to another tenant")
        void testUpdateUserCannotReassignRestaurant() {
            UserRepository userRepo = mock(UserRepository.class);
            SecurityUtils secUtils = mock(SecurityUtils.class);
            UserService userService = new UserService(userRepo, mock(RestaurantRepository.class), mock(RoleRepository.class), mock(BCryptPasswordEncoder.class), secUtils);

            when(secUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(userRepo.findByIdAndRestaurantId(adminA.getId(), tenantA.getId())).thenReturn(Optional.of(adminA));

            UserRequest updateReq = new UserRequest();
            updateReq.setRestaurantId(tenantB.getId()); // Attempting to move user to Tenant B

            assertThrows(AccessDeniedException.class, () -> userService.updateUser(adminA.getId(), updateReq));
            verify(userRepo, never()).save(any());
        }

        @Test
        @DisplayName("Order Creation: Restaurant context is strictly derived from security principal")
        void testCreateOrderDerivesRestaurantFromPrincipal() {
            OrderRepository orderRepo = mock(OrderRepository.class);
            RestaurantTableRepository tableRepo = mock(RestaurantTableRepository.class);
            MenuItemRepository itemRepo = mock(MenuItemRepository.class);
            UserRepository userRepo = mock(UserRepository.class);
            RestaurantRepository restRepo = mock(RestaurantRepository.class);
            SecurityUtils secUtils = mock(SecurityUtils.class);

            OrderService testOrderService = new OrderService(
                    orderRepo, mock(OrderItemRepository.class), mock(OrderStatusHistoryRepository.class),
                    restRepo, tableRepo, itemRepo, userRepo, mock(CustomerRepository.class),
                    secUtils, mock(KitchenOrderEventPublisher.class), mock(NotificationService.class), mock(BillRepository.class)
            );

            when(secUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(secUtils.getCurrentUserId()).thenReturn(waiterA.getId());
            when(secUtils.getCurrentUserRole()).thenReturn("WAITER");

            when(tableRepo.findByIdAndRestaurantId(tableA.getId(), tenantA.getId())).thenReturn(Optional.of(tableA));
            when(itemRepo.findByIdAndRestaurantId(menuItemA.getId(), tenantA.getId())).thenReturn(Optional.of(menuItemA));
            when(userRepo.findByIdAndRestaurantId(waiterA.getId(), tenantA.getId())).thenReturn(Optional.of(waiterA));
            when(restRepo.getReferenceById(tenantA.getId())).thenReturn(tenantA);
            when(userRepo.getReferenceById(waiterA.getId())).thenReturn(waiterA);

            OrderRequest request = new OrderRequest();
            request.setOrderType("DINE_IN");
            request.setTableId(tableA.getId());
            OrderItemRequest itemReq = new OrderItemRequest();
            itemReq.setMenuItemId(menuItemA.getId());
            itemReq.setQuantity(2);
            request.setItems(Collections.singletonList(itemReq));

            when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> {
                Order o = invocation.getArgument(0);
                o.setId(9999L);
                return o;
            });

            OrderResponse response = testOrderService.createOrder(request);
            assertNotNull(response);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepo).save(orderCaptor.capture());
            assertEquals(tenantA.getId(), orderCaptor.getValue().getRestaurant().getId(),
                    "Saved order must strictly have tenant A from authenticated identity");
        }
    }

    // =========================================================================
    // 3. Orders cannot use another tenant's tables, customers, waiters, menu items, or bills
    // =========================================================================
    @Nested
    @DisplayName("Requirement 3: Cross-Tenant Child and Reference Integrity")
    class CrossTenantReferenceIntegrityTests {

        @Mock private OrderRepository orderRepository;
        @Mock private OrderItemRepository orderItemRepository;
        @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
        @Mock private RestaurantRepository restaurantRepository;
        @Mock private RestaurantTableRepository tableRepository;
        @Mock private MenuItemRepository menuItemRepository;
        @Mock private CustomerRepository customerRepository;
        @Mock private UserRepository userRepository;
        @Mock private SecurityUtils securityUtils;
        @Mock private KitchenOrderEventPublisher eventPublisher;
        @Mock private NotificationService notificationService;
        @Mock private BillRepository billRepository;

        @InjectMocks private OrderService orderService;

        @BeforeEach
        void setupPrincipal() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.getCurrentUserId()).thenReturn(waiterA.getId());
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        }

        @Test
        @DisplayName("Order cannot use another tenant's table")
        void testOrderCannotUseAnotherTenantsTable() {
            when(tableRepository.findByIdAndRestaurantId(tableB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(tableRepository.findById(tableB.getId())).thenReturn(Optional.of(tableB));

            OrderRequest request = new OrderRequest();
            request.setOrderType("DINE_IN");
            request.setTableId(tableB.getId()); // Referencing Tenant B's table

            RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(request));
            assertTrue(ex.getMessage().contains("not found"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order cannot use another tenant's customer")
        void testOrderCannotUseAnotherTenantsCustomer() {
            when(tableRepository.findByIdAndRestaurantId(tableA.getId(), tenantA.getId())).thenReturn(Optional.of(tableA));
            when(userRepository.findByIdAndRestaurantId(waiterA.getId(), tenantA.getId())).thenReturn(Optional.of(waiterA));
            when(customerRepository.findByIdAndRestaurantId(customerB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(customerRepository.findById(customerB.getId())).thenReturn(Optional.of(customerB));

            OrderRequest request = new OrderRequest();
            request.setOrderType("DINE_IN");
            request.setTableId(tableA.getId());
            request.setCustomerId(customerB.getId()); // Referencing Tenant B's customer

            RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(request));
            assertTrue(ex.getMessage().contains("not found"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order cannot use another tenant's menu items")
        void testOrderCannotUseAnotherTenantsMenuItem() {
            when(tableRepository.findByIdAndRestaurantId(tableA.getId(), tenantA.getId())).thenReturn(Optional.of(tableA));
            when(userRepository.findByIdAndRestaurantId(waiterA.getId(), tenantA.getId())).thenReturn(Optional.of(waiterA));
            when(menuItemRepository.findByIdAndRestaurantId(menuItemB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(menuItemRepository.findById(menuItemB.getId())).thenReturn(Optional.of(menuItemB));

            OrderRequest request = new OrderRequest();
            request.setOrderType("DINE_IN");
            request.setTableId(tableA.getId());
            OrderItemRequest itemReq = new OrderItemRequest();
            itemReq.setMenuItemId(menuItemB.getId()); // Referencing Tenant B's menu item
            itemReq.setQuantity(1);
            request.setItems(Collections.singletonList(itemReq));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(request));
            assertTrue(ex.getMessage().contains("not found"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Bill creation cannot reference another tenant's order")
        void testBillCannotReferenceAnotherTenantsOrder() {
            BillRepository mockBillRepo = mock(BillRepository.class);
            OrderRepository mockOrderRepo = mock(OrderRepository.class);
            SecurityUtils mockSecUtils = mock(SecurityUtils.class);

            BillService billService = new BillService(
                    mockBillRepo, mock(PaymentRepository.class), mockOrderRepo,
                    mock(OrderStatusHistoryRepository.class), mock(RestaurantTableRepository.class),
                    mock(UserRepository.class), mockSecUtils, mock(KitchenOrderEventPublisher.class),
                    mock(NotificationService.class)
            );

            when(mockSecUtils.isAdmin()).thenReturn(true);
            when(mockSecUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(mockSecUtils.getCurrentUserId()).thenReturn(adminA.getId());

            when(mockOrderRepo.findByIdAndRestaurantId(orderB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(mockOrderRepo.findById(orderB.getId())).thenReturn(Optional.of(orderB));

            BillRequest billReq = new BillRequest(orderB.getId());
            RuntimeException ex = assertThrows(RuntimeException.class, () -> billService.createBill(billReq));
            assertTrue(ex.getMessage().contains("not found"));
            verify(mockBillRepo, never()).save(any());
        }
    }

    // =========================================================================
    // 4. Payments, inventory transactions, order history, and notifications remain tenant-scoped
    // =========================================================================
    @Nested
    @DisplayName("Requirement 4: Tenant Scoping on Child and Audit Records")
    class ChildRecordsTenantScopingTests {

        @Mock private BillRepository billRepository;
        @Mock private PaymentRepository paymentRepository;
        @Mock private OrderRepository orderRepository;
        @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
        @Mock private RestaurantTableRepository tableRepository;
        @Mock private UserRepository userRepository;
        @Mock private SecurityUtils securityUtils;
        @Mock private KitchenOrderEventPublisher eventPublisher;
        @Mock private NotificationService notificationService;

        @InjectMocks private BillService billService;

        @Test
        @DisplayName("Payment processing scopes by tenant and attaches bill restaurant to Payment entity")
        void testPaymentProcessingScopesTenant() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.getCurrentUserId()).thenReturn(adminA.getId());

            when(billRepository.findByIdAndRestaurantId(billA.getId(), tenantA.getId())).thenReturn(Optional.of(billA));
            when(orderRepository.save(any(Order.class))).thenReturn(orderA);
            when(userRepository.findByIdAndRestaurantId(adminA.getId(), tenantA.getId())).thenReturn(Optional.of(adminA));
            when(userRepository.getReferenceById(adminA.getId())).thenReturn(adminA);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(88L);
                return p;
            });

            PaymentRequest paymentReq = new PaymentRequest(billA.getTotalAmount(), "CASH", null);

            billService.processPayment(billA.getId(), paymentReq);

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertEquals(tenantA.getId(), paymentCaptor.getValue().getRestaurant().getId(),
                    "Payment must be stamped with the bill's restaurant tenant");

            ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
            verify(orderStatusHistoryRepository).save(historyCaptor.capture());
            assertEquals(tenantA.getId(), historyCaptor.getValue().getRestaurant().getId(),
                    "Order status history must be stamped with the order's restaurant tenant");
        }

        @Test
        @DisplayName("Payment processing rejects another tenant's bill")
        void testPaymentProcessingRejectsCrossTenantBill() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.getCurrentUserId()).thenReturn(adminA.getId());

            when(billRepository.findByIdAndRestaurantId(billB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(billRepository.findById(billB.getId())).thenReturn(Optional.of(billB));

            PaymentRequest paymentReq = new PaymentRequest(billB.getTotalAmount(), "CASH", null);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> billService.processPayment(billB.getId(), paymentReq));
            assertTrue(ex.getMessage().contains("not found"));
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Payment list retrieval is scoped to tenant")
        void testGetPaymentsByBillIdScopedToTenant() {
            when(securityUtils.isAdmin()).thenReturn(true);
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(billRepository.findByIdAndRestaurantId(billA.getId(), tenantA.getId())).thenReturn(Optional.of(billA));
            Payment p1 = new Payment();
            p1.setId(10L);
            p1.setRestaurant(tenantA);
            p1.setBill(billA);
            p1.setAmount(new BigDecimal("100.00"));
            p1.setPaymentMethod("CASH");
            p1.setPaymentStatus("SUCCESS");

            when(paymentRepository.findByBillIdAndRestaurantId(billA.getId(), tenantA.getId())).thenReturn(Collections.singletonList(p1));

            List<PaymentResponse> results = billService.getPaymentsByBillId(billA.getId());
            assertEquals(1, results.size());
            verify(paymentRepository).findByBillIdAndRestaurantId(billA.getId(), tenantA.getId());
        }

        @Test
        @DisplayName("Inventory transactions strictly stamp item's restaurant tenant")
        void testInventoryTransactionStampsTenant() {
            InventoryItemRepository itemRepo = mock(InventoryItemRepository.class);
            InventoryTransactionRepository transRepo = mock(InventoryTransactionRepository.class);
            UserRepository userRepo = mock(UserRepository.class);
            SecurityUtils secUtils = mock(SecurityUtils.class);

            InventoryService invService = new InventoryService(
                    itemRepo, transRepo, mock(RestaurantRepository.class), userRepo, secUtils
            );

            when(secUtils.isAdmin()).thenReturn(true);
            when(secUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(secUtils.getCurrentUserId()).thenReturn(adminA.getId());
            when(itemRepo.findByIdAndRestaurantId(inventoryItemA.getId(), tenantA.getId())).thenReturn(Optional.of(inventoryItemA));
            when(userRepo.getReferenceById(adminA.getId())).thenReturn(adminA);
            when(transRepo.save(any(InventoryTransaction.class))).thenAnswer(inv -> {
                InventoryTransaction it = inv.getArgument(0);
                it.setId(99L);
                return it;
            });

            InventoryTransactionRequest transReq = new InventoryTransactionRequest(new BigDecimal("10.00"), "PURCHASE", 5001L);
            invService.purchaseStock(inventoryItemA.getId(), transReq);

            ArgumentCaptor<InventoryTransaction> transCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
            verify(transRepo).save(transCaptor.capture());
            assertEquals(tenantA.getId(), transCaptor.getValue().getRestaurant().getId(),
                    "Inventory transaction must be stamped with the inventory item's restaurant tenant");
        }

        @Test
        @DisplayName("Notification retrieval is strictly scoped to tenant and recipient user")
        void testNotificationRetrievalTenantScoped() {
            NotificationRepository notifRepo = mock(NotificationRepository.class);
            SecurityUtils secUtils = mock(SecurityUtils.class);

            NotificationService notifService = new NotificationService(
                    notifRepo, mock(UserRepository.class), secUtils, null
            );

            when(secUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(secUtils.getCurrentUserId()).thenReturn(waiterA.getId());

            notifService.getUserNotifications();

            verify(notifRepo).findByRestaurantIdAndRecipientUserIdOrderByCreatedAtDesc(tenantA.getId(), waiterA.getId());
            verifyNoMoreInteractions(notifRepo);
        }
    }

    // =========================================================================
    // 5. Cross-tenant requests do not reveal whether another tenant's record exists
    // =========================================================================
    @Nested
    @DisplayName("Requirement 5: Anti-Enumeration & Consistent Error Behavior")
    class AntiEnumerationTests {

        @Mock private OrderRepository orderRepository;
        @Mock private RestaurantTableRepository tableRepository;
        @Mock private SecurityUtils securityUtils;
        @InjectMocks private OrderService orderService;

        @Test
        @DisplayName("Non-existent Order vs. Cross-Tenant Order: both fail with consistent 404 not-found without leaking state")
        void testOrderAntiEnumeration() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.getCurrentUserId()).thenReturn(waiterA.getId());
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            // Scenario 1: Non-existent ID
            Long nonExistentId = 999999L;
            when(orderRepository.findByIdAndRestaurantId(nonExistentId, tenantA.getId())).thenReturn(Optional.empty());
            when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            RuntimeException nonExistentException = assertThrows(RuntimeException.class,
                    () -> orderService.getOrderById(nonExistentId));
            assertTrue(nonExistentException.getMessage().contains("not found"));

            // Scenario 2: Cross-tenant ID
            Long crossTenantId = orderB.getId();
            when(orderRepository.findByIdAndRestaurantId(crossTenantId, tenantA.getId())).thenReturn(Optional.empty());
            when(orderRepository.findById(crossTenantId)).thenReturn(Optional.of(orderB));

            RuntimeException crossTenantException = assertThrows(RuntimeException.class,
                    () -> orderService.getOrderById(crossTenantId));
            assertTrue(crossTenantException.getMessage().contains("not found"));
            assertEquals(nonExistentException.getMessage().replace(nonExistentId.toString(), crossTenantId.toString()),
                    crossTenantException.getMessage());
        }

        @Test
        @DisplayName("Restaurant Table anti-enumeration: querying cross-tenant table fails securely with consistent not-found")
        void testTableAntiEnumeration() {
            RestaurantTableRepository tableRepo = mock(RestaurantTableRepository.class);
            SecurityUtils secUtils = mock(SecurityUtils.class);
            RestaurantTableService tableService = new RestaurantTableService(
                    tableRepo, mock(RestaurantRepository.class), secUtils
            );

            when(secUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());

            // Non-existent table
            when(tableRepo.findByIdAndRestaurantId(88888L, tenantA.getId())).thenReturn(Optional.empty());
            when(tableRepo.findById(88888L)).thenReturn(Optional.empty());

            RuntimeException nonExistentTableEx = assertThrows(RuntimeException.class, () -> tableService.getTableById(88888L));
            assertEquals("Table not found", nonExistentTableEx.getMessage());

            // Cross-tenant table
            when(tableRepo.findByIdAndRestaurantId(tableB.getId(), tenantA.getId())).thenReturn(Optional.empty());
            when(tableRepo.findById(tableB.getId())).thenReturn(Optional.of(tableB));

            RuntimeException crossTenantTableEx = assertThrows(RuntimeException.class, () -> tableService.getTableById(tableB.getId()));
            assertEquals("Table not found", crossTenantTableEx.getMessage());
        }
    }

    // =========================================================================
    // 6. WebSocket/KDS subscriptions and updates cannot cross tenant boundaries
    // =========================================================================
    @Nested
    @DisplayName("Requirement 6: WebSocket/KDS Cross-Tenant Subscription Authorization")
    class WebSocketTenantAuthorizationTests {

        @Mock private JwtService jwtService;
        @Mock private CustomUserDetailsService userDetailsService;
        @Mock private MessageChannel messageChannel;

        @InjectMocks private StompAuthChannelInterceptor stompInterceptor;

        private CustomUserDetails kitchenUserTenantA;
        private CustomUserDetails waiterUserTenantA;
        private CustomUserDetails kitchenUserTenantB;

        @BeforeEach
        void setupUsers() {
            kitchenUserTenantA = new CustomUserDetails(51L, tenantA.getId(), "KITCHEN", "kitchenA@alpha.com", "pass", true, Collections.emptyList());
            waiterUserTenantA = new CustomUserDetails(52L, tenantA.getId(), "WAITER", "waiterA@alpha.com", "pass", true, Collections.emptyList());
            kitchenUserTenantB = new CustomUserDetails(61L, tenantB.getId(), "KITCHEN", "kitchenB@beta.com", "pass", true, Collections.emptyList());
        }

        @Test
        @DisplayName("Client SEND frame to /topic destination is rejected")
        void testClientSendToTopicDestinationRejected() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
            accessor.setDestination("/topic/restaurants/" + tenantA.getId() + "/kitchen/orders");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(kitchenUserTenantA, null, kitchenUserTenantA.getAuthorities());
            accessor.setUser(auth);

            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
        }

        @Test
        @DisplayName("Client SEND frame to general /topic destination is rejected")
        void testClientSendToGeneralTopicDestinationRejected() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
            accessor.setDestination("/topic/orders");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(kitchenUserTenantA, null, kitchenUserTenantA.getAuthorities());
            accessor.setUser(auth);

            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
        }

        @Test
        @DisplayName("Authorized CONNECT frame continues to work")
        void testConnectFramePreserved() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            Message<?> result = stompInterceptor.preSend(message, messageChannel);
            assertNotNull(result);
        }

        @Test
        @DisplayName("KITCHEN user of Tenant A can subscribe to Tenant A Kitchen topic")
        void testKitchenUserCanSubscribeOwnTenantKitchenTopic() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination("/topic/restaurants/" + tenantA.getId() + "/kitchen/orders");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(kitchenUserTenantA, null, kitchenUserTenantA.getAuthorities());
            accessor.setUser(auth);

            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            Message<?> result = stompInterceptor.preSend(message, messageChannel);
            assertNotNull(result);
        }

        @Test
        @DisplayName("KITCHEN user of Tenant B CANNOT subscribe to Tenant A Kitchen topic (cross-tenant rejected)")
        void testCrossTenantKitchenSubscriptionRejected() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination("/topic/restaurants/" + tenantA.getId() + "/kitchen/orders");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(kitchenUserTenantB, null, kitchenUserTenantB.getAuthorities());
            accessor.setUser(auth);

            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
        }

        @Test
        @DisplayName("WAITER of Tenant A CANNOT subscribe to Tenant A Kitchen topic (role unauthorized)")
        void testRoleUnauthorizedSubscriptionRejected() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination("/topic/restaurants/" + tenantA.getId() + "/kitchen/orders");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(waiterUserTenantA, null, waiterUserTenantA.getAuthorities());
            accessor.setUser(auth);

            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
        }

        @Test
        @DisplayName("Notification topic: User cannot subscribe to another tenant's notification destination")
        void testCrossTenantNotificationSubscriptionRejected() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            // Target is Tenant A, User 51
            accessor.setDestination("/topic/restaurants/" + tenantA.getId() + "/notifications/51");
            // Caller is from Tenant B
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(kitchenUserTenantB, null, kitchenUserTenantB.getAuthorities());
            accessor.setUser(auth);

            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
        }

        @Test
        @DisplayName("Unauthenticated subscriber is rejected immediately")
        void testUnauthenticatedSubscriptionRejected() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination("/topic/restaurants/" + tenantA.getId() + "/kitchen/orders");
            // No user in accessor

            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
        }
    }

    // =========================================================================
    // 7. Scheduled jobs and auto-acceptance do not process another tenant's orders incorrectly
    // =========================================================================
    @Nested
    @DisplayName("Requirement 7: Background Auto-Acceptance Multi-Tenant Scoping")
    class AutoAcceptanceTenantIsolationTests {

        @Mock private OrderRepository orderRepository;
        @Mock private RestaurantSettingsRepository settingsRepository;
        @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
        @Mock private UserRepository userRepository;
        @Mock private KitchenOrderEventPublisher eventPublisher;
        @Mock private NotificationService notificationService;
        @InjectMocks private AutoAcceptService autoAcceptService;

        @Test
        @DisplayName("AutoAccept processes pending orders with explicit tenant context and stamps OrderStatusHistory correctly")
        void testAutoAcceptProcessesExplicitTenantContext() {
            RestaurantSettings settingsA = new RestaurantSettings();
            settingsA.setId(1L);
            settingsA.setRestaurant(tenantA);
            settingsA.setAutoAcceptOrders(true);

            Order pendingOrderA = new Order();
            pendingOrderA.setId(7001L);
            pendingOrderA.setRestaurant(tenantA);
            pendingOrderA.setOrderType("DINE_IN");
            pendingOrderA.setStatus("NEW");
            pendingOrderA.setCreatedAt(LocalDateTime.now().minusMinutes(5));
            pendingOrderA.setWaiter(adminA);

            when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(Collections.singletonList(pendingOrderA));
            when(orderRepository.findById(pendingOrderA.getId())).thenReturn(Optional.of(pendingOrderA));
            when(settingsRepository.findByRestaurantId(tenantA.getId())).thenReturn(Optional.of(settingsA));
            when(orderRepository.updateStatusToAcceptedIfNew(eq(pendingOrderA.getId()), any(LocalDateTime.class))).thenReturn(1);

            autoAcceptService.processAutoAcceptOrders();

            ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
            verify(orderStatusHistoryRepository).save(historyCaptor.capture());
            assertEquals(tenantA.getId(), historyCaptor.getValue().getRestaurant().getId(),
                    "Auto-accepted order status history must be explicitly stamped with the order's tenant");
        }

        @Test
        @DisplayName("AutoAccept does NOT touch orders for restaurants where auto-accept is disabled")
        void testAutoAcceptIgnoresTenantWithDisabledSetting() {
            RestaurantSettings settingsB = new RestaurantSettings();
            settingsB.setId(2L);
            settingsB.setRestaurant(tenantB);
            settingsB.setAutoAcceptOrders(false); // Disabled for Tenant B

            Order pendingOrderB = new Order();
            pendingOrderB.setId(7002L);
            pendingOrderB.setRestaurant(tenantB);
            pendingOrderB.setOrderType("DINE_IN");
            pendingOrderB.setStatus("NEW");
            pendingOrderB.setCreatedAt(LocalDateTime.now().minusMinutes(5));

            when(orderRepository.findEligibleForAutoAccept(any(LocalDateTime.class))).thenReturn(Collections.singletonList(pendingOrderB));
            when(orderRepository.findById(pendingOrderB.getId())).thenReturn(Optional.of(pendingOrderB));
            when(settingsRepository.findByRestaurantId(tenantB.getId())).thenReturn(Optional.of(settingsB));

            autoAcceptService.processAutoAcceptOrders();

            verify(orderRepository, never()).updateStatusToAcceptedIfNew(eq(pendingOrderB.getId()), any(LocalDateTime.class));
            verify(orderStatusHistoryRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 8. Existing waiter session ownership, table locks, billing, and order lifecycle behavior remain intact
    // =========================================================================
    @Nested
    @DisplayName("Requirement 8: Table Locks, Waiter Session Ownership, Billing & Order Lifecycle")
    class WaiterSessionAndOrderLifecycleTests {

        @Mock private OrderRepository orderRepository;
        @Mock private OrderItemRepository orderItemRepository;
        @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
        @Mock private RestaurantRepository restaurantRepository;
        @Mock private RestaurantTableRepository tableRepository;
        @Mock private MenuItemRepository menuItemRepository;
        @Mock private UserRepository userRepository;
        @Mock private SecurityUtils securityUtils;
        @Mock private KitchenOrderEventPublisher eventPublisher;
        @Mock private NotificationService notificationService;
        @Mock private BillRepository billRepository;
        @Mock private PaymentRepository paymentRepository;

        @InjectMocks private OrderService orderService;

        @Test
        @DisplayName("Concurrent table lock: another waiter cannot create order on an already occupied table")
        void testTableLockPreventCrossWaiterContention() {
            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.getCurrentUserId()).thenReturn(waiterA.getId());
            when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

            RestaurantTable occupiedTable = new RestaurantTable();
            occupiedTable.setId(tableA.getId());
            occupiedTable.setRestaurant(tenantA);
            occupiedTable.setStatus("OCCUPIED");

            User otherWaiter = new User();
            otherWaiter.setId(99L);
            otherWaiter.setRestaurant(tenantA);

            Order existingActiveOrder = new Order();
            existingActiveOrder.setId(5555L);
            existingActiveOrder.setRestaurant(tenantA);
            existingActiveOrder.setTable(occupiedTable);
            existingActiveOrder.setWaiter(otherWaiter); // Assigned to other waiter
            existingActiveOrder.setStatus("PREPARING");

            when(tableRepository.findByIdAndRestaurantId(occupiedTable.getId(), tenantA.getId())).thenReturn(Optional.of(occupiedTable));
            when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(
                    eq(tenantA.getId()), eq(occupiedTable.getId()), eq("DINE_IN"), anyList()))
                    .thenReturn(true);
            when(orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                    eq(tenantA.getId()), eq(occupiedTable.getId()), eq("DINE_IN"), anyList()))
                    .thenReturn(Optional.of(existingActiveOrder));

            OrderRequest request = new OrderRequest();
            request.setOrderType("DINE_IN");
            request.setTableId(occupiedTable.getId());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(request));
            assertTrue(ex.getMessage().contains("already has an active order assigned to another waiter"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Complete POS Order Lifecycle with Billing and Table Release within tenant")
        void testFullOrderLifecycleAndTableRelease() {
            BillService billService = new BillService(
                    billRepository, paymentRepository, orderRepository,
                    orderStatusHistoryRepository, tableRepository, userRepository,
                    securityUtils, eventPublisher, notificationService
            );

            when(securityUtils.getCurrentRestaurantId()).thenReturn(tenantA.getId());
            when(securityUtils.getCurrentUserId()).thenReturn(adminA.getId());
            when(securityUtils.isAdmin()).thenReturn(true);

            // Table initially OCCUPIED
            RestaurantTable activeTable = new RestaurantTable();
            activeTable.setId(tableA.getId());
            activeTable.setRestaurant(tenantA);
            activeTable.setStatus("OCCUPIED");

            Order completedOrder = new Order();
            completedOrder.setId(8888L);
            completedOrder.setRestaurant(tenantA);
            completedOrder.setTable(activeTable);
            completedOrder.setOrderType("DINE_IN");
            completedOrder.setStatus("READY");
            completedOrder.setSubtotal(new BigDecimal("100.00"));
            completedOrder.setTaxAmount(new BigDecimal("5.00"));
            completedOrder.setDiscountAmount(BigDecimal.ZERO);
            completedOrder.setTotalAmount(new BigDecimal("105.00"));

            Bill activeBill = new Bill();
            activeBill.setId(3333L);
            activeBill.setRestaurant(tenantA);
            activeBill.setOrder(completedOrder);
            activeBill.setBillNumber("BILL-001");
            activeBill.setTotalAmount(new BigDecimal("105.00"));

            when(billRepository.findByIdAndRestaurantId(activeBill.getId(), tenantA.getId())).thenReturn(Optional.of(activeBill));
            when(orderRepository.save(any(Order.class))).thenReturn(completedOrder);
            when(userRepository.findByIdAndRestaurantId(adminA.getId(), tenantA.getId())).thenReturn(Optional.of(adminA));
            when(userRepository.getReferenceById(adminA.getId())).thenReturn(adminA);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(88L);
                return p;
            });

            PaymentRequest payReq = new PaymentRequest(new BigDecimal("105.00"), "CASH", null);
            billService.processPayment(activeBill.getId(), payReq);

            // Verify order transitioned to COMPLETED
            assertEquals("COMPLETED", completedOrder.getStatus());
            // Verify table released back to AVAILABLE
            assertEquals("AVAILABLE", activeTable.getStatus());
            verify(tableRepository).save(activeTable);
        }
    }

    // =========================================================================
    // 9. Fail-Closed on Missing Tenant Context
    // =========================================================================
    @Nested
    @DisplayName("Requirement: Fail Closed on Missing Tenant Context")
    class MissingTenantContextFailClosedTests {

        @Mock private OrderRepository orderRepository;
        @Mock private BillRepository billRepository;
        @Mock private RestaurantTableRepository tableRepository;
        @Mock private MenuItemRepository menuItemRepository;
        @Mock private CategoryRepository categoryRepository;
        @Mock private UserRepository userRepository;
        @Mock private SecurityUtils securityUtils;
        @Mock private OrderItemRepository orderItemRepository;
        @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
        @Mock private KitchenOrderEventPublisher eventPublisher;
        @Mock private NotificationService notificationService;

        @Test
        @DisplayName("OrderService fails closed when restaurant context is null")
        void testOrderServiceFailsClosedOnNullTenant() {
            OrderService orderService = new OrderService(
                    orderRepository, orderItemRepository, orderStatusHistoryRepository,
                    mock(RestaurantRepository.class), tableRepository, menuItemRepository,
                    userRepository, mock(CustomerRepository.class), securityUtils,
                    eventPublisher, notificationService, billRepository
            );

            when(securityUtils.getCurrentRestaurantId()).thenReturn(null);

            assertThrows(AccessDeniedException.class, () -> orderService.getOrderById(1001L));
            assertThrows(AccessDeniedException.class, () -> orderService.createOrder(new OrderRequest()));
            assertThrows(AccessDeniedException.class, () -> orderService.getAllOrders(null, null));
            assertThrows(AccessDeniedException.class, () -> orderService.cancelOrder(1001L));
            assertThrows(AccessDeniedException.class, () -> orderService.addItemsToOrder(1001L, new OrderRequest()));
            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("BillService fails closed when restaurant context is null")
        void testBillServiceFailsClosedOnNullTenant() {
            BillService billService = new BillService(
                    billRepository, mock(PaymentRepository.class), orderRepository,
                    orderStatusHistoryRepository, tableRepository, userRepository,
                    securityUtils, eventPublisher, notificationService
            );

            when(securityUtils.getCurrentRestaurantId()).thenReturn(null);

            assertThrows(AccessDeniedException.class, () -> billService.getBillById(1050L));
            assertThrows(AccessDeniedException.class, () -> billService.createBill(new BillRequest(1001L)));
            assertThrows(AccessDeniedException.class, () -> billService.getPaymentsByBillId(1050L));
            assertThrows(AccessDeniedException.class, () -> billService.processPayment(1050L, new PaymentRequest()));
            verifyNoInteractions(billRepository);
        }

        @Test
        @DisplayName("RestaurantTableService fails closed when restaurant context is null")
        void testTableServiceFailsClosedOnNullTenant() {
            RestaurantTableService tableService = new RestaurantTableService(
                    tableRepository, mock(RestaurantRepository.class), securityUtils
            );

            when(securityUtils.getCurrentRestaurantId()).thenReturn(null);

            assertThrows(AccessDeniedException.class, () -> tableService.getTableById(101L));
            assertThrows(AccessDeniedException.class, () -> tableService.getAllTables());
            assertThrows(AccessDeniedException.class, () -> tableService.createTable(new TableRequest()));
            verifyNoInteractions(tableRepository);
        }

        @Test
        @DisplayName("MenuItemService fails closed when restaurant context is null")
        void testMenuItemServiceFailsClosedOnNullTenant() {
            MenuItemService menuItemService = new MenuItemService(
                    menuItemRepository, categoryRepository, mock(RestaurantRepository.class), securityUtils
            );

            when(securityUtils.getCurrentRestaurantId()).thenReturn(null);

            assertThrows(AccessDeniedException.class, () -> menuItemService.getMenuItemById(120L));
            assertThrows(AccessDeniedException.class, () -> menuItemService.getMenuItems(null));
            assertThrows(AccessDeniedException.class, () -> menuItemService.createMenuItem(new MenuItemRequest()));
            verifyNoInteractions(menuItemRepository);
        }

        @Test
        @DisplayName("CategoryService fails closed when restaurant context is null")
        void testCategoryServiceFailsClosedOnNullTenant() {
            CategoryService categoryService = new CategoryService(
                    categoryRepository, mock(RestaurantRepository.class), securityUtils
            );

            when(securityUtils.getCurrentRestaurantId()).thenReturn(null);

            assertThrows(AccessDeniedException.class, () -> categoryService.getCategoryById(110L));
            assertThrows(AccessDeniedException.class, () -> categoryService.getCategoriesByRestaurant());
            assertThrows(AccessDeniedException.class, () -> categoryService.createCategory(new CategoryRequest()));
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("UserService fails closed when restaurant context is null")
        void testUserServiceFailsClosedOnNullTenant() {
            UserService userService = new UserService(
                    userRepository, mock(RestaurantRepository.class), mock(RoleRepository.class),
                    mock(BCryptPasswordEncoder.class), securityUtils
            );

            when(securityUtils.getCurrentRestaurantId()).thenReturn(null);

            assertThrows(AccessDeniedException.class, () -> userService.getUserById(10L));
            assertThrows(AccessDeniedException.class, () -> userService.getUsersByRestaurant(1L));
            assertThrows(AccessDeniedException.class, () -> userService.createUser(new UserRequest()));
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("KitchenService fails closed when restaurant context is null")
        void testKitchenServiceFailsClosedOnNullTenant() {
            KitchenService kitchenService = new KitchenService(
                    orderRepository, orderItemRepository, orderStatusHistoryRepository,
                    userRepository, securityUtils, eventPublisher, notificationService
            );

            when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
            when(securityUtils.getCurrentRestaurantId()).thenReturn(null);

            assertThrows(AccessDeniedException.class, () -> kitchenService.getKitchenOrders(null));
            assertThrows(AccessDeniedException.class, () -> kitchenService.getKitchenOrderById(1001L));
            verifyNoInteractions(orderRepository);
        }
    }
}
