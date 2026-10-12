package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.RestaurantRequest;
import com.restaurant.restaurantpos.dto.RestaurantResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final SecurityUtils securityUtils;

    public RestaurantService(RestaurantRepository restaurantRepository, SecurityUtils securityUtils) {
        this.restaurantRepository = restaurantRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public RestaurantResponse createRestaurant(RestaurantRequest request) {
        Restaurant restaurant = mapToEntity(request);
        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        return mapToResponse(savedRestaurant);
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurantById(Long id) {
        securityUtils.validateTenantAccess(id);
        Restaurant restaurant = findRestaurantById(id);
        return mapToResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurant(Long id, RestaurantRequest request) {
        securityUtils.validateTenantAccess(id);
        Restaurant restaurant = findRestaurantById(id);

        restaurant.setName(request.getName());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone());
        restaurant.setEmail(request.getEmail());
        restaurant.setGstNumber(request.getGstNumber());

        Restaurant updatedRestaurant = restaurantRepository.save(restaurant);
        return mapToResponse(updatedRestaurant);
    }

    @Transactional
    public void deleteRestaurant(Long id) {
        securityUtils.validateTenantAccess(id);
        if (!restaurantRepository.existsById(id)) {
            throw new RuntimeException("Restaurant not found with id: " + id);
        }
        restaurantRepository.deleteById(id);
    }

    private Restaurant findRestaurantById(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + id));
    }

    private Restaurant mapToEntity(RestaurantRequest request) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.getName());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone());
        restaurant.setEmail(request.getEmail());
        restaurant.setGstNumber(request.getGstNumber());
        return restaurant;
    }

    private RestaurantResponse mapToResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .address(restaurant.getAddress())
                .phone(restaurant.getPhone())
                .email(restaurant.getEmail())
                .gstNumber(restaurant.getGstNumber())
                .isActive(restaurant.getIsActive())
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }
}
