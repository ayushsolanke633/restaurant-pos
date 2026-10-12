package com.restaurant.restaurantpos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotNull(message = "Order type is required")
    private String orderType;

    private Long tableId;

    private Long customerId;

    private String notes;

    @NotEmpty(message = "Order items list cannot be empty")
    @Valid
    private List<OrderItemRequest> items;
}
