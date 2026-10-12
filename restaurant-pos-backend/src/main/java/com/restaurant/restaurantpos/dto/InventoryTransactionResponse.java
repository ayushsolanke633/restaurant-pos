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
public class InventoryTransactionResponse {

    private Long id;
    private Long inventoryItemId;
    private String inventoryItemName;
    private String type;
    private BigDecimal quantity;
    private String referenceType;
    private Long referenceId;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
}
