package com.restaurant.restaurantpos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "bills", uniqueConstraints = {
    @UniqueConstraint(name = "uk_bills_restaurant_bill_number", columnNames = {"restaurant_id", "bill_number"}),
    @UniqueConstraint(name = "uk_bills_order_id", columnNames = {"order_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "gst_mode", length = 20)
    private String gstMode;

    @Column(name = "cgst_rate", precision = 5, scale = 2)
    private BigDecimal cgstRate;

    @Column(name = "sgst_rate", precision = 5, scale = 2)
    private BigDecimal sgstRate;

    @Column(name = "cgst_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "sgst_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne
    @JoinColumn(name = "generated_by", nullable = false)
    private User generatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.cgstAmount == null) {
            this.cgstAmount = BigDecimal.ZERO.setScale(2);
        }
        if (this.sgstAmount == null) {
            this.sgstAmount = BigDecimal.ZERO.setScale(2);
        }
    }
}
