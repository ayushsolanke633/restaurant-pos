package com.restaurant.restaurantpos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantStatusUpdateRequest {

    @NotNull(message = "isActive status must be provided")
    private Boolean isActive;
}
