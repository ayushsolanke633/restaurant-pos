package com.restaurant.restaurantpos.dto.report;

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
public class ReportBillResponse {

    private Long billId;
    private Long id;
    private Long orderId;
    private String billNumber;
    private String orderType;
    private Integer tableNumber;
    private String tableName;
    private String waiterName;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private String paymentMethod;
    private String paymentStatus;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal discount;
    private BigDecimal taxAmount;
    private BigDecimal tax;
    private String gstMode;
    private BigDecimal cgstRate;
    private BigDecimal sgstRate;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal totalAmount;
    private BigDecimal total;
    private List<ReportBillItemResponse> items;
}
