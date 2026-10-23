package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.LoginRequest;
import com.restaurant.restaurantpos.dto.LoginResponse;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.WaiterSessionManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final JwtService jwtService;
    private final WaiterSessionManager waiterSessionManager;
    private final TokenRevocationService tokenRevocationService;
    private final com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager webSocketSessionRevocationManager;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService) {
        this(authenticationManager, userRepository, jwtService, new WaiterSessionManager(), null, null, null);
    }

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            WaiterSessionManager waiterSessionManager) {
        this(authenticationManager, userRepository, jwtService, waiterSessionManager, null, null, null);
    }

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            WaiterSessionManager waiterSessionManager,
            RestaurantRepository restaurantRepository) {
        this(authenticationManager, userRepository, jwtService, waiterSessionManager, restaurantRepository, null, null);
    }

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            WaiterSessionManager waiterSessionManager,
            RestaurantRepository restaurantRepository,
            TokenRevocationService tokenRevocationService) {
        this(authenticationManager, userRepository, jwtService, waiterSessionManager, restaurantRepository, tokenRevocationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            WaiterSessionManager waiterSessionManager,
            RestaurantRepository restaurantRepository,
            @org.springframework.context.annotation.Lazy TokenRevocationService tokenRevocationService,
            @org.springframework.context.annotation.Lazy com.restaurant.restaurantpos.security.WebSocketSessionRevocationManager webSocketSessionRevocationManager) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.waiterSessionManager = waiterSessionManager;
        this.restaurantRepository = restaurantRepository;
        this.tokenRevocationService = tokenRevocationService;
        this.webSocketSessionRevocationManager = webSocketSessionRevocationManager;
    }

    public LoginResponse login(LoginRequest request) {
        String cleanEmail = request.getEmail() != null ? request.getEmail().trim() : "";

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cleanEmail, request.getPassword())
        );

        User user = userRepository.findByEmail(cleanEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(cleanEmail))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new DisabledException("User is deactivated");
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : null;

        if (!"SUPER_ADMIN".equalsIgnoreCase(roleName)) {
            if (user.getRestaurant() == null) {
                throw new DisabledException("Tenant user must belong to a restaurant");
            }
            if (!Boolean.TRUE.equals(user.getRestaurant().getIsActive())) {
                throw new DisabledException("Restaurant is deactivated");
            }
        }

        String token = jwtService.generateToken(user);

        if ("WAITER".equalsIgnoreCase(roleName)) {
            waiterSessionManager.registerSession(user.getId(), token);
        }

        log.info("Authentication successful: userId={}, role={}", user.getId(), roleName);

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
            com.restaurant.restaurantpos.exception.RevocationStorageException persistenceException = null;
            try {
                if (tokenRevocationService != null) {
                    tokenRevocationService.revokeToken(token);
                }
            } catch (com.restaurant.restaurantpos.exception.RevocationStorageException ex) {
                persistenceException = ex;
            }

            // In-memory waiter session release
            try {
                String email = jwtService.extractUsername(token);
                if (email != null) {
                    userRepository.findByEmail(email).ifPresent(user -> {
                        if (waiterSessionManager != null) {
                            waiterSessionManager.releaseSession(user.getId(), token);
                        }
                        log.info("User session released on logout: userId={}", user.getId());
                    });
                }
            } catch (Exception ex) {
                log.warn("Exception during waiter session release on logout: {}", ex.getMessage());
            }

            // In-memory WebSocket session invalidation for this exact jti
            try {
                String jti = jwtService.extractJti(token);
                if (jti != null && webSocketSessionRevocationManager != null) {
                    webSocketSessionRevocationManager.invalidateSessionsForJti(jti);
                }
            } catch (Exception ex) {
                log.warn("Exception invalidating WebSocket sessions on logout: {}", ex.getMessage());
            }

            // Propagate persistence failure so caller/controller never reports false success
            if (persistenceException != null) {
                throw persistenceException;
            }
        }
    }
}
