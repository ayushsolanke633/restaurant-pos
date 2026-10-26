package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.NotificationService;
import com.restaurant.restaurantpos.service.OrderService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Stage3D2OrderGstCalculationUnitTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private RestaurantTableRepository tableRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private KitchenOrderEventPublisher eventPublisher;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BillRepository billRepository;
    @Mock
    private RestaurantSettingsRepository settingsRepository;

    @InjectMocks
    private OrderService orderService;

    private Restaurant restaurant;
    private RestaurantTable table;
    private User adminUser;
    private MenuItem menuItem100;
    private MenuItem menuItem105;
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

        menuItem100 = new MenuItem();
        menuItem100.setId(501L);
        menuItem100.setName("Paneer Butter Masala");
        menuItem100.setPrice(new BigDecimal("100.00"));
        menuItem100.setIsAvailable(true);
        menuItem100.setRestaurant(restaurant);

        menuItem105 = new MenuItem();
        menuItem105.setId(502L);
        menuItem105.setName("Biryani Special");
        menuItem105.setPrice(new BigDecimal("105.00"));
        menuItem105.setIsAvailable(true);
        menuItem105.setRestaurant(restaurant);

        settings = new RestaurantSettings();
        settings.setId(20L);
        settings.setRestaurant(restaurant);
        settings.setTaxEnabled(true);
        settings.setDefaultTaxRate(new BigDecimal("5.00"));
        settings.setCgstRate(new BigDecimal("2.50"));
        settings.setSgstRate(new BigDecimal("2.50"));
        settings.setGstMode("GST_EXCLUDED");
        settings.setKitchenEnabled(true);

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(99L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(securityUtils.isAdmin()).thenReturn(true);

        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(tableRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(table));
        when(menuItemRepository.findByIdAndRestaurantId(501L, 1L)).thenReturn(Optional.of(menuItem100));
        when(menuItemRepository.findByIdAndRestaurantId(502L, 1L)).thenReturn(Optional.of(menuItem105));
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(settings));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(1001L);
            }
            return o;
        });

        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("GST_EXCLUDED with 5% total rate: adds CGST 2.5% and SGST 2.5% to subtotal")
    void testOrderCreationGstExcluded5Percent() {
        settings.setGstMode("GST_EXCLUDED");
        settings.setDefaultTaxRate(new BigDecimal("5.00"));
        settings.setCgstRate(new BigDecimal("2.50"));
        settings.setSgstRate(new BigDecimal("2.50"));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(Collections.singletonList(new OrderItemRequest(501L, 2, "Mild spicy"))); // 2 * 100 = 200.00

        OrderResponse res = orderService.createOrder(req);

        assertNotNull(res);
        assertEquals(new BigDecimal("200.00"), res.getSubtotal());
        assertEquals("GST_EXCLUDED", res.getGstMode());
        assertEquals(new BigDecimal("2.50"), res.getCgstRate());
        assertEquals(new BigDecimal("2.50"), res.getSgstRate());
        assertEquals(new BigDecimal("5.00"), res.getCgstAmount());
        assertEquals(new BigDecimal("5.00"), res.getSgstAmount());
        assertEquals(new BigDecimal("10.00"), res.getTaxAmount());
        assertEquals(new BigDecimal("210.00"), res.getTotalAmount());
        assertEquals(res.getTaxAmount(), res.getCgstAmount().add(res.getSgstAmount()));
    }

    @Test
    @DisplayName("GST_INCLUDED with 5% total rate: extracts tax from gross without increasing total")
    void testOrderCreationGstIncluded5Percent() {
        settings.setGstMode("GST_INCLUDED");
        settings.setDefaultTaxRate(new BigDecimal("5.00"));
        settings.setCgstRate(new BigDecimal("2.50"));
        settings.setSgstRate(new BigDecimal("2.50"));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(Collections.singletonList(new OrderItemRequest(502L, 1, null))); // 1 * 105 = 105.00

        OrderResponse res = orderService.createOrder(req);

        assertNotNull(res);
        assertEquals(new BigDecimal("105.00"), res.getSubtotal());
        assertEquals("GST_INCLUDED", res.getGstMode());
        assertEquals(new BigDecimal("2.50"), res.getCgstRate());
        assertEquals(new BigDecimal("2.50"), res.getSgstRate());
        assertEquals(new BigDecimal("2.50"), res.getCgstAmount());
        assertEquals(new BigDecimal("2.50"), res.getSgstAmount());
        assertEquals(new BigDecimal("5.00"), res.getTaxAmount());
        assertEquals(new BigDecimal("105.00"), res.getTotalAmount(), "GST_INCLUDED total payable must equal gross amount");
        assertEquals(res.getTaxAmount(), res.getCgstAmount().add(res.getSgstAmount()));
    }

    @Test
    @DisplayName("Tax disabled: tax amounts are 0.00 while preserving saved settings rates/mode")
    void testOrderCreationTaxDisabled() {
        settings.setTaxEnabled(false);
        settings.setGstMode("GST_EXCLUDED");
        settings.setDefaultTaxRate(new BigDecimal("18.00"));
        settings.setCgstRate(new BigDecimal("9.00"));
        settings.setSgstRate(new BigDecimal("9.00"));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(Collections.singletonList(new OrderItemRequest(501L, 1, null))); // 100.00

        OrderResponse res = orderService.createOrder(req);

        assertEquals(new BigDecimal("100.00"), res.getSubtotal());
        assertEquals(new BigDecimal("0.00"), res.getTaxAmount());
        assertEquals(new BigDecimal("0.00"), res.getCgstAmount());
        assertEquals(new BigDecimal("0.00"), res.getSgstAmount());
        assertEquals(new BigDecimal("100.00"), res.getTotalAmount());
        assertEquals("GST_EXCLUDED", res.getGstMode());
        assertEquals(new BigDecimal("9.00"), res.getCgstRate());
        assertEquals(new BigDecimal("9.00"), res.getSgstRate());
    }

    @Test
    @DisplayName("Zero rate: defaultTaxRate = 0.00 yields zero tax amounts and exact subtotal total")
    void testOrderCreationZeroGstRate() {
        settings.setTaxEnabled(true);
        settings.setGstMode("GST_EXCLUDED");
        settings.setDefaultTaxRate(new BigDecimal("0.00"));
        settings.setCgstRate(new BigDecimal("0.00"));
        settings.setSgstRate(new BigDecimal("0.00"));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(Collections.singletonList(new OrderItemRequest(501L, 2, null))); // 200.00

        OrderResponse res = orderService.createOrder(req);

        assertEquals(new BigDecimal("200.00"), res.getSubtotal());
        assertEquals(new BigDecimal("0.00"), res.getTaxAmount());
        assertEquals(new BigDecimal("0.00"), res.getCgstAmount());
        assertEquals(new BigDecimal("0.00"), res.getSgstAmount());
        assertEquals(new BigDecimal("200.00"), res.getTotalAmount());
    }

    @Test
    @DisplayName("Odd-hundredth rate 5.05%: splits to 2.53% CGST and 2.52% SGST with exact penny precision")
    void testOddHundredthRateOrderCreation() {
        settings.setGstMode("GST_EXCLUDED");
        settings.setDefaultTaxRate(new BigDecimal("5.05"));
        settings.setCgstRate(new BigDecimal("2.53"));
        settings.setSgstRate(new BigDecimal("2.52"));

        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(10L);
        req.setItems(Collections.singletonList(new OrderItemRequest(501L, 10, null))); // 1000.00

        OrderResponse res = orderService.createOrder(req);

        assertEquals(new BigDecimal("1000.00"), res.getSubtotal());
        assertEquals(new BigDecimal("25.30"), res.getCgstAmount());
        assertEquals(new BigDecimal("25.20"), res.getSgstAmount());
        assertEquals(new BigDecimal("50.50"), res.getTaxAmount());
        assertEquals(new BigDecimal("1050.50"), res.getTotalAmount());
        assertEquals(res.getTaxAmount(), res.getCgstAmount().add(res.getSgstAmount()));
    }

    @Test
    @DisplayName("Order update: recalculates GST amounts and total when items change")
    void testOrderUpdateRecalculatesGst() {
        settings.setGstMode("GST_EXCLUDED");
        settings.setDefaultTaxRate(new BigDecimal("5.00"));
        settings.setCgstRate(new BigDecimal("2.50"));
        settings.setSgstRate(new BigDecimal("2.50"));

        Order existingOrder = new Order();
        existingOrder.setId(101L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setTable(table);
        existingOrder.setOrderType("DINE_IN");
        existingOrder.setStatus("NEW");
        existingOrder.setSubtotal(new BigDecimal("100.00"));
        existingOrder.setTaxAmount(new BigDecimal("5.00"));
        existingOrder.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        existingOrder.setTotalAmount(new BigDecimal("105.00"));

        when(orderRepository.findByIdAndRestaurantIdForUpdate(101L, 1L)).thenReturn(Optional.of(existingOrder));
        when(orderRepository.findByIdAndRestaurantId(101L, 1L)).thenReturn(Optional.of(existingOrder));

        // Update items to 3 items * 100.00 = 300.00
        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(10L);
        updateReq.setItems(Collections.singletonList(new OrderItemRequest(501L, 3, "Updated note")));

        OrderResponse res = orderService.updateOrder(101L, updateReq);

        assertEquals(new BigDecimal("300.00"), res.getSubtotal());
        assertEquals(new BigDecimal("7.50"), res.getCgstAmount());
        assertEquals(new BigDecimal("7.50"), res.getSgstAmount());
        assertEquals(new BigDecimal("15.00"), res.getTaxAmount());
        assertEquals(new BigDecimal("315.00"), res.getTotalAmount());
        assertEquals(res.getTaxAmount(), res.getCgstAmount().add(res.getSgstAmount()));
    }

    @Test
    @DisplayName("Add items to order: recalculates GST and totals across all items")
    void testAddItemsToOrderRecalculatesGst() {
        settings.setGstMode("GST_EXCLUDED");
        settings.setDefaultTaxRate(new BigDecimal("5.00"));
        settings.setCgstRate(new BigDecimal("2.50"));
        settings.setSgstRate(new BigDecimal("2.50"));

        Order existingOrder = new Order();
        existingOrder.setId(201L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setTable(table);
        existingOrder.setOrderType("DINE_IN");
        existingOrder.setStatus("NEW");
        existingOrder.setSubtotal(new BigDecimal("100.00"));
        existingOrder.setTaxAmount(new BigDecimal("5.00"));
        existingOrder.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        existingOrder.setTotalAmount(new BigDecimal("105.00"));
        existingOrder.setGstMode("GST_EXCLUDED");
        existingOrder.setCgstRate(new BigDecimal("2.50"));
        existingOrder.setSgstRate(new BigDecimal("2.50"));
        existingOrder.setCgstAmount(new BigDecimal("2.50"));
        existingOrder.setSgstAmount(new BigDecimal("2.50"));

        OrderItem existingItem = new OrderItem();
        existingItem.setId(1L);
        existingItem.setOrder(existingOrder);
        existingItem.setRestaurant(restaurant);
        existingItem.setMenuItem(menuItem100);
        existingItem.setQuantity(1);
        existingItem.setUnitPrice(new BigDecimal("100.00"));
        existingItem.setSubtotal(new BigDecimal("100.00"));
        existingItem.setStatus("NEW");
        existingItem.setBatchNumber(1);

        OrderItem addedItem = new OrderItem();
        addedItem.setId(2L);
        addedItem.setOrder(existingOrder);
        addedItem.setRestaurant(restaurant);
        addedItem.setMenuItem(menuItem100);
        addedItem.setQuantity(2);
        addedItem.setUnitPrice(new BigDecimal("100.00"));
        addedItem.setSubtotal(new BigDecimal("200.00"));
        addedItem.setStatus("NEW");
        addedItem.setBatchNumber(2);

        when(orderRepository.findByIdAndRestaurantIdForUpdate(201L, 1L)).thenReturn(Optional.of(existingOrder));
        when(orderRepository.findByIdAndRestaurantId(201L, 1L)).thenReturn(Optional.of(existingOrder));
        when(orderItemRepository.findByOrderId(201L)).thenReturn(new ArrayList<>(Collections.singletonList(existingItem)));
        when(orderItemRepository.findByOrderIdAndRestaurantId(201L, 1L)).thenReturn(Arrays.asList(existingItem, addedItem));

        OrderRequest addReq = new OrderRequest();
        addReq.setOrderType("DINE_IN");
        addReq.setTableId(10L);
        addReq.setItems(Collections.singletonList(new OrderItemRequest(501L, 2, "Second round")));

        OrderResponse res = orderService.addItemsToOrder(201L, addReq);

        assertEquals(new BigDecimal("300.00"), res.getSubtotal());
        assertEquals(new BigDecimal("7.50"), res.getCgstAmount());
        assertEquals(new BigDecimal("7.50"), res.getSgstAmount());
        assertEquals(new BigDecimal("15.00"), res.getTaxAmount());
        assertEquals(new BigDecimal("315.00"), res.getTotalAmount());
        assertEquals(res.getTaxAmount(), res.getCgstAmount().add(res.getSgstAmount()));
    }

    @Test
    @DisplayName("Legacy orders with null GST mode/rates remain safe and are not recalculated merely by reading or status transition")
    void testLegacyOrderPreservedWithoutRecalculation() {
        Order legacyOrder = new Order();
        legacyOrder.setId(901L);
        legacyOrder.setRestaurant(restaurant);
        legacyOrder.setTable(table);
        legacyOrder.setOrderType("DINE_IN");
        legacyOrder.setStatus("NEW");
        legacyOrder.setSubtotal(new BigDecimal("500.00"));
        legacyOrder.setTaxAmount(new BigDecimal("25.00"));
        legacyOrder.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        legacyOrder.setTotalAmount(new BigDecimal("525.00"));
        legacyOrder.setGstMode(null);
        legacyOrder.setCgstRate(null);
        legacyOrder.setSgstRate(null);
        legacyOrder.setCgstAmount(new BigDecimal("0.00"));
        legacyOrder.setSgstAmount(new BigDecimal("0.00"));

        when(orderRepository.findByIdAndRestaurantId(901L, 1L)).thenReturn(Optional.of(legacyOrder));
        when(orderItemRepository.findByOrderIdAndRestaurantId(901L, 1L)).thenReturn(Collections.emptyList());

        // 1. Read legacy order
        OrderResponse readRes = orderService.getOrderById(901L);
        assertNull(readRes.getGstMode(), "Legacy GST mode must remain null on read");
        assertNull(readRes.getCgstRate(), "Legacy CGST rate must remain null on read");
        assertNull(readRes.getSgstRate(), "Legacy SGST rate must remain null on read");
        assertEquals(new BigDecimal("25.00"), readRes.getTaxAmount(), "Historical tax amount must not be altered");
        assertEquals(new BigDecimal("525.00"), readRes.getTotalAmount(), "Historical total amount must not be altered");

        // 2. Status change on legacy order
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.getReferenceById(99L)).thenReturn(adminUser);
        OrderResponse acceptRes = orderService.acceptOrder(901L);

        assertEquals("ACCEPTED", acceptRes.getStatus());
        assertNull(acceptRes.getGstMode(), "Legacy GST mode must remain null after status transition");
        assertNull(acceptRes.getCgstRate(), "Legacy CGST rate must remain null after status transition");
        assertNull(acceptRes.getSgstRate(), "Legacy SGST rate must remain null after status transition");
        assertEquals(new BigDecimal("25.00"), acceptRes.getTaxAmount());
        assertEquals(new BigDecimal("525.00"), acceptRes.getTotalAmount());
    }
}
