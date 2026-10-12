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
public class InventorySummaryResponse {

    private Long totalActiveItems;
    private Long lowStockItemCount;
    private Long outOfStockItemCount;
    private BigDecimal totalInventoryValue;
}
