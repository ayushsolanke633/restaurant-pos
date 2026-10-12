package com.restaurant.restaurantpos.dto.report;

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
public class ReportBillItemResponse {

    private Long id;
    private Long menuItemId;
    private String menuItemName;
    private String name;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String notes;
    private BigDecimal subtotal;
    private BigDecimal amount;
}
