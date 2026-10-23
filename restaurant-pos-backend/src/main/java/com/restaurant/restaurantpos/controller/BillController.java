package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.BillRequest;
import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.PaymentRequest;
import com.restaurant.restaurantpos.dto.PaymentResponse;
import com.restaurant.restaurantpos.service.BillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bills")
@PreAuthorize("hasRole('ADMIN')")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @PostMapping
    public ResponseEntity<BillResponse> createBill(@Valid @RequestBody BillRequest request) {
        BillResponse response = billService.createBill(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<BillResponse>> getAllBills() {
        List<BillResponse> response = billService.getAllBills();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillResponse> getBillById(@PathVariable Long id) {
        BillResponse response = billService.getBillById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<BillResponse> getBillByOrderId(@PathVariable Long orderId) {
        BillResponse response = billService.getBillByOrderId(orderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{billId}/payments")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long billId,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = billService.processPayment(billId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{billId}/payments")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByBillId(@PathVariable Long billId) {
        List<PaymentResponse> response = billService.getPaymentsByBillId(billId);
        return ResponseEntity.ok(response);
    }
}
