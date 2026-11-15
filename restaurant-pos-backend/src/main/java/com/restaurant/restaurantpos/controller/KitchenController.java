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
    public ResponseEntity<KitchenOrderResponse> getKitchenOrderById(
            @PathVariable Long id,
            @RequestParam(required = false) Integer batch) {
        KitchenOrderResponse response = kitchenService.getKitchenOrderById(id, batch);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/accept")
    public ResponseEntity<KitchenOrderResponse> acceptOrder(
            @PathVariable Long id,
            @RequestParam(required = false) Integer batch) {
        KitchenOrderResponse response = (batch != null) ?
                kitchenService.acceptBatch(id, batch) :
                kitchenService.acceptOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/batches/{batchNumber}/accept")
    public ResponseEntity<KitchenOrderResponse> acceptBatch(
            @PathVariable Long id,
            @PathVariable Integer batchNumber) {
        KitchenOrderResponse response = kitchenService.acceptBatch(id, batchNumber);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/preparing")
    public ResponseEntity<KitchenOrderResponse> prepareOrder(
            @PathVariable Long id,
            @RequestParam(required = false) Integer batch) {
        KitchenOrderResponse response = (batch != null) ?
                kitchenService.prepareBatch(id, batch) :
                kitchenService.prepareOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/batches/{batchNumber}/preparing")
    public ResponseEntity<KitchenOrderResponse> prepareBatch(
            @PathVariable Long id,
            @PathVariable Integer batchNumber) {
        KitchenOrderResponse response = kitchenService.prepareBatch(id, batchNumber);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/ready")
    public ResponseEntity<KitchenOrderResponse> readyOrder(
            @PathVariable Long id,
            @RequestParam(required = false) Integer batch) {
        KitchenOrderResponse response = (batch != null) ?
                kitchenService.readyBatch(id, batch) :
                kitchenService.readyOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{id}/batches/{batchNumber}/ready")
    public ResponseEntity<KitchenOrderResponse> readyBatch(
            @PathVariable Long id,
            @PathVariable Integer batchNumber) {
        KitchenOrderResponse response = kitchenService.readyBatch(id, batchNumber);
        return ResponseEntity.ok(response);
    }
}
