package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.MenuItemRequest;
import com.restaurant.restaurantpos.dto.MenuItemResponse;
import com.restaurant.restaurantpos.service.MenuItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu-items")
public class MenuItemController {

    private final MenuItemService menuItemService;

    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> createMenuItem(@Valid @RequestBody MenuItemRequest request) {
        MenuItemResponse response = menuItemService.createMenuItem(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> getMenuItems(@RequestParam(required = false) Long categoryId) {
        List<MenuItemResponse> response = menuItemService.getMenuItems(categoryId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> getMenuItemById(@PathVariable Long id) {
        MenuItemResponse response = menuItemService.getMenuItemById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemRequest request) {
        MenuItemResponse response = menuItemService.updateMenuItem(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/available")
    public ResponseEntity<MenuItemResponse> setAvailable(@PathVariable Long id) {
        MenuItemResponse response = menuItemService.setAvailable(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/unavailable")
    public ResponseEntity<MenuItemResponse> setUnavailable(@PathVariable Long id) {
        MenuItemResponse response = menuItemService.setUnavailable(id);
        return ResponseEntity.ok(response);
    }
}
