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
public class PaymentResponse {

    private Long id;
    private Long billId;
    private Long orderId;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentStatus;
    private String transactionReference;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
