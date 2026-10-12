package com.restaurant.restaurantpos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantSettingsResponse {

    private Long id;
    private Long restaurantId;
    private Boolean taxEnabled;
    private BigDecimal defaultTaxRate;
    private Boolean serviceChargeEnabled;
    private BigDecimal defaultServiceChargeRate;
    private String currency;
    private String timezone;
    private String receiptHeader;
    private String receiptFooter;
    private String invoicePrefix;
    private Boolean autoAcceptOrders;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
