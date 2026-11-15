package com.restaurant.restaurantpos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Builder
public class OrderItemRequest {

    @NotNull(message = "Menu item ID is required")
    private Long menuItemId;

    private Long variationId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private String notes;

    public OrderItemRequest(Long menuItemId, Integer quantity, String notes) {
        this.menuItemId = menuItemId;
        this.variationId = null;
        this.quantity = quantity;
        this.notes = notes;
    }

    public OrderItemRequest(Long menuItemId, Long variationId, Integer quantity, String notes) {
        this.menuItemId = menuItemId;
        this.variationId = variationId;
        this.quantity = quantity;
        this.notes = notes;
    }
}
