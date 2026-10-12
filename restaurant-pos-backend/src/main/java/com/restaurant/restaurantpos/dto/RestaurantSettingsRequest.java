package com.restaurant.restaurantpos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
public class RestaurantSettingsRequest {

    private Boolean taxEnabled;

    @DecimalMin(value = "0.00", message = "Tax rate must be at least 0")
    @DecimalMax(value = "100.00", message = "Tax rate cannot exceed 100")
    private BigDecimal defaultTaxRate;

    private Boolean serviceChargeEnabled;

    @DecimalMin(value = "0.00", message = "Service charge rate must be at least 0")
    @DecimalMax(value = "100.00", message = "Service charge rate cannot exceed 100")
    private BigDecimal defaultServiceChargeRate;

    private String currency;
    private String timezone;
    private String receiptHeader;
    private String receiptFooter;
    private String invoicePrefix;
    private Boolean autoAcceptOrders;
}
