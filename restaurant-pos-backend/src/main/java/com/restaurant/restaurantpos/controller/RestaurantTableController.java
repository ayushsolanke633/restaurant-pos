package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.TableRequest;
import com.restaurant.restaurantpos.dto.TableResponse;
import com.restaurant.restaurantpos.service.RestaurantTableService;
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
@RequestMapping("/api/tables")
public class RestaurantTableController {

    private final RestaurantTableService tableService;

    public RestaurantTableController(RestaurantTableService tableService) {
        this.tableService = tableService;
    }

    @PostMapping
    public ResponseEntity<TableResponse> createTable(@Valid @RequestBody TableRequest request) {
        TableResponse response = tableService.createTable(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TableResponse>> getAllTables() {
        List<TableResponse> tables = tableService.getAllTables();
        return ResponseEntity.ok(tables);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TableResponse> getTableById(@PathVariable Long id) {
        TableResponse table = tableService.getTableById(id);
        return ResponseEntity.ok(table);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TableResponse> updateTable(
            @PathVariable Long id,
            @Valid @RequestBody TableRequest request) {
        TableResponse response = tableService.updateTable(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/available")
    public ResponseEntity<TableResponse> updateStatusToAvailable(@PathVariable Long id) {
        TableResponse response = tableService.updateStatusToAvailable(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/occupied")
    public ResponseEntity<TableResponse> updateStatusToOccupied(@PathVariable Long id) {
        TableResponse response = tableService.updateStatusToOccupied(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/billing")
    public ResponseEntity<TableResponse> updateStatusToBilling(@PathVariable Long id) {
        TableResponse response = tableService.updateStatusToBilling(id);
        return ResponseEntity.ok(response);
    }
}
