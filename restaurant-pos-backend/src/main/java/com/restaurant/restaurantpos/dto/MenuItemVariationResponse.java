package com.restaurant.restaurantpos.dto;

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
public class MenuItemVariationResponse {

    private Long id;
    private Long menuItemId;
    private String name;
    private BigDecimal price;
    private Boolean isAvailable;
    private Integer sortOrder;
}
