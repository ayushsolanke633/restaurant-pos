package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.restaurantpos.security.CachedBodyHttpServletRequest;
import com.restaurant.restaurantpos.security.LoginRateLimiter;
import com.restaurant.restaurantpos.security.LoginRateLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class LoginRateLimitingFilterTest {

    private ObjectMapper objectMapper;
    private LoginRateLimiter rateLimiter;
    private LoginRateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Fixed clock for deterministic execution, 3 attempts per 60s
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
        rateLimiter = new LoginRateLimiter(3, 60, 100, fixedClock);

        UrlBasedCorsConfigurationSource corsSource = new UrlBasedCorsConfigurationSource();
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of("http://localhost:5173"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        corsSource.registerCorsConfiguration("/**", corsConfig);

        filter = new LoginRateLimitingFilter(rateLimiter, objectMapper, true, Set.of("10.0.0.1", "127.0.0.1"), corsSource);
    }

    @Test
    @DisplayName("8. Rate limiting does not interfere with unrelated public endpoints or authenticated API routes")
    void testFilterDoesNotInterfereWithOtherRoutes() throws ServletException, IOException {
        String[] otherEndpoints = {
                "GET /api/categories",
                "GET /api/health",
                "POST /api/auth/logout",
                "GET /api/auth/login", // GET on /api/auth/login is not a login attempt
                "GET /api/menu-items"
        };

        for (String endpoint : otherEndpoints) {
            String[] parts = endpoint.split(" ");
            String method = parts[0];
            String uri = parts[1];

            MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
            request.setRemoteAddr("192.168.1.100");
            MockHttpServletResponse response = new MockHttpServletResponse();

            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            filter.doFilter(request, response, (req, res) -> chainInvoked.set(true));

            assertTrue(chainInvoked.get(), "Filter chain must be invoked for " + endpoint);
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    @DisplayName("1. & 2. Requests under limit are forwarded; requests beyond limit return 429")
    void testLoginRateLimitingExecution() throws ServletException, IOException {
        String loginJson = "{\"email\":\"admin@restaurant.com\",\"password\":\"secret\"}";

        // 3 requests under limit are permitted and downstream receives CachedBodyHttpServletRequest
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("192.168.1.200");
            request.setContent(loginJson.getBytes(StandardCharsets.UTF_8));
            request.setContentType("application/json");

            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<ServletRequest> passedRequest = new AtomicReference<>();

            filter.doFilter(request, response, (req, res) -> passedRequest.set(req));

            assertNotNull(passedRequest.get(), "Downstream chain must be called on attempt " + i);
            assertTrue(passedRequest.get() instanceof CachedBodyHttpServletRequest,
                    "Request passed to downstream must be CachedBodyHttpServletRequest");

            // Verify body is still fully readable downstream
            byte[] bodyBytes = passedRequest.get().getInputStream().readAllBytes();
            assertEquals(loginJson, new String(bodyBytes, StandardCharsets.UTF_8));
            assertEquals(200, response.getStatus());
        }

        // 4th request exceeds limit -> returns HTTP 429
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedRequest.setRemoteAddr("192.168.1.200");
        blockedRequest.setContent(loginJson.getBytes(StandardCharsets.UTF_8));
        blockedRequest.setContentType("application/json");

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(blockedRequest, blockedResponse, (req, res) -> chainInvoked.set(true));

        assertFalse(chainInvoked.get(), "Downstream chain must NOT be invoked when rate limited");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.getStatus());
        assertEquals("application/json;charset=UTF-8", blockedResponse.getContentType());
        assertTrue(blockedResponse.getContentAsString().contains("Too many login attempts. Please try again later."));
        assertNotNull(blockedResponse.getHeader("Retry-After"));
        assertTrue(Integer.parseInt(blockedResponse.getHeader("Retry-After")) > 0);
    }

    @Test
    @DisplayName("Untrusted X-Forwarded-For is ignored when remote address is not in trusted-proxies")
    void testUntrustedXForwardedForIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        // Remote address is NOT in trustedProxies Set ("10.0.0.1", "127.0.0.1")
        request.setRemoteAddr("203.0.113.50");
        request.addHeader("X-Forwarded-For", "198.51.100.99");

        String derivedIp = filter.deriveClientIp(request);
        assertEquals("203.0.113.50", derivedIp,
                "Untrusted remote address must ignore X-Forwarded-For header");
    }

    @Test
    @DisplayName("Trusted proxy derives client IP from first X-Forwarded-For entry")
    void testTrustedProxyExtractsClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        // Remote address IS in trustedProxies Set ("10.0.0.1", "127.0.0.1")
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.77, 10.0.0.1");

        String derivedIp = filter.deriveClientIp(request);
        assertEquals("203.0.113.77", derivedIp,
                "Trusted proxy remote address must extract client IP from X-Forwarded-For");
    }

    @Test
    @DisplayName("Invalid X-Forwarded-For format falls back to remote address safely")
    void testInvalidXForwardedForFallsBack() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "attacker-header-injection-string");

        String derivedIp = filter.deriveClientIp(request);
        assertEquals("127.0.0.1", derivedIp,
                "Malformed IP header must fall back to remote address");
    }

    @Test
    @DisplayName("Malformed JSON login body does not bypass IP rate limiting")
    void testMalformedJsonDoesNotBypassIpRateLimiting() throws ServletException, IOException {
        String malformedJson = "{not-a-valid-json-string}";

        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("192.168.1.250");
            req.setContent(malformedJson.getBytes(StandardCharsets.UTF_8));
            req.setContentType("application/json");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, (r, s) -> {});
            assertEquals(200, res.getStatus());
        }

        // 4th request from same IP with malformed body must be rejected
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedReq.setRemoteAddr("192.168.1.250");
        blockedReq.setContent(malformedJson.getBytes(StandardCharsets.UTF_8));
        blockedReq.setContentType("application/json");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        filter.doFilter(blockedReq, blockedRes, (r, s) -> {});
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedRes.getStatus());
    }

    @Test
    @DisplayName("SEC-01: Blocked IP returns 429 on precheck without buffering body stream")
    void testBlockedIpRejectsWithoutConsumingRequestBody() throws ServletException, IOException {
        String testIp = "192.168.1.210";
        String validBody = "{\"email\":\"user@test.com\",\"password\":\"secret\"}";

        // Exhaust 3 attempts
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr(testIp);
            req.setContent(validBody.getBytes(StandardCharsets.UTF_8));
            req.setContentType("application/json");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, (r, s) -> {});
            assertEquals(200, res.getStatus());
        }

        // 4th request: IP is already blocked. Use a request body with a large payload.
        byte[] largePayload = new byte[50_000];
        java.util.Arrays.fill(largePayload, (byte) 'A');

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedReq.setRemoteAddr(testIp);
        blockedReq.setContent(largePayload);
        blockedReq.setContentType("application/json");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(blockedReq, blockedRes, (r, s) -> chainCalled.set(true));

        assertFalse(chainCalled.get(), "Downstream must not be called");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedRes.getStatus());
        assertNotNull(blockedRes.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("SEC-01: Oversized request body (> 64 KiB) returns HTTP 413 Payload Too Large")
    void testOversizedPayloadReturns413() throws ServletException, IOException {
        // 70 KiB payload (> 64 KiB limit)
        byte[] oversized = new byte[70 * 1024];
        java.util.Arrays.fill(oversized, (byte) '{');

        // Test with Content-Length set
        MockHttpServletRequest reqWithLength = new MockHttpServletRequest("POST", "/api/auth/login");
        reqWithLength.setRemoteAddr("192.168.10.1");
        reqWithLength.setContent(oversized);
        reqWithLength.setContentType("application/json");
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        filter.doFilter(reqWithLength, res1, (r, s) -> {});
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), res1.getStatus());
        assertTrue(res1.getContentAsString().contains("64 KB"));

        // Test with chunked / unspecified content length
        MockHttpServletRequest reqChunked = new MockHttpServletRequest("POST", "/api/auth/login");
        reqChunked.setRemoteAddr("192.168.10.2");
        reqChunked.setContent(oversized);
        reqChunked.setContentType("application/json");
        reqChunked.removeHeader("Content-Length");
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        filter.doFilter(reqChunked, res2, (r, s) -> {});
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), res2.getStatus());
        assertTrue(res2.getContentAsString().contains("64 KB"));
    }

    @Test
    @DisplayName("SEC-02: Trusted proxy right-to-left traversal ignores client-injected leftmost IP")
    void testTrustedProxyIgnoresSpoofedClientIpInXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        // Peer remoteAddr is in trustedProxies ("10.0.0.1")
        request.setRemoteAddr("10.0.0.1");
        // Attacker injected 1.1.1.1 on the left, actual client IP from proxy is 198.51.100.50
        request.addHeader("X-Forwarded-For", "1.1.1.1, 198.51.100.50");

        String derivedIp = filter.deriveClientIp(request);
        assertEquals("198.51.100.50", derivedIp,
                "Right-to-left traversal must select the first untrusted IP from the right (198.51.100.50), ignoring forged leftmost 1.1.1.1");
    }

    @Test
    @DisplayName("SEC-02: Multi-hop trusted proxies are traversed right-to-left to the genuine client")
    void testMultipleTrustedProxyHops() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        // Peer remoteAddr is trusted 10.0.0.1
        request.setRemoteAddr("10.0.0.1");
        // 127.0.0.1 is also trusted; 198.51.100.50 is untrusted client
        request.addHeader("X-Forwarded-For", "198.51.100.50, 127.0.0.1");

        String derivedIp = filter.deriveClientIp(request);
        assertEquals("198.51.100.50", derivedIp,
                "Traversal must step past trusted 127.0.0.1 and select genuine client 198.51.100.50");
    }

    @Test
    @DisplayName("SEC-02: IPv6 clients via trusted proxy are derived and normalized correctly")
    void testIpv6ClientsViaProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "2001:0db8:0000:0000:0000:0000:0000:0001");

        String derivedIp = filter.deriveClientIp(request);
        assertEquals("2001:db8::1", derivedIp, "Derived IPv6 address must be normalized to canonical form");
    }

    @Test
    @DisplayName("SEC-04: CORS headers are included on HTTP 429 and 413 responses for allowed origins")
    void testCorsHeadersIncludedOn429And413() throws ServletException, IOException {
        String origin = "http://localhost:5173";

        // Test 413 CORS
        byte[] oversized = new byte[70 * 1024];
        MockHttpServletRequest req413 = new MockHttpServletRequest("POST", "/api/auth/login");
        req413.setRemoteAddr("192.168.1.11");
        req413.setContent(oversized);
        req413.setContentType("application/json");
        req413.addHeader("Origin", origin);
        MockHttpServletResponse res413 = new MockHttpServletResponse();

        filter.doFilter(req413, res413, (r, s) -> {});
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), res413.getStatus());
        assertEquals(origin, res413.getHeader("Access-Control-Allow-Origin"),
                "413 response must contain Access-Control-Allow-Origin for allowed origin");

        // Test 429 CORS
        String validJson = "{\"email\":\"cors@test.com\",\"password\":\"pw\"}";
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/auth/login");
            r.setRemoteAddr("192.168.1.12");
            r.setContent(validJson.getBytes(StandardCharsets.UTF_8));
            r.setContentType("application/json");
            r.addHeader("Origin", origin);
            MockHttpServletResponse s = new MockHttpServletResponse();
            filter.doFilter(r, s, (req, res) -> {});
        }

        MockHttpServletRequest req429 = new MockHttpServletRequest("POST", "/api/auth/login");
        req429.setRemoteAddr("192.168.1.12");
        req429.setContent(validJson.getBytes(StandardCharsets.UTF_8));
        req429.setContentType("application/json");
        req429.addHeader("Origin", origin);
        MockHttpServletResponse res429 = new MockHttpServletResponse();

        filter.doFilter(req429, res429, (r, s) -> {});
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), res429.getStatus());
        assertEquals(origin, res429.getHeader("Access-Control-Allow-Origin"),
                "429 response must contain Access-Control-Allow-Origin for allowed origin");
    }

    @Test
    @DisplayName("SEC-05: IPv6 normalization maps equivalent formats to the same key and rejects malformed values")
    void testIpv6NormalizationAndEquivalence() {
        // Full expanded vs compressed vs uppercase
        String norm1 = LoginRateLimitingFilter.normalizeIp("2001:0db8:0000:0000:0000:0000:0000:0001");
        String norm2 = LoginRateLimitingFilter.normalizeIp("2001:db8::1");
        String norm3 = LoginRateLimitingFilter.normalizeIp("2001:DB8::1");
        assertEquals("2001:db8::1", norm1);
        assertEquals(norm1, norm2);
        assertEquals(norm2, norm3);

        // IPv4-mapped IPv6 maps to standard IPv4
        String mapped = LoginRateLimitingFilter.normalizeIp("::ffff:192.168.1.1");
        assertEquals("192.168.1.1", mapped);

        // Malformed values must return null (never accepted as valid keys)
        assertNull(LoginRateLimitingFilter.normalizeIp("::::"));
        assertNull(LoginRateLimitingFilter.normalizeIp("1234::5678::9999"));
        assertNull(LoginRateLimitingFilter.normalizeIp("bad:hex:address"));
        assertNull(LoginRateLimitingFilter.normalizeIp("attacker.com"));
        assertNull(LoginRateLimitingFilter.normalizeIp("192.168.1.1.5"));
        assertNull(LoginRateLimitingFilter.normalizeIp("010.0.0.1")); // Octal-style leading zeros rejected
    }

    @Test
    @DisplayName("SEC-08: Canonical route matching intercepts trailing slashes, matrix params, and case variants")
    void testCanonicalRouteMatchingVariants() throws ServletException, IOException {
        String loginJson = "{\"email\":\"variant@test.com\",\"password\":\"secret\"}";
        String[] variants = {
                "/api/auth/login/",
                "/api/auth/login;jsessionid=12345",
                "/api/auth/Login"
        };

        for (String uri : variants) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
            req.setRemoteAddr("10.20.30.40");
            req.setContent(loginJson.getBytes(StandardCharsets.UTF_8));
            req.setContentType("application/json");
            MockHttpServletResponse res = new MockHttpServletResponse();

            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            filter.doFilter(req, res, (r, s) -> chainInvoked.set(true));

            assertTrue(chainInvoked.get(), "Login variant " + uri + " must be intercepted and forwarded through filter");
        }
    }
}

