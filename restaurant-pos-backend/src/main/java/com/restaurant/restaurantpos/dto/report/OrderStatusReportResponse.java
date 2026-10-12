package com.restaurant.restaurantpos.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusReportResponse {

    private Long newOrders;
    private Long acceptedOrders;
    private Long preparingOrders;
    private Long readyOrders;
    private Long completedOrders;
    private Long cancelledOrders;
}
