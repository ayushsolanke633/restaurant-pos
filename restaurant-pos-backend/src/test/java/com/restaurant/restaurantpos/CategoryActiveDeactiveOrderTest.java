package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.Category;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.NotificationService;
import com.restaurant.restaurantpos.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CategoryActiveDeactiveOrderTest {

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

    @InjectMocks
    private OrderService orderService;

    private Restaurant restaurant;
    private Category activeCategory;
    private Category inactiveCategory;
    private MenuItem activeItem;
    private MenuItem inactiveCategoryItem;
    private RestaurantTable table;
    private User waiter;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);

        waiter = new User();
        waiter.setId(2L);
        waiter.setRestaurant(restaurant);

        activeCategory = new Category();
        activeCategory.setId(10L);
        activeCategory.setName("Beverages");
        activeCategory.setRestaurant(restaurant);
        activeCategory.setIsActive(true);

        inactiveCategory = new Category();
        inactiveCategory.setId(20L);
        inactiveCategory.setName("Seasonal");
        inactiveCategory.setRestaurant(restaurant);
        inactiveCategory.setIsActive(false);

        activeItem = new MenuItem();
        activeItem.setId(100L);
        activeItem.setName("Coffee");
        activeItem.setPrice(new BigDecimal("5.00"));
        activeItem.setRestaurant(restaurant);
        activeItem.setCategory(activeCategory);
        activeItem.setIsAvailable(true);

        inactiveCategoryItem = new MenuItem();
        inactiveCategoryItem.setId(200L);
        inactiveCategoryItem.setName("Pumpkin Spice Latte");
        inactiveCategoryItem.setPrice(new BigDecimal("7.00"));
        inactiveCategoryItem.setRestaurant(restaurant);
        inactiveCategoryItem.setCategory(inactiveCategory);
        inactiveCategoryItem.setIsAvailable(true);

        table = new RestaurantTable();
        table.setId(5L);
        table.setTableNumber(5);
        table.setRestaurant(restaurant);
        table.setStatus("AVAILABLE");
    }

    // 1. Active category + available menu item: order creation succeeds
    @Test
    void testCreateOrder_ActiveCategory_Success() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(waiter));
        when(menuItemRepository.findById(100L)).thenReturn(Optional.of(activeItem));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(1000L);
            return o;
        });
        when(orderItemRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setMenuItemId(100L);
        itemReq.setQuantity(2);

        OrderRequest request = new OrderRequest();
        request.setOrderType("DINE_IN");
        request.setTableId(5L);
        request.setItems(List.of(itemReq));

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("10.00"), response.getSubtotal());
        verify(orderRepository).save(any(Order.class));
    }

    // 2. Inactive category item: new order creation is rejected by backend
    @Test
    void testCreateOrder_InactiveCategory_ThrowsException() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(waiter));
        when(menuItemRepository.findById(200L)).thenReturn(Optional.of(inactiveCategoryItem));

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setMenuItemId(200L);
        itemReq.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setOrderType("DINE_IN");
        request.setTableId(5L);
        request.setItems(List.of(itemReq));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(request));
        assertTrue(ex.getMessage().contains("category is inactive"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    // 3. Inactive category item: add-on request is rejected by backend
    @Test
    void testAddItemsToOrder_InactiveCategory_ThrowsException() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order existingOrder = new Order();
        existingOrder.setId(1000L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setStatus("NEW");
        existingOrder.setOrderType("DINE_IN");
        User waiter = new User();
        waiter.setId(2L);
        existingOrder.setWaiter(waiter);

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(existingOrder));
        when(billRepository.existsByOrderId(1000L)).thenReturn(false);
        when(menuItemRepository.findById(200L)).thenReturn(Optional.of(inactiveCategoryItem));

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setMenuItemId(200L);
        itemReq.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(itemReq));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.addItemsToOrder(1000L, request));
        assertTrue(ex.getMessage().contains("category is inactive"));
        verify(orderItemRepository, never()).saveAll(anyList());
    }

    // 4. Order modification cannot introduce an inactive-category item
    @Test
    void testUpdateOrder_InactiveCategory_ThrowsException() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        Order existingOrder = new Order();
        existingOrder.setId(1000L);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setStatus("NEW");
        existingOrder.setOrderType("DINE_IN");
        User waiter = new User();
        waiter.setId(2L);
        existingOrder.setWaiter(waiter);

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(existingOrder));
        when(menuItemRepository.findById(200L)).thenReturn(Optional.of(inactiveCategoryItem));

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setMenuItemId(200L);
        itemReq.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(itemReq));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.updateOrder(1000L, request));
        assertTrue(ex.getMessage().contains("category is inactive"));
        verify(orderItemRepository, never()).saveAll(anyList());
    }

    // 5. Reactivated category becomes orderable again
    @Test
    void testCreateOrder_ReactivatedCategory_Success() {
        inactiveCategory.setIsActive(true); // Reactivate category

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(waiter));
        when(menuItemRepository.findById(200L)).thenReturn(Optional.of(inactiveCategoryItem));
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurant);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(1001L);
            return o;
        });
        when(orderItemRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setMenuItemId(200L);
        itemReq.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setOrderType("DINE_IN");
        request.setTableId(5L);
        request.setItems(List.of(itemReq));

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("7.00"), response.getSubtotal());
    }

    // 6. Mixed request containing active and inactive items throws exception and saves nothing
    @Test
    void testCreateOrder_MixedItems_ThrowsAndRollsBack() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");

        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));
        when(orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(waiter));
        when(menuItemRepository.findById(100L)).thenReturn(Optional.of(activeItem));
        when(menuItemRepository.findById(200L)).thenReturn(Optional.of(inactiveCategoryItem));

        OrderItemRequest itemReq1 = new OrderItemRequest();
        itemReq1.setMenuItemId(100L);
        itemReq1.setQuantity(1);

        OrderItemRequest itemReq2 = new OrderItemRequest();
        itemReq2.setMenuItemId(200L);
        itemReq2.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setOrderType("DINE_IN");
        request.setTableId(5L);
        request.setItems(List.of(itemReq1, itemReq2));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createOrder(request));
        assertTrue(ex.getMessage().contains("category is inactive"));
        verify(orderRepository, never()).save(any(Order.class));
    }
}
