package com.restaurant.restaurantpos.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isActive")
    @JsonAlias({"active", "status"})
    @NotNull(message = "isActive status must be provided")
    private Boolean isActive;
}

