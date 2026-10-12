package com.restaurant.restaurantpos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemResponse {

    private Long id;
    private Long restaurantId;
    private String name;
    private String unit;
    private BigDecimal currentQuantity;
    private BigDecimal minimumQuantity;
    private BigDecimal costPerUnit;
    private Boolean isActive;
    private Boolean lowStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
