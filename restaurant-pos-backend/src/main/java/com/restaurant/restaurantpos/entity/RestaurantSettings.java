package com.restaurant.restaurantpos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "restaurant_settings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_restaurant_settings_restaurant_id", columnNames = "restaurant_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(name = "tax_enabled", nullable = false)
    private Boolean taxEnabled = true;

    @Column(name = "default_tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultTaxRate = BigDecimal.ZERO.setScale(2);

    @Column(name = "service_charge_enabled", nullable = false)
    private Boolean serviceChargeEnabled = false;

    @Column(name = "default_service_charge_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultServiceChargeRate = BigDecimal.ZERO.setScale(2);

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "timezone", nullable = false, length = 100)
    private String timezone = "Asia/Kolkata";

    @Column(name = "receipt_header", columnDefinition = "TEXT")
    private String receiptHeader;

    @Column(name = "receipt_footer", columnDefinition = "TEXT")
    private String receiptFooter;

    @Column(name = "invoice_prefix", nullable = false, length = 20)
    private String invoicePrefix = "INV";

    @Column(name = "auto_accept_orders", nullable = false)
    private Boolean autoAcceptOrders = false;

    @Column(name = "kitchen_enabled", nullable = false)
    private Boolean kitchenEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.taxEnabled == null) this.taxEnabled = true;
        if (this.defaultTaxRate == null) this.defaultTaxRate = BigDecimal.ZERO.setScale(2);
        if (this.serviceChargeEnabled == null) this.serviceChargeEnabled = false;
        if (this.defaultServiceChargeRate == null) this.defaultServiceChargeRate = BigDecimal.ZERO.setScale(2);
        if (this.currency == null) this.currency = "INR";
        if (this.timezone == null) this.timezone = "Asia/Kolkata";
        if (this.invoicePrefix == null) this.invoicePrefix = "INV";
        if (this.autoAcceptOrders == null) this.autoAcceptOrders = false;
        if (this.kitchenEnabled == null) this.kitchenEnabled = true;
        if (!Boolean.TRUE.equals(this.kitchenEnabled)) {
            this.autoAcceptOrders = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (!Boolean.TRUE.equals(this.kitchenEnabled)) {
            this.autoAcceptOrders = false;
        }
    }
}
