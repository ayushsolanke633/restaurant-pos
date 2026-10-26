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
public class OrderResponse {

    private Long id;
    private Long restaurantId;
    private Long tableId;
    private Integer tableNumber;
    private Long customerId;
    private Long waiterId;
    private String waiterName;
    private String orderType;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private String gstMode;
    private BigDecimal cgstRate;
    private BigDecimal sgstRate;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;
}
