package com.restaurant.restaurantpos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransactionRequest {

    @NotNull(message = "Quantity is required")
    private BigDecimal quantity;

    private String referenceType;
    private Long referenceId;
}
