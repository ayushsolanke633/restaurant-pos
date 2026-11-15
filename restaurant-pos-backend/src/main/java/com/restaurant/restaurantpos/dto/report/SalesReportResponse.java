package com.restaurant.restaurantpos.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesReportResponse {

    private LocalDate from;
    private LocalDate to;

    /**
     * Total distinct orders created within the date range (based on order.createdAt).
     */
    private Long totalOrders;

    /**
     * Total distinct orders whose payment was successfully settled (PAID) within the date range (based on payment.paidAt).
     */
    private Long ordersSettled;

    /**
     * Total bills generated within the date range (based on bill.createdAt).
     */
    private Long billsGenerated;

    /**
     * Legacy metric: count of orders created within the date range that currently have status COMPLETED.
     * Note: Avoid using as a substitute for settled orders; does not reflect older orders settled in this period.
     */
    private Long completedOrders;

    /**
     * Count of orders created within the date range that currently have status CANCELLED.
     */
    private Long cancelledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalTax;
    private BigDecimal totalCgst;
    private BigDecimal totalSgst;
    private BigDecimal totalDiscount;
    private BigDecimal averageOrderValue;
    private BigDecimal cashAmount;
    private BigDecimal upiAmount;
    private BigDecimal cardAmount;
}
