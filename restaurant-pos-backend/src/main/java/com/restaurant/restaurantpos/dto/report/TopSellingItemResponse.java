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
public class TopSellingItemResponse {

    private Long menuItemId;
    private String menuItemName;
    private Long quantitySold;
    private BigDecimal revenue;
}
