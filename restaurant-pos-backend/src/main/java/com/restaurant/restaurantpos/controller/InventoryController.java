package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.InventoryItemRequest;
import com.restaurant.restaurantpos.dto.InventoryItemResponse;
import com.restaurant.restaurantpos.dto.InventoryTransactionRequest;
import com.restaurant.restaurantpos.dto.InventoryTransactionResponse;
import com.restaurant.restaurantpos.service.InventoryService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/inventory-items")
    public ResponseEntity<InventoryItemResponse> createInventoryItem(@Valid @RequestBody InventoryItemRequest request) {
        InventoryItemResponse response = inventoryService.createInventoryItem(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/inventory-items")
    public ResponseEntity<List<InventoryItemResponse>> getAllInventoryItems() {
        List<InventoryItemResponse> response = inventoryService.getAllInventoryItems();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/inventory-items/{id}")
    public ResponseEntity<InventoryItemResponse> getInventoryItemById(@PathVariable Long id) {
        InventoryItemResponse response = inventoryService.getInventoryItemById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/inventory-items/{id}")
    public ResponseEntity<InventoryItemResponse> updateInventoryItem(
            @PathVariable Long id,
            @Valid @RequestBody InventoryItemRequest request) {
        InventoryItemResponse response = inventoryService.updateInventoryItem(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/inventory-items/{id}/activate")
    public ResponseEntity<InventoryItemResponse> activateInventoryItem(@PathVariable Long id) {
        InventoryItemResponse response = inventoryService.activateInventoryItem(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/inventory-items/{id}/deactivate")
    public ResponseEntity<InventoryItemResponse> deactivateInventoryItem(@PathVariable Long id) {
        InventoryItemResponse response = inventoryService.deactivateInventoryItem(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/inventory-items/{id}/purchase")
    public ResponseEntity<InventoryTransactionResponse> purchaseStock(
            @PathVariable Long id,
            @Valid @RequestBody InventoryTransactionRequest request) {
        InventoryTransactionResponse response = inventoryService.purchaseStock(id, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/inventory-items/{id}/adjust")
    public ResponseEntity<InventoryTransactionResponse> adjustStock(
            @PathVariable Long id,
            @Valid @RequestBody InventoryTransactionRequest request) {
        InventoryTransactionResponse response = inventoryService.adjustStock(id, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/inventory-items/{id}/wastage")
    public ResponseEntity<InventoryTransactionResponse> recordWastage(
            @PathVariable Long id,
            @Valid @RequestBody InventoryTransactionRequest request) {
        InventoryTransactionResponse response = inventoryService.recordWastage(id, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/inventory-items/{id}/transactions")
    public ResponseEntity<List<InventoryTransactionResponse>> getTransactionsByItemId(@PathVariable Long id) {
        List<InventoryTransactionResponse> response = inventoryService.getTransactionsByItemId(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/inventory-transactions")
    public ResponseEntity<List<InventoryTransactionResponse>> getAllTransactions() {
        List<InventoryTransactionResponse> response = inventoryService.getAllTransactions();
        return ResponseEntity.ok(response);
    }
}
