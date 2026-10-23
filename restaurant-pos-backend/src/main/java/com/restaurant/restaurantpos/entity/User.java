package com.restaurant.restaurantpos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "restaurant_id", nullable = true)
    private Restaurant restaurant;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.tokenVersion == null) {
            this.tokenVersion = 1;
        }
        validateRoleTenantConsistency();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        validateRoleTenantConsistency();
    }

    public void validateRoleTenantConsistency() {
        if (this.role != null && this.role.getName() != null) {
            if ("SUPER_ADMIN".equalsIgnoreCase(this.role.getName())) {
                if (this.restaurant != null) {
                    throw new IllegalStateException("Platform SUPER_ADMIN user cannot be assigned to a tenant restaurant.");
                }
            } else {
                if (this.restaurant == null) {
                    throw new IllegalStateException("Tenant user with role '" + this.role.getName() + "' must be assigned to a restaurant.");
                }
            }
        }
    }
}
