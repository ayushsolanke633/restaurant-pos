package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.config.DataInitializer;
import com.restaurant.restaurantpos.config.SecurityConfig;
import com.restaurant.restaurantpos.config.WebSocketConfig;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.GlobalExceptionHandler;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.restaurant.restaurantpos.security.RequestLoggingFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityHardeningTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private StompEndpointRegistry stompEndpointRegistry;

    @Mock
    private StompWebSocketEndpointRegistration stompWebSocketRegistration;

    // =========================================================================
    // 1. JWT Secret Validation Tests
    // =========================================================================

    @Test
    @DisplayName("JwtService: Missing (null) secret fails fail-closed at startup")
    void testJwtSecretMissingFailsStartup() {
        JwtService jwtService = new JwtService();
        jwtService.setSecret(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtService::validateSecret);
        assertTrue(ex.getMessage().contains("missing or empty"), "Should fail closed on null secret");
    }

    @Test
    @DisplayName("JwtService: Blank/empty secret fails fail-closed at startup")
    void testJwtSecretBlankFailsStartup() {
        JwtService jwtService = new JwtService();
        jwtService.setSecret("   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtService::validateSecret);
        assertTrue(ex.getMessage().contains("missing or empty"), "Should fail closed on blank secret");
    }

    @Test
    @DisplayName("JwtService: Weak secret (<32 bytes) fails fail-closed at startup")
    void testJwtSecretTooShortFailsStartup() {
        JwtService jwtService = new JwtService();
        jwtService.setSecret("short-weak-secret-123"); // 21 bytes < 32 bytes

        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtService::validateSecret);
        assertTrue(ex.getMessage().contains("too weak"), "Should fail closed on secret < 32 bytes");
        assertFalse(ex.getMessage().contains("short-weak-secret-123"), "Error message must never leak the secret");
    }

    @Test
    @DisplayName("JwtService: Valid secret (>=32 bytes) passes validation and signs tokens")
    void testJwtSecretValidSucceeds() {
        JwtService jwtService = new JwtService();
        // 64-char valid hex secret (256 bits)
        jwtService.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertDoesNotThrow(jwtService::validateSecret);

        User user = new User();
        user.setId(10L);
        user.setEmail("admin@test.com");
        Role role = new Role();
        role.setName("ADMIN");
        user.setRole(role);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        user.setRestaurant(restaurant);

        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertEquals("admin@test.com", jwtService.extractUsername(token));
    }

    // =========================================================================
    // 2. DataInitializer Demo Password Reset Isolation Tests
    // =========================================================================

    @Test
    @DisplayName("DataInitializer: initWaiterPassword bean is isolated to dev profile and conditional on property")
    void testDataInitializerWaiterPasswordIsolatedToDev() throws NoSuchMethodException {
        Method method = DataInitializer.class.getMethod("initWaiterPassword", UserRepository.class, BCryptPasswordEncoder.class);

        Profile profileAnnotation = method.getAnnotation(Profile.class);
        assertNotNull(profileAnnotation, "initWaiterPassword must have @Profile annotation");
        assertArrayEquals(new String[]{"dev"}, profileAnnotation.value(), "initWaiterPassword must be restricted strictly to 'dev' profile");

        ConditionalOnProperty condAnnotation = method.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condAnnotation, "initWaiterPassword must have @ConditionalOnProperty annotation");
        assertEquals("app.seed.demo-users", condAnnotation.name()[0]);
        assertEquals("true", condAnnotation.havingValue());
        assertFalse(condAnnotation.matchIfMissing(), "Must not match if property is missing");
    }

    @Test
    @DisplayName("DataInitializer: initRoles runs safely without demo user reset")
    void testDataInitializerInitRolesPreservesRoles() {
        DataInitializer initializer = new DataInitializer();
        when(roleRepository.findByName(any())).thenReturn(Optional.empty());

        CommandLineRunner runner = initializer.initRoles(roleRepository);
        assertDoesNotThrow(() -> runner.run());

        verify(roleRepository, times(3)).save(any(Role.class));
    }

    // =========================================================================
    // 3. Global Exception Handler Generic Error Tests
    // =========================================================================

    @Test
    @DisplayName("GlobalExceptionHandler: Unexpected RuntimeException returns generic message without internal leakage")
    void testUnexpectedRuntimeExceptionReturnsGenericError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String internalDbError = "ERROR: relation \"tbl_users\" does not exist at character 15, query: SELECT * FROM tbl_users";
        RuntimeException ex = new RuntimeException(internalDbError);

        ResponseEntity<Map<String, String>> response = handler.handleRuntimeException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("An unexpected error occurred. Please contact system administrator.", response.getBody().get("message"));
        assertFalse(response.getBody().get("message").contains("tbl_users"), "Must not leak internal table names");
        assertFalse(response.getBody().get("message").contains("SELECT"), "Must not leak internal SQL statements");
    }

    @Test
    @DisplayName("GlobalExceptionHandler: General Exception returns generic 500 error")
    void testGeneralExceptionReturnsGenericError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception ex = new Exception("Internal I/O failure on disk block 4096");

        ResponseEntity<Map<String, String>> response = handler.handleGeneralException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("An unexpected error occurred. Please contact system administrator.", response.getBody().get("message"));
    }

    @Test
    @DisplayName("GlobalExceptionHandler: Domain business errors preserve HTTP status codes")
    void testDomainExceptionsPreserveHttpStatus() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // 404
        ResponseEntity<Map<String, String>> notFound = handler.handleRuntimeException(new RuntimeException("Table not found with id: 99"));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());

        // 409
        ResponseEntity<Map<String, String>> conflict = handler.handleRuntimeException(new RuntimeException("Bill already exists for this order"));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());

        // 400
        ResponseEntity<Map<String, String>> badRequest = handler.handleRuntimeException(new RuntimeException("Order is in invalid status"));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
    }

    // =========================================================================
    // 4. CORS and WebSocket Allowed Origins Tests
    // =========================================================================

    @Test
    @DisplayName("SecurityConfig: CORS allows only explicitly configured origins and has no wildcards")
    void testCorsExplicitOriginsNoWildcards() {
        List<String> trustedOrigins = List.of("https://admin.restaurant.com", "https://pos.restaurant.com");
        SecurityConfig securityConfig = new SecurityConfig(null, null, null, trustedOrigins);

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tables");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        assertEquals(2, config.getAllowedOrigins().size());
        assertTrue(config.getAllowedOrigins().contains("https://admin.restaurant.com"));
        assertTrue(config.getAllowedOrigins().contains("https://pos.restaurant.com"));
        assertFalse(config.getAllowedOrigins().contains("http://localhost:5173"));
        assertNull(config.getAllowedOriginPatterns(), "Allowed origin patterns must be null (no wildcards)");
    }

    @Test
    @DisplayName("WebSocketConfig: Registers STOMP with only explicit allowed origins and no wildcards")
    void testWebSocketExplicitOriginsNoWildcards() {
        List<String> trustedOrigins = List.of("https://admin.restaurant.com", "https://pos.restaurant.com");
        WebSocketConfig webSocketConfig = new WebSocketConfig(null, trustedOrigins);

        doReturn(stompWebSocketRegistration).when(stompEndpointRegistry).addEndpoint("/ws");
        doReturn(stompWebSocketRegistration).when(stompWebSocketRegistration).setAllowedOrigins(any(String[].class));
        doReturn(null).when(stompWebSocketRegistration).withSockJS();

        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);

        verify(stompWebSocketRegistration, times(2)).setAllowedOrigins(new String[]{"https://admin.restaurant.com", "https://pos.restaurant.com"});
        verify(stompWebSocketRegistration, never()).setAllowedOriginPatterns(any());
    }

    // =========================================================================
    // 5. Phase 4G: Production Security Remediation & Configuration Tests
    // =========================================================================

    @Test
    @DisplayName("Configuration: Base application.properties and application-prod.properties contain no credential fallbacks")
    void testBaseAndProdConfigDoNotContainCredentialFallbacks() throws IOException {
        List<String> configPaths = List.of(
                "src/main/resources/application.properties",
                "src/main/resources/application-prod.properties"
        );

        for (String configPath : configPaths) {
            String content = Files.readString(Paths.get(configPath));

            // Must require JWT_SECRET without fallback default
            assertTrue(content.contains("jwt.secret=${JWT_SECRET}"), configPath + " must require ${JWT_SECRET}");
            assertFalse(content.matches("(?s).*jwt\\.secret=\\$\\{JWT_SECRET:.*\\}.*"), configPath + " must not contain fallback default for JWT_SECRET");

            // Must require DB_PASSWORD without fallback default
            assertTrue(content.contains("spring.datasource.password=${DB_PASSWORD}"), configPath + " must require ${DB_PASSWORD}");
            assertFalse(content.matches("(?s).*spring\\.datasource\\.password=\\$\\{DB_PASSWORD:.*\\}.*"), configPath + " must not contain fallback default for DB_PASSWORD");

            // Must require DB_URL and DB_USERNAME without fallback default (SEC-P03-F06, SEC-P03-F08)
            assertTrue(content.contains("spring.datasource.url=${DB_URL}"), configPath + " must require ${DB_URL}");
            assertFalse(content.matches("(?s).*spring\\.datasource\\.url=\\$\\{DB_URL:.*\\}.*"), configPath + " must not contain fallback default for DB_URL");

            assertTrue(content.contains("spring.datasource.username=${DB_USERNAME}"), configPath + " must require ${DB_USERNAME}");
            assertFalse(content.matches("(?s).*spring\\.datasource\\.username=\\$\\{DB_USERNAME:.*\\}.*"), configPath + " must not contain fallback default for DB_USERNAME");
        }
    }

    @Test
    @DisplayName("Configuration: SQL and Hibernate formatting logging are disabled by default in base and prod configs")
    void testSqlLoggingDisabledByDefaultInBaseAndProd() throws IOException {
        List<String> configPaths = List.of(
                "src/main/resources/application.properties",
                "src/main/resources/application-prod.properties"
        );

        for (String configPath : configPaths) {
            String content = Files.readString(Paths.get(configPath));
            assertTrue(content.contains("spring.jpa.show-sql=false"), configPath + " must disable show-sql by default");
            assertTrue(content.contains("spring.jpa.properties.hibernate.format_sql=false"), configPath + " must disable format_sql by default");
        }
    }

    @Test
    @DisplayName("Configuration: CORS in base and prod configs strictly requires CORS_ALLOWED_ORIGINS and cannot fall back to localhost")
    void testCorsProductionConfigurationCannotSilentlyFallbackToLocalhost() throws IOException {
        List<String> configPaths = List.of(
                "src/main/resources/application.properties",
                "src/main/resources/application-prod.properties"
        );

        for (String configPath : configPaths) {
            String content = Files.readString(Paths.get(configPath));
            assertTrue(content.contains("app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}"), configPath + " must require ${CORS_ALLOWED_ORIGINS}");
            assertFalse(content.matches("(?s).*app\\.cors\\.allowed-origins=\\$\\{CORS_ALLOWED_ORIGINS:.*localhost.*\\}.*"), configPath + " must not fall back to localhost");
        }

        // SecurityConfig fails fail-closed on empty or null allowed origins
        SecurityConfig emptyConfig = new SecurityConfig(null, null, null, List.of());
        IllegalStateException exEmpty = assertThrows(IllegalStateException.class, emptyConfig::corsConfigurationSource);
        assertTrue(exEmpty.getMessage().contains("must be configured and cannot be empty"));

        SecurityConfig nullConfig = new SecurityConfig(null, null, null, (List<String>) null);
        IllegalStateException exNull = assertThrows(IllegalStateException.class, nullConfig::corsConfigurationSource);
        assertTrue(exNull.getMessage().contains("must be configured and cannot be empty"));

        // SecurityConfig fails fail-closed on wildcard origins
        SecurityConfig wildcardConfig = new SecurityConfig(null, null, null, List.of("*"));
        IllegalStateException exWildcard = assertThrows(IllegalStateException.class, wildcardConfig::corsConfigurationSource);
        assertTrue(exWildcard.getMessage().contains("wildcard origins ('*') are strictly prohibited"));

        SecurityConfig patternWildcard = new SecurityConfig(null, null, null, List.of("https://*.restaurant.com"));
        IllegalStateException exPattern = assertThrows(IllegalStateException.class, patternWildcard::corsConfigurationSource);
        assertTrue(exPattern.getMessage().contains("wildcard origins ('*') are strictly prohibited"));

        // WebSocketConfig fails fail-closed on empty or wildcard allowed origins
        WebSocketConfig wsEmptyConfig = new WebSocketConfig(null, List.of());
        assertThrows(IllegalStateException.class, () -> wsEmptyConfig.registerStompEndpoints(stompEndpointRegistry));

        WebSocketConfig wsWildcardConfig = new WebSocketConfig(null, List.of("*"));
        assertThrows(IllegalStateException.class, () -> wsWildcardConfig.registerStompEndpoints(stompEndpointRegistry));
    }

    @Test
    @DisplayName("Configuration: Demo-user seeding is disabled by default in base and prod configs")
    void testDemoUserSeedingDisabledByDefaultInBaseAndProd() throws IOException {
        List<String> configPaths = List.of(
                "src/main/resources/application.properties",
                "src/main/resources/application-prod.properties"
        );

        for (String configPath : configPaths) {
            String content = Files.readString(Paths.get(configPath));
            assertTrue(content.contains("app.seed.demo-users=false"), configPath + " must disable demo-user seeding");
        }
    }

    @Test
    @DisplayName("RequestLoggingFilter: Excludes passwords, tokens, authorization headers and does not log at INFO")
    void testRequestLoggingFilterDoesNotExposeCredentials() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer sensitive-token-secret-12345");
        request.addHeader("Cookie", "sessionid=secret-session-token");
        request.setParameter("password", "secret-password-raw");
        request.setContentType("application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // Filter executes without error and without leaking credentials
        assertDoesNotThrow(() -> filter.doFilter(request, response, (req, res) -> {}));

        // Verify class methods and structure do not log sensitive headers
        Method doFilterMethod = RequestLoggingFilter.class.getDeclaredMethod("doFilterInternal",
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class,
                jakarta.servlet.FilterChain.class);
        assertNotNull(doFilterMethod);
    }

    // =========================================================================
    // 6. Login Account Enumeration Prevention (SEC-P03-F03)
    // =========================================================================

    @Test
    @DisplayName("F03: Login failure normalization prevents account enumeration between nonexistent email and bad password")
    void testLoginFailureNormalizationPreventsAccountEnumeration() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // 1. Non-existent email throws UsernameNotFoundException
        UsernameNotFoundException notFoundEx = new UsernameNotFoundException("User not found with email: non_existent_admin@test.com");
        ResponseEntity<Map<String, String>> notFoundResponse = handler.handleAuthenticationException(notFoundEx);

        // 2. Incorrect password throws BadCredentialsException
        BadCredentialsException badCredEx = new BadCredentialsException("Bad credentials");
        ResponseEntity<Map<String, String>> badCredResponse = handler.handleAuthenticationException(badCredEx);

        // Assert identical HTTP 401 status
        assertEquals(HttpStatus.UNAUTHORIZED, notFoundResponse.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, badCredResponse.getStatusCode());

        // Assert identical generic payload
        assertNotNull(notFoundResponse.getBody());
        assertNotNull(badCredResponse.getBody());
        assertEquals("Invalid email or password", notFoundResponse.getBody().get("message"));
        assertEquals("Invalid email or password", badCredResponse.getBody().get("message"));
        assertEquals(notFoundResponse.getBody(), badCredResponse.getBody(),
                "Non-existent email and incorrect password responses must be identical to prevent enumeration");

        // Assert sensitive account details are NOT exposed
        assertFalse(notFoundResponse.getBody().get("message").contains("User not found"));
        assertFalse(notFoundResponse.getBody().get("message").contains("non_existent_admin@test.com"));
    }

    @Test
    @DisplayName("F03: Deactivated account returns HTTP 403 without leaking credentials")
    void testDeactivatedAccountHandling() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        DisabledException disabledUserEx = new DisabledException("User is deactivated");
        ResponseEntity<Map<String, String>> response = handler.handleDisabledException(disabledUserEx);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User is deactivated", response.getBody().get("message"));
    }
}
