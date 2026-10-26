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
public class BillResponse {

    private Long id;
    private Long restaurantId;
    private Long orderId;
    private String billNumber;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private String gstMode;
    private BigDecimal cgstRate;
    private BigDecimal sgstRate;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private Long generatedById;
    private String generatedByName;
    private LocalDateTime createdAt;
}
