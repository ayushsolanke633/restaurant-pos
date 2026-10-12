package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.RestaurantSettingsRequest;
import com.restaurant.restaurantpos.dto.RestaurantSettingsResponse;
import com.restaurant.restaurantpos.service.RestaurantSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class RestaurantSettingsController {

    private final RestaurantSettingsService settingsService;

    public RestaurantSettingsController(RestaurantSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN')")
    public ResponseEntity<RestaurantSettingsResponse> getSettings() {
        RestaurantSettingsResponse response = settingsService.getSettings();
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantSettingsResponse> updateSettings(@Valid @RequestBody RestaurantSettingsRequest request) {
        RestaurantSettingsResponse response = settingsService.updateSettings(request);
        return ResponseEntity.ok(response);
    }
}

