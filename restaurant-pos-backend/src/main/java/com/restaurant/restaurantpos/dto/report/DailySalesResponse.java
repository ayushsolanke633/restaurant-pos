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
public class DailySalesResponse {

    private LocalDate date;

    /**
     * Total distinct orders created on this date (based on order.createdAt).
     */
    private Long orders;

    /**
     * Total distinct orders whose payment was settled on this date (based on payment.paidAt).
     */
    private Long ordersSettled;

    private Long completedOrders;
    private Long cancelledOrders;
    private BigDecimal revenue;
}
