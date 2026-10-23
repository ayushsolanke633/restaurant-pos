package com.restaurant.restaurantpos.config;

import com.restaurant.restaurantpos.security.JwtAuthenticationFilter;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final com.restaurant.restaurantpos.security.RequestLoggingFilter requestLoggingFilter;
    private final com.restaurant.restaurantpos.security.LoginRateLimitingFilter loginRateLimitingFilter;
    private final List<String> allowedOrigins;

    @org.springframework.beans.factory.annotation.Autowired
    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            com.restaurant.restaurantpos.security.RequestLoggingFilter requestLoggingFilter,
            com.restaurant.restaurantpos.security.LoginRateLimitingFilter loginRateLimitingFilter,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.requestLoggingFilter = requestLoggingFilter;
        this.loginRateLimitingFilter = loginRateLimitingFilter;
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins : List.of();
    }

    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            com.restaurant.restaurantpos.security.RequestLoggingFilter requestLoggingFilter,
            List<String> allowedOrigins) {
        this(userDetailsService, jwtAuthenticationFilter, requestLoggingFilter, null, allowedOrigins);
    }

    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            com.restaurant.restaurantpos.security.RequestLoggingFilter requestLoggingFilter) {
        this(userDetailsService, jwtAuthenticationFilter, requestLoggingFilter, null, List.of("http://localhost:5173", "http://localhost:5174"));
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> cleanOrigins = (allowedOrigins != null ? allowedOrigins : List.<String>of()).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (cleanOrigins.isEmpty()) {
            throw new IllegalStateException("CORS allowed origins ('app.cors.allowed-origins' / CORS_ALLOWED_ORIGINS) must be configured and cannot be empty.");
        }
        for (String origin : cleanOrigins) {
            if ("*".equals(origin) || origin.contains("*")) {
                throw new IllegalStateException("CORS wildcard origins ('*') are strictly prohibited.");
            }
        }

        configuration.setAllowedOrigins(cleanOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Unauthenticated\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Access denied\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**", "/api/health", "/api/health/**", "/ws/**").permitAll()
                .requestMatchers("/api/kitchen/**").hasAnyRole("ADMIN", "KITCHEN")
                .requestMatchers("/api/orders/waiter/history").hasAnyRole("ADMIN", "WAITER")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/complete").hasRole("ADMIN")
                .requestMatchers("/api/orders/**").hasAnyRole("ADMIN", "WAITER", "KITCHEN")
                .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").hasAnyRole("ADMIN", "WAITER")
                .requestMatchers(HttpMethod.GET, "/api/menu-items", "/api/menu-items/**").hasAnyRole("ADMIN", "WAITER")
                .requestMatchers(HttpMethod.GET, "/api/tables", "/api/tables/**").hasAnyRole("ADMIN", "WAITER")
                .requestMatchers(HttpMethod.GET, "/api/settings").hasAnyRole("ADMIN", "KITCHEN")
                .requestMatchers("/api/platform/**").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/restaurants").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/restaurants/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/restaurants/**", "/api/users/**", "/api/categories/**", "/api/menu-items/**", "/api/tables/**", "/api/bills/**", "/api/payments/**", "/api/inventory-items/**", "/api/inventory-transactions/**", "/api/customers/**", "/api/reports/**", "/api/settings/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider());

        if (loginRateLimitingFilter != null) {
            http.addFilterBefore(loginRateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        }

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(requestLoggingFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.restaurant.restaurantpos.security.LoginRateLimitingFilter> loginRateLimitingFilterRegistration(
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.restaurant.restaurantpos.security.LoginRateLimitingFilter filter) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<com.restaurant.restaurantpos.security.LoginRateLimitingFilter> registration =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>();
        if (filter != null) {
            registration.setFilter(filter);
        }
        registration.setEnabled(false);
        return registration;
    }
}
