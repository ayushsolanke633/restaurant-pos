package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderResponse;
import com.restaurant.restaurantpos.service.KitchenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kitchen")
@PreAuthorize("hasRole('KITCHEN')")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<KitchenOrderResponse>> getKitchenOrders(@RequestParam(required = false) String status) {
        List<KitchenOrderResponse> response = kitchenService.getKitchenOrders(status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<KitchenOrderResponse> getKitchenOrderById(@PathVariable Long id) {
        KitchenOrderResponse response = kitchenService.getKitchenOrderById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/accept")
    public ResponseEntity<KitchenOrderResponse> acceptOrder(@PathVariable Long id) {
        KitchenOrderResponse response = kitchenService.acceptOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/preparing")
    public ResponseEntity<KitchenOrderResponse> prepareOrder(@PathVariable Long id) {
        KitchenOrderResponse response = kitchenService.prepareOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/ready")
    public ResponseEntity<KitchenOrderResponse> readyOrder(@PathVariable Long id) {
        KitchenOrderResponse response = kitchenService.readyOrder(id);
        return ResponseEntity.ok(response);
    }
}
