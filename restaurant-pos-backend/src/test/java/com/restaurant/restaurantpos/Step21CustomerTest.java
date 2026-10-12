package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.CustomerRequest;
import com.restaurant.restaurantpos.dto.CustomerResponse;
import com.restaurant.restaurantpos.entity.Customer;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Step21CustomerTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private CustomerService customerService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private Customer customer1;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Restaurant A");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Restaurant B");

        customer1 = new Customer();
        customer1.setId(100L);
        customer1.setRestaurant(restaurantA);
        customer1.setName("Rahul Sharma");
        customer1.setPhone("9876543210");
        customer1.setEmail("rahul@example.com");
        customer1.setAddress("Nagpur");
    }

    // 1. ADMIN creates customer
    @Test
    void testAdminCreatesCustomer() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(customerRepository.existsByRestaurantIdAndPhone(1L, "9876543210")).thenReturn(false);
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurantA);
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        CustomerRequest request = new CustomerRequest("Rahul Sharma", "9876543210", "rahul@example.com", "Nagpur");
        CustomerResponse response = customerService.createCustomer(request);

        assertNotNull(response);
        assertEquals("Rahul Sharma", response.getName());
        assertEquals("9876543210", response.getPhone());
        assertEquals(1L, response.getRestaurantId());
    }

    // 2 & 3. Non-ADMIN (WAITER/KITCHEN) cannot create customer -> 403 Forbidden
    @Test
    void testNonAdminCannotCreateCustomer() {
        when(securityUtils.isAdmin()).thenReturn(false);

        CustomerRequest request = new CustomerRequest("Rahul", "9876543210", null, null);
        assertThrows(AccessDeniedException.class, () -> customerService.createCustomer(request));
    }

    // 5 & 6. ADMIN retrieves customer list with tenant isolation
    @Test
    void testGetCustomersTenantIsolated() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(customerRepository.findByRestaurantId(1L)).thenReturn(Arrays.asList(customer1));

        List<CustomerResponse> list = customerService.getAllCustomers();
        assertEquals(1, list.size());
        assertEquals("Rahul Sharma", list.get(0).getName());
        assertEquals(1L, list.get(0).getRestaurantId());
    }

    // 7 & 8. Retrieve customer by ID & Cross-tenant access rejected
    @Test
    void testGetCustomerByIdAndCrossTenantRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(customerRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(customer1));
        when(customerRepository.findByIdAndRestaurantId(200L, 1L)).thenReturn(Optional.empty());

        CustomerResponse res = customerService.getCustomerById(100L);
        assertEquals("Rahul Sharma", res.getName());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> customerService.getCustomerById(200L));
        assertTrue(ex.getMessage().contains("not found"));
    }

    // 9 & 10. Update customer & Cross-tenant update rejected
    @Test
    void testUpdateCustomerAndCrossTenantRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(customerRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(customer1));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        CustomerRequest req = new CustomerRequest("Rahul Updated", "9876543210", "new@example.com", "Pune");
        CustomerResponse res = customerService.updateCustomer(100L, req);

        assertEquals("Rahul Updated", res.getName());
        assertEquals("Pune", res.getAddress());

        // Cross tenant update
        when(customerRepository.findByIdAndRestaurantId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> customerService.updateCustomer(999L, req));
    }

    // 11-14. Search by name, phone, email & tenant isolation
    @Test
    void testSearchCustomersTenantIsolated() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(customerRepository.searchByRestaurantIdAndQuery(1L, "9876543210")).thenReturn(Arrays.asList(customer1));

        List<CustomerResponse> searchResult = customerService.searchCustomers("9876543210");
        assertEquals(1, searchResult.size());
        assertEquals("Rahul Sharma", searchResult.get(0).getName());
    }

    // 15 & 16. Duplicate phone within same restaurant rejected (409), but allowed in different restaurants
    @Test
    void testDuplicatePhoneHandling() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(customerRepository.existsByRestaurantIdAndPhone(1L, "9876543210")).thenReturn(true);

        CustomerRequest req = new CustomerRequest("New Customer", "9876543210", null, null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> customerService.createCustomer(req));
        assertTrue(ex.getMessage().contains("already exists"));

        // Different restaurant (Restaurant 2) -> exists check for rest 2 returns false
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L);
        when(customerRepository.existsByRestaurantIdAndPhone(2L, "9876543210")).thenReturn(false);
        when(restaurantRepository.getReferenceById(2L)).thenReturn(restaurantB);
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        CustomerResponse res2 = customerService.createCustomer(req);
        assertEquals(2L, res2.getRestaurantId());
    }

    // 17 & 18. Optional phone and email work
    @Test
    void testOptionalPhoneAndEmail() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurantA);
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        CustomerRequest req = new CustomerRequest("Anonymous", null, null, null);
        CustomerResponse res = customerService.createCustomer(req);

        assertNotNull(res);
        assertEquals("Anonymous", res.getName());
        assertNull(res.getPhone());
        assertNull(res.getEmail());
    }

    // 20. Blank name rejected -> 400 Bad Request
    @Test
    void testBlankNameRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        CustomerRequest req = new CustomerRequest("   ", "9876543210", null, null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> customerService.createCustomer(req));
        assertTrue(ex.getMessage().contains("Name is required"));
    }

    // 23. Order -> Customer relationship compatibility check
    @Test
    void testOrderCustomerRelationshipCompatibility() {
        Order order = new Order();
        order.setId(10L);
        order.setCustomer(customer1);

        assertNotNull(order.getCustomer());
        assertEquals("Rahul Sharma", order.getCustomer().getName());
        assertEquals("9876543210", order.getCustomer().getPhone());
    }
}
