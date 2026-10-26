package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String cleanEmail = email != null ? email.trim() : "";
        User user = userRepository.findByEmail(cleanEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(cleanEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new DisabledException("User is deactivated");
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : "USER";
        if (!"SUPER_ADMIN".equalsIgnoreCase(roleName)) {
            if (user.getRestaurant() == null) {
                throw new DisabledException("Tenant user must belong to a restaurant");
            }
            if (!Boolean.TRUE.equals(user.getRestaurant().getIsActive())) {
                throw new DisabledException("Restaurant is deactivated");
            }
        }

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + roleName);

        Long restaurantId = user.getRestaurant() != null ? user.getRestaurant().getId() : null;
        Integer restaurantTokenVersion = (user.getRestaurant() != null && user.getRestaurant().getTokenVersion() != null)
                ? user.getRestaurant().getTokenVersion()
                : 1;

        return new CustomUserDetails(
                user.getId(),
                restaurantId,
                roleName,
                user.getEmail(),
                user.getPasswordHash(),
                user.getIsActive(),
                Collections.singletonList(authority),
                user.getTokenVersion() != null ? user.getTokenVersion() : 1,
                restaurantTokenVersion
        );
    }
}
