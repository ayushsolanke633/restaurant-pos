package com.restaurant.restaurantpos.dto.kitchen;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KitchenBatchSummary {
    private Integer batchNumber;
    private String status;
    private Integer itemCount;
    private Integer totalQuantity;
    private LocalDateTime createdAt;
}
