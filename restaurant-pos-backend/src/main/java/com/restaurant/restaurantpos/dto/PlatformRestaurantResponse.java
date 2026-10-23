package com.restaurant.restaurantpos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Platform-level restaurant summary response.
 * Note: Strictly excludes all credentials, passwords, and password hashes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformRestaurantResponse {

    private Long id;
    private String name;
    private String address;
    private String phone;
    private String email;
    private String gstNumber;
    @com.fasterxml.jackson.annotation.JsonProperty("isActive")
    @com.fasterxml.jackson.annotation.JsonAlias({"active", "status"})
    private Boolean isActive;
    private String adminEmail;
    private Long totalUsers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
