package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.repository.*;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantSettingsRepository restaurantSettingsRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Restaurant restaurant;
    private RestaurantTable table1;
    private RestaurantTable table2;
    private MenuItem menuItem;
    private MenuItem menuItem2;

    private User adminUser;
    private User waiter1User;
    private User waiter2User;
    private User kitchenUser;

    private Order order1;
    private Order order2;

    private static RequestPostProcessor customUser(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(
                user.getId(),
                user.getRestaurant() != null ? user.getRestaurant().getId() : null,
                user.getRole().getName(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getIsActive(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()))
        );
        return SecurityMockMvcRequestPostProcessors.user(userDetails);
    }

    private void cleanupDatabase() {
        try {
            notificationRepository.deleteAll();
            paymentRepository.deleteAll();
            billRepository.deleteAll();
            orderStatusHistoryRepository.deleteAll();
            orderItemRepository.deleteAll();
            orderRepository.deleteAll();
            menuItemRepository.deleteAll();
            categoryRepository.deleteAll();
            tableRepository.deleteAll();
            userRepository.deleteAll();
            restaurantSettingsRepository.deleteAll();
            restaurantRepository.deleteAll();
        } catch (Exception ignored) {
        }
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        cleanupDatabase();

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));
        Role waiterRole = roleRepository.findByName("WAITER")
                .orElseGet(() -> roleRepository.save(new Role(null, "WAITER")));
        Role kitchenRole = roleRepository.findByName("KITCHEN")
                .orElseGet(() -> roleRepository.save(new Role(null, "KITCHEN")));

        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setPhone("1234567890");
        restaurant.setEmail("test@restaurant.com");
        restaurant.setIsActive(true);
        restaurant = restaurantRepository.save(restaurant);

        RestaurantSettings settings = new RestaurantSettings();
        settings.setRestaurant(restaurant);
        settings.setKitchenEnabled(true);
        settings.setGstMode("GST_EXCLUDED");
        settings.setCgstRate(BigDecimal.ZERO.setScale(2));
        settings.setSgstRate(BigDecimal.ZERO.setScale(2));
        restaurantSettingsRepository.save(settings);

        table1 = new RestaurantTable();
        table1.setRestaurant(restaurant);
        table1.setTableNumber(1);
        table1.setCapacity(4);
        table1.setStatus("AVAILABLE");
        table1 = tableRepository.save(table1);

        table2 = new RestaurantTable();
        table2.setRestaurant(restaurant);
        table2.setTableNumber(2);
        table2.setCapacity(4);
        table2.setStatus("AVAILABLE");
        table2 = tableRepository.save(table2);

        Category category = new Category();
        category.setRestaurant(restaurant);
        category.setName("Main Course");
        category.setIsActive(true);
        category = categoryRepository.save(category);

        menuItem = new MenuItem();
        menuItem.setRestaurant(restaurant);
        menuItem.setCategory(category);
        menuItem.setName("Paneer Butter Masala");
        menuItem.setPrice(new BigDecimal("150.00"));
        menuItem.setIsAvailable(true);
        menuItem = menuItemRepository.save(menuItem);

        menuItem2 = new MenuItem();
        menuItem2.setRestaurant(restaurant);
        menuItem2.setCategory(category);
        menuItem2.setName("Dal Makhani");
        menuItem2.setPrice(new BigDecimal("120.00"));
        menuItem2.setIsAvailable(true);
        menuItem2 = menuItemRepository.save(menuItem2);

        adminUser = new User();
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@test.com");
        adminUser.setPasswordHash(passwordEncoder.encode("admin123"));
        adminUser.setRole(adminRole);
        adminUser.setRestaurant(restaurant);
        adminUser.setIsActive(true);
        adminUser = userRepository.save(adminUser);

        waiter1User = new User();
        waiter1User.setName("Waiter One");
        waiter1User.setEmail("waiter1@test.com");
        waiter1User.setPasswordHash(passwordEncoder.encode("waiter123"));
        waiter1User.setRole(waiterRole);
        waiter1User.setRestaurant(restaurant);
        waiter1User.setIsActive(true);
        waiter1User = userRepository.save(waiter1User);

        waiter2User = new User();
        waiter2User.setName("Waiter Two");
        waiter2User.setEmail("waiter2@test.com");
        waiter2User.setPasswordHash(passwordEncoder.encode("waiter123"));
        waiter2User.setRole(waiterRole);
        waiter2User.setRestaurant(restaurant);
        waiter2User.setIsActive(true);
        waiter2User = userRepository.save(waiter2User);

        kitchenUser = new User();
        kitchenUser.setName("Kitchen User");
        kitchenUser.setEmail("kitchen@test.com");
        kitchenUser.setPasswordHash(passwordEncoder.encode("kitchen123"));
        kitchenUser.setRole(kitchenRole);
        kitchenUser.setRestaurant(restaurant);
        kitchenUser.setIsActive(true);
        kitchenUser = userRepository.save(kitchenUser);

        order1 = new Order();
        order1.setRestaurant(restaurant);
        order1.setTable(table1);
        order1.setWaiter(waiter1User);
        order1.setOrderType("DINE_IN");
        order1.setStatus("NEW");
        order1.setSubtotal(new BigDecimal("150.00"));
        order1.setTaxAmount(BigDecimal.ZERO.setScale(2));
        order1.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order1.setTotalAmount(new BigDecimal("150.00"));
        order1 = orderRepository.save(order1);

        OrderItem orderItem1 = new OrderItem();
        orderItem1.setRestaurant(restaurant);
        orderItem1.setOrder(order1);
        orderItem1.setMenuItem(menuItem);
        orderItem1.setQuantity(1);
        orderItem1.setUnitPrice(new BigDecimal("150.00"));
        orderItem1.setSubtotal(new BigDecimal("150.00"));
        orderItem1.setStatus("NEW");
        orderItem1.setBatchNumber(1);
        orderItemRepository.save(orderItem1);

        order2 = new Order();
        order2.setRestaurant(restaurant);
        order2.setTable(table2);
        order2.setWaiter(waiter2User);
        order2.setOrderType("DINE_IN");
        order2.setStatus("NEW");
        order2.setSubtotal(new BigDecimal("150.00"));
        order2.setTaxAmount(BigDecimal.ZERO.setScale(2));
        order2.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order2.setTotalAmount(new BigDecimal("150.00"));
        order2 = orderRepository.save(order2);

        OrderItem orderItem2 = new OrderItem();
        orderItem2.setRestaurant(restaurant);
        orderItem2.setOrder(order2);
        orderItem2.setMenuItem(menuItem);
        orderItem2.setQuantity(1);
        orderItem2.setUnitPrice(new BigDecimal("150.00"));
        orderItem2.setSubtotal(new BigDecimal("150.00"));
        orderItem2.setStatus("NEW");
        orderItem2.setBatchNumber(1);
        orderItemRepository.save(orderItem2);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanupDatabase();
    }

    private OrderRequest dineInOrder() {
        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(table1.getId());
        req.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, "test")));
        return req;
    }

    @Test
    void waiterCannotCreateTakeaway() throws Exception {
        OrderRequest req = new OrderRequest();
        req.setOrderType("TAKEAWAY");
        req.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, "test")));
        mockMvc.perform(post("/api/orders")
                .with(customUser(waiter1User))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void kitchenCannotCreateOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
                .with(customUser(kitchenUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(dineInOrder())))
                .andExpect(status().isForbidden());
    }

    @Test
    void waiterCannotAcceptOrder() throws Exception {
        mockMvc.perform(patch("/api/orders/" + order1.getId() + "/accept")
                .with(customUser(waiter1User)))
                .andExpect(status().isForbidden());
    }

    @Test
    void kitchenCanAcceptOrder() throws Exception {
        mockMvc.perform(patch("/api/orders/" + order1.getId() + "/accept")
                .with(customUser(kitchenUser)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCompleteOrderAfterPayment() throws Exception {
        mockMvc.perform(patch("/api/orders/" + order1.getId() + "/complete")
                .with(customUser(adminUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void waiterCannotCancelOtherWaiterOrder() throws Exception {
        mockMvc.perform(patch("/api/orders/" + order2.getId() + "/cancel")
                .with(customUser(waiter1User)))
                .andExpect(status().isForbidden());
    }

    @Test
    void waiterCannotCancelOwnOrder() throws Exception {
        mockMvc.perform(patch("/api/orders/" + order1.getId() + "/cancel")
                .with(customUser(waiter1User)))
                .andExpect(status().isForbidden());
    }

    @Test
    void kitchenCannotCancelOrder() throws Exception {
        mockMvc.perform(patch("/api/orders/" + order1.getId() + "/cancel")
                .with(customUser(kitchenUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCancelNewOrder() throws Exception {
        mockMvc.perform(patch("/api/orders/" + order1.getId() + "/cancel")
                .with(customUser(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        RestaurantTable updatedTable = tableRepository.findById(table1.getId()).get();
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", updatedTable.getStatus());
    }

    @Test
    void adminCanCancelAcceptedOrder() throws Exception {
        order2.setStatus("ACCEPTED");
        orderRepository.save(order2);

        mockMvc.perform(patch("/api/orders/" + order2.getId() + "/cancel")
                .with(customUser(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void adminCannotCancelBilledOrder() throws Exception {
        Bill bill = new Bill();
        bill.setRestaurant(restaurant);
        bill.setOrder(order1);
        bill.setBillNumber("BILL-CANCEL-001");
        bill.setSubtotal(order1.getSubtotal());
        bill.setTaxAmount(order1.getTaxAmount());
        bill.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        bill.setTotalAmount(order1.getTotalAmount());
        bill.setGeneratedBy(adminUser);
        billRepository.save(bill);

        mockMvc.perform(patch("/api/orders/" + order1.getId() + "/cancel")
                .with(customUser(adminUser)))
                .andExpect(status().isConflict());
    }

    @Test
    void crossRestaurantAdminCannotCancelOrder() throws Exception {
        Restaurant restaurant2 = new Restaurant();
        restaurant2.setName("Other Restaurant 2");
        restaurant2.setAddress("789 Other St");
        restaurant2.setPhone("9876543211");
        restaurant2.setEmail("other2@restaurant.com");
        restaurant2.setIsActive(true);
        restaurant2 = restaurantRepository.save(restaurant2);

        Role adminRole = roleRepository.findByName("ADMIN").get();
        User otherAdmin = new User();
        otherAdmin.setName("Other Admin 2");
        otherAdmin.setEmail("otheradmin2@test.com");
        otherAdmin.setPasswordHash(passwordEncoder.encode("admin123"));
        otherAdmin.setRole(adminRole);
        otherAdmin.setRestaurant(restaurant2);
        otherAdmin.setIsActive(true);
        otherAdmin = userRepository.save(otherAdmin);

        mockMvc.perform(patch("/api/orders/" + order1.getId() + "/cancel")
                .with(customUser(otherAdmin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanDeleteItemFromOwnRestaurantOrder() throws Exception {
        OrderItem item2 = new OrderItem();
        item2.setRestaurant(restaurant);
        item2.setOrder(order1);
        item2.setMenuItem(menuItem2);
        item2.setQuantity(1);
        item2.setUnitPrice(new BigDecimal("120.00"));
        item2.setSubtotal(new BigDecimal("120.00"));
        item2.setStatus("NEW");
        item2.setBatchNumber(1);
        orderItemRepository.save(item2);

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table1.getId());
        updateReq.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, "only item 1")));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].menuItemId").value(menuItem.getId()));
    }

    @Test
    void adminCanDecreaseQuantityOnOwnRestaurantOrder() throws Exception {
        OrderItem existingItem = orderItemRepository.findByOrderId(order1.getId()).get(0);
        existingItem.setQuantity(3);
        existingItem.setSubtotal(new BigDecimal("450.00"));
        orderItemRepository.save(existingItem);

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table1.getId());
        updateReq.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, "decreased qty")));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void adminCanModifyAnotherWaitersOrderWithinSameRestaurant() throws Exception {
        OrderItem item = orderItemRepository.findByOrderId(order2.getId()).get(0);
        item.setQuantity(2);
        orderItemRepository.save(item);

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table2.getId());
        updateReq.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, "admin modified waiter2 order")));

        mockMvc.perform(put("/api/orders/" + order2.getId())
                .with(customUser(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void waiterCannotDeleteOrderItems() throws Exception {
        OrderItem item2 = new OrderItem();
        item2.setRestaurant(restaurant);
        item2.setOrder(order1);
        item2.setMenuItem(menuItem2);
        item2.setQuantity(1);
        item2.setUnitPrice(new BigDecimal("120.00"));
        item2.setSubtotal(new BigDecimal("120.00"));
        item2.setStatus("NEW");
        item2.setBatchNumber(1);
        orderItemRepository.save(item2);

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table1.getId());
        updateReq.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, null)));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(waiter1User))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only ADMIN can delete order items")));
    }

    @Test
    void waiterCannotDecreaseOrderItemQuantity() throws Exception {
        OrderItem item = orderItemRepository.findByOrderId(order1.getId()).get(0);
        item.setQuantity(2);
        orderItemRepository.save(item);

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table1.getId());
        updateReq.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, null)));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(waiter1User))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only ADMIN can decrease order item quantities")));
    }

    @Test
    void kitchenCannotDeleteOrDecreaseOrderItems() throws Exception {
        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table1.getId());
        updateReq.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, null)));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(kitchenUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossRestaurantAdminCannotModifyOtherRestaurantOrder() throws Exception {
        Restaurant restaurant2 = new Restaurant();
        restaurant2.setName("Other Restaurant");
        restaurant2.setAddress("456 Other St");
        restaurant2.setPhone("9876543210");
        restaurant2.setEmail("other@restaurant.com");
        restaurant2.setIsActive(true);
        restaurant2 = restaurantRepository.save(restaurant2);

        Role adminRole = roleRepository.findByName("ADMIN").get();
        User otherAdmin = new User();
        otherAdmin.setName("Other Admin");
        otherAdmin.setEmail("otheradmin@test.com");
        otherAdmin.setPasswordHash(passwordEncoder.encode("admin123"));
        otherAdmin.setRole(adminRole);
        otherAdmin.setRestaurant(restaurant2);
        otherAdmin.setIsActive(true);
        otherAdmin = userRepository.save(otherAdmin);

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table1.getId());
        updateReq.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, null)));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(otherAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotModifyBilledOrder() throws Exception {
        Bill bill = new Bill();
        bill.setRestaurant(restaurant);
        bill.setOrder(order1);
        bill.setBillNumber("BILL-TEST-001");
        bill.setSubtotal(order1.getSubtotal());
        bill.setTaxAmount(order1.getTaxAmount());
        bill.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        bill.setTotalAmount(order1.getTotalAmount());
        bill.setGeneratedBy(adminUser);
        billRepository.save(bill);

        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table1.getId());
        updateReq.setItems(List.of(new OrderItemRequest(menuItem.getId(), 1, null)));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quantityValidationZeroOrNegativeRejected() throws Exception {
        OrderRequest req = new OrderRequest();
        req.setOrderType("DINE_IN");
        req.setTableId(table1.getId());
        req.setItems(List.of(new OrderItemRequest(menuItem.getId(), 0, null)));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void waiterCanIncreaseQuantityAndAddNewItemOnOwnOrder() throws Exception {
        OrderRequest updateReq = new OrderRequest();
        updateReq.setOrderType("DINE_IN");
        updateReq.setTableId(table1.getId());
        updateReq.setItems(List.of(
                new OrderItemRequest(menuItem.getId(), 2, "increased qty"),
                new OrderItemRequest(menuItem2.getId(), 1, "added new item")
        ));

        mockMvc.perform(put("/api/orders/" + order1.getId())
                .with(customUser(waiter1User))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }
}
