package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.PlatformOnboardingRequest;
import com.restaurant.restaurantpos.dto.PlatformOnboardingResponse;
import com.restaurant.restaurantpos.dto.PlatformRestaurantResponse;
import com.restaurant.restaurantpos.dto.RestaurantStatusUpdateRequest;
import com.restaurant.restaurantpos.service.PlatformOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/restaurants")
public class PlatformRestaurantController {

    private final PlatformOnboardingService platformOnboardingService;

    public PlatformRestaurantController(PlatformOnboardingService platformOnboardingService) {
        this.platformOnboardingService = platformOnboardingService;
    }

    @PostMapping
    public ResponseEntity<PlatformOnboardingResponse> onboardRestaurant(
            @Valid @RequestBody PlatformOnboardingRequest request) {
        PlatformOnboardingResponse response = platformOnboardingService.onboardRestaurant(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<PlatformRestaurantResponse>> getAllRestaurants() {
        List<PlatformRestaurantResponse> response = platformOnboardingService.getAllRestaurants();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlatformRestaurantResponse> getRestaurantById(@PathVariable Long id) {
        PlatformRestaurantResponse response = platformOnboardingService.getRestaurantById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PlatformRestaurantResponse> updateRestaurantStatus(
            @PathVariable Long id,
            @Valid @RequestBody RestaurantStatusUpdateRequest request) {
        PlatformRestaurantResponse response = platformOnboardingService.updateRestaurantStatus(id, request.getIsActive());
        return ResponseEntity.ok(response);
    }
}
