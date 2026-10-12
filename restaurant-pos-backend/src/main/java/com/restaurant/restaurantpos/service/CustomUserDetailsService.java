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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new DisabledException("User is deactivated");
        }

        if (user.getRestaurant() != null && !Boolean.TRUE.equals(user.getRestaurant().getIsActive())) {
            throw new DisabledException("Restaurant is deactivated");
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : "USER";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + roleName);

        Long restaurantId = user.getRestaurant() != null ? user.getRestaurant().getId() : null;

        return new CustomUserDetails(
                user.getId(),
                restaurantId,
                roleName,
                user.getEmail(),
                user.getPasswordHash(),
                user.getIsActive(),
                Collections.singletonList(authority)
        );
    }
}
