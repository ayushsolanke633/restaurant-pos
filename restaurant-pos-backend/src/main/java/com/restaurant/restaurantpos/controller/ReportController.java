package com.restaurant.restaurantpos.controller;

import com.restaurant.restaurantpos.dto.report.*;
import com.restaurant.restaurantpos.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboardSummary() {
        DashboardResponse response = reportService.getDashboardSummary();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sales")
    public ResponseEntity<SalesReportResponse> getSalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SalesReportResponse response = reportService.getSalesReport(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sales/daily")
    public ResponseEntity<List<DailySalesResponse>> getDailySalesBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<DailySalesResponse> response = reportService.getDailySalesBreakdown(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payments")
    public ResponseEntity<List<PaymentMethodReportResponse>> getPaymentMethodBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<PaymentMethodReportResponse> response = reportService.getPaymentMethodBreakdown(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
    public ResponseEntity<OrderStatusReportResponse> getOrderStatusStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        OrderStatusReportResponse response = reportService.getOrderStatusStatistics(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/types")
    public ResponseEntity<List<OrderTypeReportResponse>> getOrderTypeReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<OrderTypeReportResponse> response = reportService.getOrderTypeReport(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-items")
    public ResponseEntity<List<TopSellingItemResponse>> getTopSellingItems(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<TopSellingItemResponse> response = reportService.getTopSellingItems(from, to, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategorySalesResponse>> getCategorySalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<CategorySalesResponse> response = reportService.getCategorySalesReport(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/inventory")
    public ResponseEntity<InventorySummaryResponse> getInventorySummary() {
        InventorySummaryResponse response = reportService.getInventorySummary();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tables")
    public ResponseEntity<TableSummaryResponse> getTableSummary() {
        TableSummaryResponse response = reportService.getTableSummary();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/staff")
    public ResponseEntity<StaffSummaryResponse> getStaffSummary() {
        StaffSummaryResponse response = reportService.getStaffSummary();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/customers")
    public ResponseEntity<CustomerSummaryResponse> getCustomerSummary() {
        CustomerSummaryResponse response = reportService.getCustomerSummary();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bills")
    public ResponseEntity<List<ReportBillResponse>> getReportBills(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<ReportBillResponse> response = reportService.getReportBills(from, to);
        return ResponseEntity.ok(response);
    }
}
