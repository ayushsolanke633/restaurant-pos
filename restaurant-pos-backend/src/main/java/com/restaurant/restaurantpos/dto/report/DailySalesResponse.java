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
    private Long orders;
    private Long completedOrders;
    private Long cancelledOrders;
    private BigDecimal revenue;
}
