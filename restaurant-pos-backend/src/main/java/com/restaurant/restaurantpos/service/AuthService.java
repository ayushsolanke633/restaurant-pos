package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.LoginResponse;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.WaiterSessionManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final WaiterSessionManager waiterSessionManager;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService) {
        this(authenticationManager, userRepository, jwtService, new WaiterSessionManager());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            WaiterSessionManager waiterSessionManager) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.waiterSessionManager = waiterSessionManager;
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new DisabledException("User is deactivated");
        }

        if (user.getRestaurant() != null && !Boolean.TRUE.equals(user.getRestaurant().getIsActive())) {
            throw new DisabledException("Restaurant is deactivated");
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : null;

        String token = jwtService.generateToken(user);

        if ("WAITER".equalsIgnoreCase(roleName)) {
            waiterSessionManager.registerSession(user.getId(), token);
        }

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .userId(user.getId())
                .restaurantId(user.getRestaurant() != null ? user.getRestaurant().getId() : null)
                .role(roleName)
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            try {
                String email = jwtService.extractUsername(token);
                if (email != null) {
                    userRepository.findByEmail(email).ifPresent(user -> {
                        waiterSessionManager.releaseSession(user.getId(), token);
                    });
                }
            } catch (Exception ignored) {
            }
        }
    }
}
