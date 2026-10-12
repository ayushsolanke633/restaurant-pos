package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

public class CorsSecurityConfigTest {

    @Test
    public void testCorsConfigurationSource() {
        SecurityConfig securityConfig = new SecurityConfig(null, null, null);
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source, "CorsConfigurationSource bean should not be null");

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader("Origin", "http://localhost:5174");
        request.addHeader("Access-Control-Request-Method", "POST");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config, "CorsConfiguration should be matched for /api/auth/login");

        assertTrue(config.getAllowedOrigins().contains("http://localhost:5173"), "Origin http://localhost:5173 should be allowed");
        assertTrue(config.getAllowedOrigins().contains("http://localhost:5174"), "Origin http://localhost:5174 should be allowed");
        assertTrue(config.getAllowedMethods().containsAll(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")), "Allowed methods should cover GET, POST, PUT, PATCH, DELETE, OPTIONS");
        assertTrue(config.getAllowedHeaders().containsAll(java.util.List.of("Authorization", "Content-Type")), "Allowed headers should include Authorization and Content-Type");
        assertFalse(Boolean.TRUE.equals(config.getAllowCredentials()), "Credentials should not be enabled unnecessarily");
    }
}
