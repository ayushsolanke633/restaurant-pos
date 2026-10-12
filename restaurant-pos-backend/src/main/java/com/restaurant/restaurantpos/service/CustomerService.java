package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.CustomerRequest;
import com.restaurant.restaurantpos.dto.CustomerResponse;
import com.restaurant.restaurantpos.entity.Customer;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final SecurityUtils securityUtils;

    public CustomerService(
            CustomerRepository customerRepository,
            RestaurantRepository restaurantRepository,
            SecurityUtils securityUtils) {
        this.customerRepository = customerRepository;
        this.restaurantRepository = restaurantRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }

        Long restaurantId = securityUtils.getCurrentRestaurantId();
        String name = request.getName() != null ? request.getName().trim() : "";
        String phone = request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null;
        String email = request.getEmail() != null && !request.getEmail().isBlank() ? request.getEmail().trim() : null;
        String address = request.getAddress() != null && !request.getAddress().isBlank() ? request.getAddress().trim() : null;

        if (name.isBlank()) {
            throw new RuntimeException("Name is required");
        }

        if (phone != null && customerRepository.existsByRestaurantIdAndPhone(restaurantId, phone)) {
            throw new RuntimeException("Customer with phone number '" + phone + "' already exists");
        }

        Restaurant restaurant = restaurantRepository.getReferenceById(restaurantId);

        Customer customer = new Customer();
        customer.setRestaurant(restaurant);
        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setAddress(address);

        Customer savedCustomer = customerRepository.save(customer);
        return mapToResponse(savedCustomer);
    }

    public List<CustomerResponse> getAllCustomers() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        List<Customer> customers = customerRepository.findByRestaurantId(restaurantId);
        return customers.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public CustomerResponse getCustomerById(Long id) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Customer customer = customerRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        return mapToResponse(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }

        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Customer customer = customerRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        String name = request.getName() != null ? request.getName().trim() : "";
        String phone = request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null;
        String email = request.getEmail() != null && !request.getEmail().isBlank() ? request.getEmail().trim() : null;
        String address = request.getAddress() != null && !request.getAddress().isBlank() ? request.getAddress().trim() : null;

        if (name.isBlank()) {
            throw new RuntimeException("Name is required");
        }

        if (phone != null && customerRepository.existsByRestaurantIdAndPhoneAndIdNot(restaurantId, phone, id)) {
            throw new RuntimeException("Customer with phone number '" + phone + "' already exists");
        }

        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setAddress(address);

        Customer saved = customerRepository.save(customer);
        return mapToResponse(saved);
    }

    public List<CustomerResponse> searchCustomers(String query) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();

        if (query == null || query.isBlank()) {
            return getAllCustomers();
        }

        List<Customer> customers = customerRepository.searchByRestaurantIdAndQuery(restaurantId, query.trim());
        return customers.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .restaurantId(customer.getRestaurant() != null ? customer.getRestaurant().getId() : null)
                .name(customer.getName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .address(customer.getAddress())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
