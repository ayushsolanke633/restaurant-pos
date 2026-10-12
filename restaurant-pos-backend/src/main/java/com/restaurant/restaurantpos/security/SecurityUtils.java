package com.restaurant.restaurantpos.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public CustomUserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new AccessDeniedException("Access denied");
    }

    public Long getCurrentUserId() {
        return getCurrentUserDetails().getUserId();
    }

    public Long getCurrentRestaurantId() {
        return getCurrentUserDetails().getRestaurantId();
    }

    public String getCurrentUserRole() {
        return getCurrentUserDetails().getRoleName();
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(getCurrentUserRole());
    }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equalsIgnoreCase(getCurrentUserRole());
    }

    public void validateTenantAccess(Long targetRestaurantId) {
        Long currentRestaurantId = getCurrentRestaurantId();
        if (currentRestaurantId == null || targetRestaurantId == null || !targetRestaurantId.equals(currentRestaurantId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
