package com.restaurant.restaurantpos.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final Long restaurantId;
    private final String roleName;
    private final String email;
    private final String passwordHash;
    private final boolean isActive;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(
            Long userId,
            Long restaurantId,
            String roleName,
            String email,
            String passwordHash,
            boolean isActive,
            Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.restaurantId = restaurantId;
        this.roleName = roleName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.isActive = isActive;
        this.authorities = authorities;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public String getRoleName() {
        return roleName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}
