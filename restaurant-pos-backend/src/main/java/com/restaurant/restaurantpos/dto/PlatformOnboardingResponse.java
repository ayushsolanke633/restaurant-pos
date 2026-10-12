package com.restaurant.restaurantpos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Response returned upon successful restaurant onboarding.
 * Note: Never includes passwords or password hashes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformOnboardingResponse {

    private Long restaurantId;
    private String restaurantName;
    private String adminEmail;
    private String adminName;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
