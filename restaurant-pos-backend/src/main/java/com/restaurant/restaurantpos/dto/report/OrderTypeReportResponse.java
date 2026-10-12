package com.restaurant.restaurantpos.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTypeReportResponse {

    private String orderType;
    private Long orderCount;
    private Long completedCount;
    private Long cancelledCount;
    private BigDecimal revenue;
}
