package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.BillRequest;
import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.MenuItemRequest;
import com.restaurant.restaurantpos.dto.MenuItemResponse;
import com.restaurant.restaurantpos.dto.MenuItemVariationRequest;
import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderItemResponse;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.dto.PaymentRequest;
import com.restaurant.restaurantpos.dto.PaymentResponse;
import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderItemResponse;
import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderResponse;
import com.restaurant.restaurantpos.dto.report.ReportBillResponse;
import com.restaurant.restaurantpos.dto.report.SalesReportResponse;
import com.restaurant.restaurantpos.entity.Category;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.MenuItemVariation;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.CategoryRepository;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.MenuItemVariationRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.BillService;
import com.restaurant.restaurantpos.service.KitchenService;
import com.restaurant.restaurantpos.service.MenuItemService;
import com.restaurant.restaurantpos.service.OrderService;
import com.restaurant.restaurantpos.service.ReportService;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MenuItemVariationTest {

    @Autowired
    private MenuItemService menuItemService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private MenuItemVariationRepository variationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BillService billService;

    @Autowired
    private KitchenService kitchenService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private RestaurantSettingsRepository settingsRepository;

    @Autowired
    private BillRepository billRepository;

    @MockitoBean
    private SecurityUtils securityUtils;

    @MockitoBean
    private KitchenOrderEventPublisher eventPublisher;

    @MockitoBean
    private NotificationService notificationService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private Category categoryA;
    private Category categoryB;
    private RestaurantTable tableA;
    private User adminUser;
    private User waiterUser;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));
        Role waiterRole = roleRepository.findByName("WAITER")
                .orElseGet(() -> roleRepository.save(new Role(null, "WAITER")));

        restaurantA = new Restaurant();
        restaurantA.setName("Restaurant A");
        restaurantA.setAddress("address A");
        restaurantA.setPhone("phone A");
        restaurantA.setIsActive(true);
        restaurantA = restaurantRepository.save(restaurantA);

        restaurantB = new Restaurant();
        restaurantB.setName("Restaurant B");
        restaurantB.setAddress("address B");
        restaurantB.setPhone("phone B");
        restaurantB.setIsActive(true);
        restaurantB = restaurantRepository.save(restaurantB);

        categoryA = new Category();
        categoryA.setRestaurant(restaurantA);
        categoryA.setName("Biryani");
        categoryA.setDescription("Delicious Biryanis");
        categoryA.setIsActive(true);
        categoryA = categoryRepository.save(categoryA);

        categoryB = new Category();
        categoryB.setRestaurant(restaurantB);
        categoryB.setName("Starters");
        categoryB.setDescription("Snacks");
        categoryB.setIsActive(true);
        categoryB = categoryRepository.save(categoryB);

        tableA = new RestaurantTable();
        tableA.setRestaurant(restaurantA);
        tableA.setTableNumber(1);
        tableA.setCapacity(4);
        tableA.setStatus("AVAILABLE");
        tableA = tableRepository.save(tableA);

        adminUser = new User();
        adminUser.setName("Admin User");
        adminUser.setEmail("admin_var@test.com");
        adminUser.setPasswordHash("password");
        adminUser.setRole(adminRole);
        adminUser.setRestaurant(restaurantA);
        adminUser.setIsActive(true);
        adminUser = userRepository.save(adminUser);

        waiterUser = new User();
        waiterUser.setName("Waiter User");
        waiterUser.setEmail("waiter_var@test.com");
        waiterUser.setPasswordHash("password");
        waiterUser.setRole(waiterRole);
        waiterUser.setRestaurant(restaurantA);
        waiterUser.setIsActive(true);
        waiterUser = userRepository.save(waiterUser);

        RestaurantSettings settingsA = new RestaurantSettings();
        settingsA.setRestaurant(restaurantA);
        settingsA.setTaxEnabled(true);
        settingsA.setDefaultTaxRate(new BigDecimal("5.00"));
        settingsA.setGstMode("GST_EXCLUDED");
        settingsA.setCgstRate(new BigDecimal("2.50"));
        settingsA.setSgstRate(new BigDecimal("2.50"));
        settingsA.setKitchenEnabled(true);
        settingsRepository.save(settingsA);

        // Default mock context: Restaurant A, Admin User
        when(securityUtils.getCurrentRestaurantId()).thenReturn(restaurantA.getId());
        when(securityUtils.getCurrentUserId()).thenReturn(adminUser.getId());
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(securityUtils.isAdmin()).thenReturn(true);
    }

    @Test
    @DisplayName("1. Create MenuItem with multiple variations (Half, Full)")
    void testCreateMenuItemWithVariations() {
        MenuItemVariationRequest varHalf = MenuItemVariationRequest.builder()
                .name("Half")
                .price(new BigDecimal("120.00"))
                .isAvailable(true)
                .sortOrder(1)
                .build();
        MenuItemVariationRequest varFull = MenuItemVariationRequest.builder()
                .name("Full")
                .price(new BigDecimal("220.00"))
                .isAvailable(true)
                .sortOrder(2)
                .build();

        MenuItemRequest request = MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Chicken Biryani")
                .description("Hyderabadi style")
                .variations(Arrays.asList(varHalf, varFull))
                .isAvailable(true)
                .build();

        MenuItemResponse response = menuItemService.createMenuItem(request);

        assertNotNull(response.getId());
        assertEquals("Chicken Biryani", response.getName());
        assertNotNull(response.getVariations());
        assertEquals(2, response.getVariations().size());

        MenuItemResponse.class.getDeclaredFields();
        assertEquals("Half", response.getVariations().get(0).getName());
        assertEquals(new BigDecimal("120.00"), response.getVariations().get(0).getPrice());
        assertEquals("Full", response.getVariations().get(1).getName());
        assertEquals(new BigDecimal("220.00"), response.getVariations().get(1).getPrice());

        // Baseline price on MenuItem is automatically set to the lowest variation price
        assertEquals(new BigDecimal("120.00"), response.getPrice());
    }

    @Test
    @DisplayName("2. Update MenuItem: edit existing variations, add new, remove old")
    void testUpdateMenuItemVariations() {
        MenuItemVariationRequest v1 = MenuItemVariationRequest.builder().name("Small").price(new BigDecimal("100.00")).isAvailable(true).build();
        MenuItemVariationRequest v2 = MenuItemVariationRequest.builder().name("Large").price(new BigDecimal("200.00")).isAvailable(true).build();

        MenuItemResponse created = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Veg Pulao")
                .variations(Arrays.asList(v1, v2))
                .build());

        Long smallId = created.getVariations().get(0).getId();
        Long largeId = created.getVariations().get(1).getId();

        // Update: Edit Small price to 110, remove Large, add Family Pack at 350
        MenuItemVariationRequest updateSmall = MenuItemVariationRequest.builder()
                .id(smallId)
                .name("Small (Updated)")
                .price(new BigDecimal("110.00"))
                .isAvailable(true)
                .build();
        MenuItemVariationRequest addFamily = MenuItemVariationRequest.builder()
                .name("Family Pack")
                .price(new BigDecimal("350.00"))
                .isAvailable(true)
                .build();

        MenuItemResponse updated = menuItemService.updateMenuItem(created.getId(), MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Veg Pulao")
                .variations(Arrays.asList(updateSmall, addFamily))
                .build());

        assertEquals(2, updated.getVariations().size());
        assertEquals("Small (Updated)", updated.getVariations().get(0).getName());
        assertEquals(new BigDecimal("110.00"), updated.getVariations().get(0).getPrice());
        assertEquals("Family Pack", updated.getVariations().get(1).getName());
        assertEquals(new BigDecimal("350.00"), updated.getVariations().get(1).getPrice());

        // Verify large variation was removed
        assertFalse(variationRepository.findById(largeId).isPresent());
    }

    @Test
    @DisplayName("3. Duplicate variation name within same menu item is rejected")
    void testDuplicateVariationNameRejected() {
        MenuItemVariationRequest v1 = MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("120.00")).build();
        MenuItemVariationRequest v2 = MenuItemVariationRequest.builder().name("half").price(new BigDecimal("130.00")).build();

        MenuItemRequest request = MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Mutton Biryani")
                .variations(Arrays.asList(v1, v2))
                .build();

        assertThrows(RuntimeException.class, () -> menuItemService.createMenuItem(request));
    }

    @Test
    @DisplayName("4. Negative variation price is rejected")
    void testNegativeVariationPriceRejected() {
        MenuItemVariationRequest v1 = MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("-10.00")).build();

        MenuItemRequest request = MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Paneer Biryani")
                .variations(Collections.singletonList(v1))
                .build();

        assertThrows(RuntimeException.class, () -> menuItemService.createMenuItem(request));
    }

    @Test
    @DisplayName("5. Authoritative backend pricing: client cannot tamper with variation price")
    void testAuthoritativePricingEnforced() {
        // Create Biryani: Half ₹120, Full ₹220
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Special Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("120.00")).isAvailable(true).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("220.00")).isAvailable(true).build()
                ))
                .build());

        Long halfVarId = biryani.getVariations().get(0).getId();

        // Order 2 Half portions (expected 2 * 120 = 240)
        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Collections.singletonList(
                OrderItemRequest.builder()
                        .menuItemId(biryani.getId())
                        .variationId(halfVarId)
                        .quantity(2)
                        .build()
        ));

        OrderResponse orderResp = orderService.createOrder(orderReq);
        assertNotNull(orderResp.getId());
        assertEquals(1, orderResp.getItems().size());
        OrderItemResponse item = orderResp.getItems().get(0);

        // Unit price MUST be 120.00 as stored in backend variation
        assertEquals(new BigDecimal("120.00"), item.getUnitPrice());
        assertEquals(new BigDecimal("240.00"), item.getSubtotal());
        assertEquals("Half", item.getVariationName());
        assertEquals(halfVarId, item.getVariationId());
    }

    @Test
    @DisplayName("6. Half and Full variations are separate order lines and not incorrectly deduplicated")
    void testHalfAndFullSeparateOrderLines() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Dum Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("130.00")).isAvailable(true).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("240.00")).isAvailable(true).build()
                ))
                .build());

        Long halfVarId = biryani.getVariations().get(0).getId();
        Long fullVarId = biryani.getVariations().get(1).getId();

        // Order 2 Half, 1 Full, and an additional 1 Half
        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Arrays.asList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(halfVarId).quantity(2).build(),
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(fullVarId).quantity(1).build(),
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(halfVarId).quantity(1).build()
        ));

        OrderResponse orderResp = orderService.createOrder(orderReq);

        // Must result in exactly 2 lines: Half (qty 3) and Full (qty 1)
        assertEquals(2, orderResp.getItems().size());

        OrderItemResponse halfLine = orderResp.getItems().stream()
                .filter(i -> "Half".equals(i.getVariationName())).findFirst().orElseThrow();
        assertEquals(3, halfLine.getQuantity());
        assertEquals(new BigDecimal("130.00"), halfLine.getUnitPrice());
        assertEquals(new BigDecimal("390.00"), halfLine.getSubtotal());

        OrderItemResponse fullLine = orderResp.getItems().stream()
                .filter(i -> "Full".equals(i.getVariationName())).findFirst().orElseThrow();
        assertEquals(1, fullLine.getQuantity());
        assertEquals(new BigDecimal("240.00"), fullLine.getUnitPrice());
        assertEquals(new BigDecimal("240.00"), fullLine.getSubtotal());
    }

    @Test
    @DisplayName("7. Unavailable variation cannot be ordered")
    void testUnavailableVariationRejected() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Fish Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("150.00")).isAvailable(false).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("280.00")).isAvailable(true).build()
                ))
                .build());

        Long halfVarId = biryani.getVariations().get(0).getId();

        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Collections.singletonList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(halfVarId).quantity(1).build()
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(orderReq));
        assertTrue(ex.getMessage().contains("not available"));
    }

    @Test
    @DisplayName("8. Item with multiple variations requires selecting a variation")
    void testMultipleVariationsRequiresSelection() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Egg Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("110.00")).isAvailable(true).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("200.00")).isAvailable(true).build()
                ))
                .build());

        // Order without specifying variationId
        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Collections.singletonList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(null).quantity(1).build()
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(orderReq));
        assertTrue(ex.getMessage().contains("Please select a variation"));
    }

    @Test
    @DisplayName("9. Cross-restaurant variation access is rejected")
    void testCrossRestaurantVariationRejected() {
        // Create variation in Restaurant B
        when(securityUtils.getCurrentRestaurantId()).thenReturn(restaurantB.getId());
        MenuItemResponse itemB = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryB.getId())
                .name("Restaurant B Snack")
                .variations(Collections.singletonList(
                        MenuItemVariationRequest.builder().name("Regular").price(new BigDecimal("50.00")).isAvailable(true).build()
                ))
                .build());
        Long varIdB = itemB.getVariations().get(0).getId();

        // Switch context to Restaurant A and try to order using varIdB
        when(securityUtils.getCurrentRestaurantId()).thenReturn(restaurantA.getId());
        MenuItemResponse itemA = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Restaurant A Biryani")
                .price(new BigDecimal("100.00"))
                .build());

        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Collections.singletonList(
                OrderItemRequest.builder().menuItemId(itemA.getId()).variationId(varIdB).quantity(1).build()
        ));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(orderReq));
    }

    @Test
    @DisplayName("10. Existing menu items without variations work normally with NULL variation")
    void testLegacyMenuItemWithoutVariations() {
        MenuItemResponse regularItem = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Plain Rice")
                .price(new BigDecimal("60.00"))
                .isAvailable(true)
                .build());

        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Collections.singletonList(
                OrderItemRequest.builder().menuItemId(regularItem.getId()).quantity(2).build()
        ));

        OrderResponse orderResp = orderService.createOrder(orderReq);
        assertNotNull(orderResp.getId());
        assertEquals(1, orderResp.getItems().size());
        OrderItemResponse line = orderResp.getItems().get(0);
        assertEquals(new BigDecimal("60.00"), line.getUnitPrice());
        assertEquals(new BigDecimal("120.00"), line.getSubtotal());
        assertNull(line.getVariationId());
        assertNull(line.getVariationName());
    }

    @Test
    @DisplayName("11. Client Change 1: Waiter cannot decrease variation quantity or remove variation")
    void testWaiterCannotDecreaseVariationQuantity() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Kolkata Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("140.00")).isAvailable(true).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("260.00")).isAvailable(true).build()
                ))
                .build());

        Long halfVarId = biryani.getVariations().get(0).getId();
        Long fullVarId = biryani.getVariations().get(1).getId();

        // Create initial order with 2 Half and 2 Full
        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Arrays.asList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(halfVarId).quantity(2).build(),
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(fullVarId).quantity(2).build()
        ));

        OrderResponse created = orderService.createOrder(orderReq);

        // Switch to WAITER
        when(securityUtils.getCurrentUserId()).thenReturn(waiterUser.getId());
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.isAdmin()).thenReturn(false);

        // Attempt 1: Waiter tries to decrease Half from 2 to 1 -> AccessDeniedException
        OrderRequest updateReq1 = new OrderRequest();
        updateReq1.setItems(Arrays.asList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(halfVarId).quantity(1).build(),
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(fullVarId).quantity(2).build()
        ));

        assertThrows(AccessDeniedException.class, () -> orderService.updateOrder(created.getId(), updateReq1));

        // Attempt 2: Waiter tries to remove Full line completely -> AccessDeniedException
        OrderRequest updateReq2 = new OrderRequest();
        updateReq2.setItems(Collections.singletonList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(halfVarId).quantity(2).build()
        ));

        assertThrows(AccessDeniedException.class, () -> orderService.updateOrder(created.getId(), updateReq2));

        // Attempt 3: ADMIN can decrease or remove without issue
        when(securityUtils.getCurrentUserId()).thenReturn(adminUser.getId());
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(securityUtils.isAdmin()).thenReturn(true);

        OrderResponse adminUpdated = orderService.updateOrder(created.getId(), updateReq1);
        assertNotNull(adminUpdated);
    }

    @Test
    @DisplayName("12. Client Change 2: Waiter cannot cancel order with variations")
    void testWaiterCannotCancelOrderWithVariations() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Awadhi Biryani")
                .variations(Collections.singletonList(
                        MenuItemVariationRequest.builder().name("Standard").price(new BigDecimal("180.00")).isAvailable(true).build()
                ))
                .build());

        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Collections.singletonList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).quantity(1).build()
        ));

        OrderResponse created = orderService.createOrder(orderReq);

        // Switch to WAITER
        when(securityUtils.getCurrentUserId()).thenReturn(waiterUser.getId());
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.isAdmin()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> orderService.cancelOrder(created.getId()));
    }

    @Test
    @DisplayName("13. Variation price snapshot immutability: changing menu price preserves order item snapshot")
    void testVariationPriceSnapshotImmutability() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Hyderabadi Dum Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("120.00")).isAvailable(true).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("220.00")).isAvailable(true).build()
                ))
                .build());

        Long halfVarId = biryani.getVariations().get(0).getId();

        // Place order with Half at ₹120.00
        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Collections.singletonList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(halfVarId).quantity(2).build()
        ));

        OrderResponse created = orderService.createOrder(orderReq);
        assertEquals(new BigDecimal("120.00"), created.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("240.00"), created.getItems().get(0).getSubtotal());

        // Now, update Menu: increase Half to ₹160.00
        MenuItemVariationRequest updatedHalf = MenuItemVariationRequest.builder()
                .id(halfVarId)
                .name("Half")
                .price(new BigDecimal("160.00"))
                .isAvailable(true)
                .build();
        menuItemService.updateMenuItem(biryani.getId(), MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Hyderabadi Dum Biryani")
                .variations(Arrays.asList(updatedHalf, MenuItemVariationRequest.builder().id(biryani.getVariations().get(1).getId()).name("Full").price(new BigDecimal("220.00")).isAvailable(true).build()))
                .build());

        // Verify that existing order item price snapshot was NOT altered
        List<OrderItem> existingItems = orderItemRepository.findByOrderId(created.getId());
        assertEquals(1, existingItems.size());
        OrderItem existingItem = existingItems.get(0);
        assertEquals(new BigDecimal("120.00"), existingItem.getUnitPrice());
        assertEquals(new BigDecimal("240.00"), existingItem.getSubtotal());
        assertEquals("Half", existingItem.getVariationName());
    }

    @Test
    @DisplayName("14. Billing and GST calculations on orders with variations")
    void testBillingAndGstCalculationsWithVariations() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Special Mutton Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("120.00")).isAvailable(true).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("220.00")).isAvailable(true).build()
                ))
                .build());

        Long halfVarId = biryani.getVariations().get(0).getId();
        Long fullVarId = biryani.getVariations().get(1).getId();

        // 1 Half (₹120) + 1 Full (₹220) = subtotal ₹340.00
        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Arrays.asList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(halfVarId).quantity(1).build(),
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(fullVarId).quantity(1).build()
        ));

        OrderResponse created = orderService.createOrder(orderReq);
        assertEquals(new BigDecimal("340.00"), created.getSubtotal());
        // GST_EXCLUDED with 2.50% CGST and 2.50% SGST
        // CGST = 340 * 0.025 = 8.50, SGST = 8.50, Total = 357.00
        assertEquals(new BigDecimal("8.50"), created.getCgstAmount());
        assertEquals(new BigDecimal("8.50"), created.getSgstAmount());
        assertEquals(new BigDecimal("17.00"), created.getTaxAmount());
        assertEquals(new BigDecimal("357.00"), created.getTotalAmount());

        // Advance to READY
        kitchenService.acceptOrder(created.getId());
        kitchenService.prepareOrder(created.getId());
        kitchenService.readyOrder(created.getId());

        // Create Bill
        BillResponse bill = billService.createBill(new BillRequest(created.getId()));
        assertNotNull(bill.getId());
        assertEquals(new BigDecimal("340.00"), bill.getSubtotal());
        assertEquals(new BigDecimal("8.50"), bill.getCgstAmount());
        assertEquals(new BigDecimal("8.50"), bill.getSgstAmount());
        assertEquals(new BigDecimal("17.00"), bill.getTaxAmount());
        assertEquals(new BigDecimal("357.00"), bill.getTotalAmount());
        assertEquals("GST_EXCLUDED", bill.getGstMode());
    }

    @Test
    @DisplayName("15. KDS variation display and lifecycle handling")
    void testKdsVariationDisplayAndKitchenLifecycle() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Chef Special Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("150.00")).isAvailable(true).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("280.00")).isAvailable(true).build()
                ))
                .build());

        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Arrays.asList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(biryani.getVariations().get(0).getId()).quantity(1).build(),
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(biryani.getVariations().get(1).getId()).quantity(2).build()
        ));

        OrderResponse created = orderService.createOrder(orderReq);

        // Fetch Kitchen orders
        List<KitchenOrderResponse> kitchenOrders = kitchenService.getKitchenOrders("ALL");
        KitchenOrderResponse kOrder = kitchenOrders.stream()
                .filter(ko -> ko.getOrderId().equals(created.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, kOrder.getItems().size());
        KitchenOrderItemResponse kItemHalf = kOrder.getItems().stream()
                .filter(i -> "Half".equals(i.getVariationName()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, kItemHalf.getQuantity());

        KitchenOrderItemResponse kItemFull = kOrder.getItems().stream()
                .filter(i -> "Full".equals(i.getVariationName()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, kItemFull.getQuantity());

        // Advance through kitchen
        KitchenOrderResponse accepted = kitchenService.acceptOrder(created.getId());
        assertEquals("ACCEPTED", accepted.getStatus());

        KitchenOrderResponse preparing = kitchenService.prepareOrder(created.getId());
        assertEquals("PREPARING", preparing.getStatus());

        KitchenOrderResponse ready = kitchenService.readyOrder(created.getId());
        assertEquals("READY", ready.getStatus());
    }

    @Test
    @DisplayName("16. Sales reports and receipt line formatting with variations")
    void testSalesReportAndBillItemsWithVariations() {
        MenuItemResponse biryani = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Lucknowi Biryani")
                .variations(Arrays.asList(
                        MenuItemVariationRequest.builder().name("Half").price(new BigDecimal("120.00")).isAvailable(true).build(),
                        MenuItemVariationRequest.builder().name("Full").price(new BigDecimal("220.00")).isAvailable(true).build()
                ))
                .build());

        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Arrays.asList(
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(biryani.getVariations().get(0).getId()).quantity(1).build(),
                OrderItemRequest.builder().menuItemId(biryani.getId()).variationId(biryani.getVariations().get(1).getId()).quantity(1).build()
        ));

        OrderResponse created = orderService.createOrder(orderReq);

        // Advance to READY, Bill, and Pay
        kitchenService.acceptOrder(created.getId());
        kitchenService.prepareOrder(created.getId());
        kitchenService.readyOrder(created.getId());

        BillResponse bill = billService.createBill(new BillRequest(created.getId()));

        PaymentResponse payment = billService.processPayment(bill.getId(),
                new PaymentRequest(new BigDecimal("357.00"), "CASH", "CASH-001"));
        assertNotNull(payment.getId());

        // Check Bill with Items from ReportService
        List<ReportBillResponse> reportBills = reportService.getReportBills(LocalDate.now(), LocalDate.now());
        ReportBillResponse billDetails = reportBills.stream()
                .filter(b -> b.getBillId().equals(bill.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, billDetails.getItems().size());

        assertTrue(billDetails.getItems().stream()
                .anyMatch(i -> "Lucknowi Biryani (Half)".equals(i.getName()) && "Half".equals(i.getVariationName())));
        assertTrue(billDetails.getItems().stream()
                .anyMatch(i -> "Lucknowi Biryani (Full)".equals(i.getName()) && "Full".equals(i.getVariationName())));

        // Check Sales Report
        SalesReportResponse salesReport = reportService.getSalesReport(LocalDate.now(), LocalDate.now());
        assertEquals(new BigDecimal("357.00"), salesReport.getTotalRevenue());
        assertEquals(new BigDecimal("17.00"), salesReport.getTotalTax());
    }

    @Test
    @DisplayName("17. Historical order items without variations handled gracefully without NPE")
    void testHistoricalOrderItemWithoutVariation() {
        MenuItemResponse regularItem = menuItemService.createMenuItem(MenuItemRequest.builder()
                .categoryId(categoryA.getId())
                .name("Jeera Rice")
                .price(new BigDecimal("80.00"))
                .isAvailable(true)
                .build());

        OrderRequest orderReq = new OrderRequest();
        orderReq.setOrderType("DINE_IN");
        orderReq.setTableId(tableA.getId());
        orderReq.setItems(Collections.singletonList(
                OrderItemRequest.builder().menuItemId(regularItem.getId()).quantity(1).build()
        ));

        OrderResponse created = orderService.createOrder(orderReq);
        assertNull(created.getItems().get(0).getVariationId());
        assertNull(created.getItems().get(0).getVariationName());

        // KDS handles null variationName gracefully
        List<KitchenOrderResponse> kitchenOrders = kitchenService.getKitchenOrders("ALL");
        KitchenOrderResponse kOrder = kitchenOrders.stream()
                .filter(ko -> ko.getOrderId().equals(created.getId()))
                .findFirst()
                .orElseThrow();
        assertNull(kOrder.getItems().get(0).getVariationName());

        // Advance to READY, Bill, and Pay
        kitchenService.acceptOrder(created.getId());
        kitchenService.prepareOrder(created.getId());
        kitchenService.readyOrder(created.getId());

        BillResponse bill = billService.createBill(new BillRequest(created.getId()));
        assertNotNull(bill.getId());

        billService.processPayment(bill.getId(),
                new PaymentRequest(new BigDecimal("84.00"), "CASH", "CASH-002"));

        List<ReportBillResponse> reportBills = reportService.getReportBills(LocalDate.now(), LocalDate.now());
        ReportBillResponse billDetails = reportBills.stream()
                .filter(b -> b.getBillId().equals(bill.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, billDetails.getItems().size());
        assertEquals("Jeera Rice", billDetails.getItems().get(0).getName());
        assertNull(billDetails.getItems().get(0).getVariationName());
    }
}
