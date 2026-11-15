package com.restaurant.restaurantpos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItemResponse {

    private Long id;
    private Long restaurantId;
    private Long categoryId;
    private String categoryName;
    private Boolean categoryIsActive;
    private String name;
    private String description;
    private BigDecimal price;
    private Boolean isAvailable;
    private List<MenuItemVariationResponse> variations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
