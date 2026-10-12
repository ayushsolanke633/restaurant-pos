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
    private Long totalOrders;
    private Long completedOrders;
    private Long cancelledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalTax;
    private BigDecimal totalDiscount;
    private BigDecimal averageOrderValue;
    private BigDecimal cashAmount;
    private BigDecimal upiAmount;
    private BigDecimal cardAmount;
}
