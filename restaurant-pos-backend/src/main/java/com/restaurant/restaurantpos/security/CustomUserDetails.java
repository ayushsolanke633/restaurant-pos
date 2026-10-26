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
    private final Integer tokenVersion;
    private final Integer restaurantTokenVersion;

    public CustomUserDetails(
            Long userId,
            Long restaurantId,
            String roleName,
            String email,
            String passwordHash,
            boolean isActive,
            Collection<? extends GrantedAuthority> authorities,
            Integer tokenVersion,
            Integer restaurantTokenVersion) {
        this.userId = userId;
        this.restaurantId = restaurantId;
        this.roleName = roleName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.isActive = isActive;
        this.authorities = authorities;
        this.tokenVersion = tokenVersion != null ? tokenVersion : 1;
        this.restaurantTokenVersion = restaurantTokenVersion != null ? restaurantTokenVersion : 1;
    }

    public CustomUserDetails(
            Long userId,
            Long restaurantId,
            String roleName,
            String email,
            String passwordHash,
            boolean isActive,
            Collection<? extends GrantedAuthority> authorities,
            Integer tokenVersion) {
        this(userId, restaurantId, roleName, email, passwordHash, isActive, authorities, tokenVersion, 1);
    }

    public CustomUserDetails(
            Long userId,
            Long restaurantId,
            String roleName,
            String email,
            String passwordHash,
            boolean isActive,
            Collection<? extends GrantedAuthority> authorities) {
        this(userId, restaurantId, roleName, email, passwordHash, isActive, authorities, 1, 1);
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

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public Integer getRestaurantTokenVersion() {
        return restaurantTokenVersion;
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
