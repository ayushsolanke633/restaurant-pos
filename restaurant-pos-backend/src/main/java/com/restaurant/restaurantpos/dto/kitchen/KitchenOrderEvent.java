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
public class KitchenOrderEvent {

    private String eventType; // ORDER_CREATED, ORDER_UPDATED, ORDER_STATUS_CHANGED
    private Long orderId;
    private Long restaurantId;
    private String orderType;
    private Long tableId;
    private Integer tableNumber;
    private String status;
    private Integer batchNumber;
    private Boolean isUpdate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
