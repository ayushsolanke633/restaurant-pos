package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.service.OrderService;
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
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        List<OrderResponse> response = orderService.getAllOrders(status, orderType);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/waiter/history")
    public ResponseEntity<List<OrderResponse>> getWaiterOrderHistory(
            @RequestParam(required = false) String status) {
        List<OrderResponse> response = orderService.getWaiterOrderHistory(status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/table/{tableId}/active")
    public ResponseEntity<OrderResponse> getActiveOrderByTable(@PathVariable Long tableId) {
        OrderResponse response = orderService.getActiveOrderByTable(tableId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.updateOrder(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<OrderResponse> addItemsToOrder(
            @PathVariable Long id,
            @Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.addItemsToOrder(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/accept")
    public ResponseEntity<OrderResponse> acceptOrder(@PathVariable Long id) {
        OrderResponse response = orderService.acceptOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/preparing")
    public ResponseEntity<OrderResponse> prepareOrder(@PathVariable Long id) {
        OrderResponse response = orderService.prepareOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/ready")
    public ResponseEntity<OrderResponse> readyOrder(@PathVariable Long id) {
        OrderResponse response = orderService.readyOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<OrderResponse> completeOrder(@PathVariable Long id) {
        OrderResponse response = orderService.completeOrder(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        OrderResponse response = orderService.cancelOrder(id);
        return ResponseEntity.ok(response);
    }
}
