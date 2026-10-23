package com.restaurant.restaurantpos.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Filter that enforces brute-force protection strictly on POST /api/auth/login.
 *
 * Security Enhancements (P0-3 Stage 2B Remediation):
 * - SEC-01: Non-consuming early IP precheck blocks known abusers without buffering bodies.
 * - SEC-01: Strict 64 KiB maximum body size bounds heap memory, returning 413 for oversized payloads.
 * - SEC-02: Trust-aware right-to-left X-Forwarded-For traversal prevents proxy header spoofing.
 * - SEC-04: Filter registration managed via SecurityConfig; CORS headers preserved on 429 and 413.
 * - SEC-05: Robust IPv4/IPv6 parsing without DNS resolution, with RFC 5952 canonical normalization.
 * - SEC-08: Canonical path matching normalizes matrix variables and trailing slashes.
 */
@Component
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitingFilter.class);

    private final LoginRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Set<String> trustedProxies;
    private final CorsConfigurationSource corsConfigurationSource;

    @Autowired
    public LoginRateLimitingFilter(
            LoginRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.login.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.trusted-proxies:}") String trustedProxiesConfig,
            @org.springframework.context.annotation.Lazy @Autowired(required = false) CorsConfigurationSource corsConfigurationSource) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.enabled = enabled;
        this.corsConfigurationSource = corsConfigurationSource;
        if (trustedProxiesConfig != null && !trustedProxiesConfig.isBlank()) {
            this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        } else {
            this.trustedProxies = Collections.emptySet();
        }
    }

    public LoginRateLimitingFilter(
            LoginRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            boolean enabled,
            Set<String> trustedProxies) {
        this(rateLimiter, objectMapper, enabled, trustedProxies, null);
    }

    public LoginRateLimitingFilter(
            LoginRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            boolean enabled,
            Set<String> trustedProxies,
            CorsConfigurationSource corsConfigurationSource) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.enabled = enabled;
        this.trustedProxies = trustedProxies != null ? trustedProxies : Collections.emptySet();
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only apply rate limiting to enabled instances and POST /api/auth/login
        if (!enabled || !isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // SEC-01 & SEC-02: Derive client IP before inspecting or buffering request body
        String clientIp = deriveClientIp(request);

        // SEC-01: Early IP-limit precheck (non-consuming) - does not read body
        LoginRateLimiter.RateLimitResult precheck = rateLimiter.checkIpLimit(clientIp);
        if (!precheck.isPermitted()) {
            log.warn("Login rate limit exceeded on precheck: reason=IP, retryAfterSeconds={}", precheck.getRetryAfterSeconds());
            sendErrorResponse(request, response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too many login attempts. Please try again later.", precheck.getRetryAfterSeconds());
            return;
        }

        // SEC-01: Bounded request-body buffering with strict 64 KiB limit
        CachedBodyHttpServletRequest wrappedRequest;
        try {
            wrappedRequest = new CachedBodyHttpServletRequest(request);
        } catch (CachedBodyHttpServletRequest.PayloadTooLargeException e) {
            log.warn("Login request payload exceeded 64 KB limit from client IP: {}", clientIp);
            sendErrorResponse(request, response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "Request payload exceeds the maximum allowed size of 64 KB.", null);
            return;
        }

        String email = extractEmail(wrappedRequest.getCachedBody());

        // Final atomic dual-key check and consume operation
        LoginRateLimiter.RateLimitResult result = rateLimiter.checkAndConsume(clientIp, email);

        if (!result.isPermitted()) {
            log.warn("Login rate limit exceeded: reason={}, retryAfterSeconds={}",
                    result.getReason(), result.getRetryAfterSeconds());
            sendErrorResponse(request, response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too many login attempts. Please try again later.", result.getRetryAfterSeconds());
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private void sendErrorResponse(HttpServletRequest request, HttpServletResponse response,
                                   int status, String message, Long retryAfter) throws IOException {
        applyCorsHeaders(request, response);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (retryAfter != null && retryAfter > 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
        }
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (corsConfigurationSource != null) {
            CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
            if (config != null) {
                String origin = request.getHeader("Origin");
                if (origin != null && !origin.isBlank()) {
                    List<String> allowed = config.getAllowedOrigins();
                    if (allowed != null && (allowed.contains(origin.trim()) || allowed.contains("*"))) {
                        response.setHeader("Access-Control-Allow-Origin", origin.trim());
                        response.setHeader("Vary", "Origin");
                    }
                }
            }
        }
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        // Strip matrix parameters (;foo=bar)
        int semicolonIndex = uri.indexOf(';');
        if (semicolonIndex != -1) {
            uri = uri.substring(0, semicolonIndex);
        }
        // Strip trailing slashes
        while (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return "/api/auth/login".equalsIgnoreCase(uri);
    }

    /**
     * Derives client IP address using right-to-left traversal of X-Forwarded-For if peer is trusted.
     */
    public String deriveClientIp(HttpServletRequest request) {
        String remoteAddrRaw = request.getRemoteAddr();
        String remoteAddr = normalizeIp(remoteAddrRaw);
        if (remoteAddr == null || remoteAddr.isBlank()) {
            remoteAddr = "unknown";
        }

        // Only trust forwarding headers if immediate remoteAddr matches configured trusted proxy
        if (!trustedProxies.isEmpty() && isTrustedProxy(remoteAddr, trustedProxies)) {
            List<String> xffList = new ArrayList<>();
            Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
            while (headers != null && headers.hasMoreElements()) {
                String headerVal = headers.nextElement();
                if (headerVal != null && !headerVal.isBlank()) {
                    String[] parts = headerVal.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            xffList.add(trimmed);
                        }
                    }
                }
            }

            if (!xffList.isEmpty()) {
                // Parse forwarding chain from RIGHT to LEFT
                for (int i = xffList.size() - 1; i >= 0; i--) {
                    String candidateRaw = xffList.get(i);
                    String candidate = normalizeIp(candidateRaw);
                    if (candidate == null) {
                        // Malformed IP encountered in chain -> fail safe back to peer remote address
                        log.warn("Malformed IP in X-Forwarded-For chain: '{}'. Falling back to peer address.", candidateRaw);
                        return remoteAddr;
                    }
                    if (!isTrustedProxy(candidate, trustedProxies)) {
                        // First untrusted IP encountered from the trusted end is the genuine client address
                        return candidate;
                    }
                }
                // If all entries in chain are trusted proxies, select the leftmost trusted proxy
                String leftmost = normalizeIp(xffList.get(0));
                return leftmost != null ? leftmost : remoteAddr;
            }
        }

        return remoteAddr;
    }

    private String extractEmail(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root != null && root.hasNonNull("email")) {
                JsonNode emailNode = root.get("email");
                if (emailNode.isTextual()) {
                    String email = emailNode.asText();
                    return (email != null && !email.isBlank()) ? email.trim() : null;
                }
            }
        } catch (Exception e) {
            log.debug("Non-JSON or malformed payload in login request");
        }
        return null;
    }

    // =========================================================================
    // Robust IP Parsing, Normalization and Subnet Matching (No DNS Resolution)
    // =========================================================================

    public static boolean isTrustedProxy(String ip, Set<String> trustedProxies) {
        if (ip == null || trustedProxies == null || trustedProxies.isEmpty()) {
            return false;
        }
        String normalizedCandidate = normalizeIp(ip);
        if (normalizedCandidate == null) {
            return false;
        }
        for (String pattern : trustedProxies) {
            if (pattern.contains("/")) {
                if (matchesCidr(normalizedCandidate, pattern)) {
                    return true;
                }
            } else {
                String normalizedPattern = normalizeIp(pattern);
                if (normalizedPattern != null && normalizedPattern.equals(normalizedCandidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean matchesCidr(String candidateIp, String cidr) {
        String[] parts = cidr.split("/", 2);
        if (parts.length != 2) {
            return false;
        }
        String baseIp = parts[0].trim();
        int prefixLen;
        try {
            prefixLen = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] candBytes = parseIpBytes(candidateIp);
        byte[] baseBytes = parseIpBytes(baseIp);
        if (candBytes == null || baseBytes == null || candBytes.length != baseBytes.length) {
            return false;
        }
        int maxBits = candBytes.length * 8;
        if (prefixLen < 0 || prefixLen > maxBits) {
            return false;
        }
        int fullBytes = prefixLen / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candBytes[i] != baseBytes[i]) {
                return false;
            }
        }
        int remainingBits = prefixLen % 8;
        if (remainingBits > 0) {
            int mask = ((0xFF << (8 - remainingBits)) & 0xFF);
            if ((candBytes[fullBytes] & mask) != (baseBytes[fullBytes] & mask)) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeIp(String rawIp) {
        byte[] bytes = parseIpBytes(rawIp);
        if (bytes == null) {
            return null;
        }
        if (bytes.length == 4) {
            try {
                return InetAddress.getByAddress(bytes).getHostAddress();
            } catch (UnknownHostException e) {
                return null;
            }
        } else if (bytes.length == 16) {
            return formatIpv6(bytes);
        }
        return null;
    }

    public static String formatIpv6(byte[] bytes) {
        int[] words = new int[8];
        for (int i = 0; i < 8; i++) {
            words[i] = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
        }

        // Find longest sequence of consecutive zeros (length >= 2) per RFC 5952
        int longestRunStart = -1;
        int longestRunLen = 1;
        int currentRunStart = -1;
        int currentRunLen = 0;

        for (int i = 0; i < 8; i++) {
            if (words[i] == 0) {
                if (currentRunStart == -1) {
                    currentRunStart = i;
                    currentRunLen = 1;
                } else {
                    currentRunLen++;
                }
                if (currentRunLen > longestRunLen) {
                    longestRunStart = currentRunStart;
                    longestRunLen = currentRunLen;
                }
            } else {
                currentRunStart = -1;
                currentRunLen = 0;
            }
        }

        StringBuilder sb = new StringBuilder(39);
        for (int i = 0; i < 8; i++) {
            if (i == longestRunStart) {
                sb.append("::");
                i += longestRunLen - 1;
                continue;
            }
            if (i > 0 && sb.length() > 0 && sb.charAt(sb.length() - 1) != ':') {
                sb.append(':');
            }
            sb.append(Integer.toHexString(words[i]));
        }
        return sb.toString();
    }

    public static byte[] parseIpBytes(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }
        String ip = rawIp.trim();
        if (ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length() - 1).trim();
        }
        int scopeIndex = ip.indexOf('%');
        if (scopeIndex != -1) {
            ip = ip.substring(0, scopeIndex).trim();
        }
        if (ip.isEmpty()) {
            return null;
        }

        if (ip.indexOf(':') == -1) {
            return parseIpv4Literal(ip);
        } else {
            byte[] v6 = parseIpv6Literal(ip);
            if (v6 != null && isIpv4Mapped(v6)) {
                byte[] v4 = new byte[4];
                System.arraycopy(v6, 12, v4, 0, 4);
                return v4;
            }
            return v6;
        }
    }

    public static byte[] parseIpv4Literal(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            if (part.length() > 1 && part.startsWith("0")) {
                return null; // Reject octal ambiguity
            }
            int val = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                val = val * 10 + (c - '0');
            }
            if (val < 0 || val > 255) {
                return null;
            }
            bytes[i] = (byte) val;
        }
        return bytes;
    }

    public static byte[] parseIpv6Literal(String ip) {
        int doubleColonIndex = ip.indexOf("::");
        if (doubleColonIndex != -1 && ip.indexOf("::", doubleColonIndex + 2) != -1) {
            return null; // Reject multiple "::"
        }

        String left = (doubleColonIndex == -1) ? ip : ip.substring(0, doubleColonIndex);
        String right = (doubleColonIndex == -1) ? null : ip.substring(doubleColonIndex + 2);

        List<Integer> words = new ArrayList<>(8);
        List<Integer> rightWords = new ArrayList<>(8);

        if (!parseIpv6Words(left, words)) {
            return null;
        }
        if (right != null && !parseIpv6Words(right, rightWords)) {
            return null;
        }

        if (doubleColonIndex == -1) {
            if (words.size() != 8) {
                return null;
            }
        } else {
            if (words.size() + rightWords.size() > 7) {
                return null;
            }
            int zerosNeeded = 8 - (words.size() + rightWords.size());
            for (int i = 0; i < zerosNeeded; i++) {
                words.add(0);
            }
            words.addAll(rightWords);
        }

        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            int w = words.get(i);
            bytes[i * 2] = (byte) ((w >> 8) & 0xff);
            bytes[i * 2 + 1] = (byte) (w & 0xff);
        }
        return bytes;
    }

    private static boolean parseIpv6Words(String s, List<Integer> words) {
        if (s.isEmpty()) {
            return true;
        }
        String[] parts = s.split(":", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                return false;
            }
            // Check for embedded IPv4 in last segment
            if (i == parts.length - 1 && part.contains(".")) {
                byte[] v4 = parseIpv4Literal(part);
                if (v4 == null) {
                    return false;
                }
                words.add(((v4[0] & 0xff) << 8) | (v4[1] & 0xff));
                words.add(((v4[2] & 0xff) << 8) | (v4[3] & 0xff));
                return true;
            }
            if (part.length() > 4) {
                return false;
            }
            int val = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                int digit = Character.digit(c, 16);
                if (digit == -1) {
                    return false;
                }
                val = (val << 4) | digit;
            }
            words.add(val);
        }
        return true;
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return (b[10] == (byte) 0xff || b[10] == 0) && (b[11] == (byte) 0xff);
    }
}
