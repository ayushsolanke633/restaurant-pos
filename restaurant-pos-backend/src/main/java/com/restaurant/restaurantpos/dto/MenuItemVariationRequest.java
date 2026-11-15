package com.restaurant.restaurantpos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItemVariationRequest {

    private Long id;

    @NotBlank(message = "Variation name is required")
    @Size(max = 100, message = "Variation name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Variation price is required")
    @DecimalMin(value = "0.00", message = "Variation price cannot be negative")
    private BigDecimal price;

    private Boolean isAvailable;

    private Integer sortOrder;
}
