package com.restaurant.restaurantpos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class InventoryItemRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotNull(message = "Minimum quantity is required")
    @DecimalMin(value = "0.0", message = "Minimum quantity cannot be negative")
    private BigDecimal minimumQuantity;

    @NotNull(message = "Cost per unit is required")
    @DecimalMin(value = "0.0", message = "Cost per unit cannot be negative")
    private BigDecimal costPerUnit;

    private Boolean isActive = true;
}
