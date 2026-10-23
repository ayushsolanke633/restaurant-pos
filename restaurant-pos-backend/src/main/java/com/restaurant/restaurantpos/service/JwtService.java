package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import com.restaurant.restaurantpos.security.CustomUserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs = 86400000L;

    private final com.restaurant.restaurantpos.security.TenantTokenVersionManager tenantTokenVersionManager;

    public JwtService() {
        this(null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtService(@org.springframework.context.annotation.Lazy com.restaurant.restaurantpos.security.TenantTokenVersionManager tenantTokenVersionManager) {
        this.tenantTokenVersionManager = tenantTokenVersionManager;
    }

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret configuration ('jwt.secret' / JWT_SECRET) is missing or empty. Application cannot start without a configured secret key.");
        }
        if (secret.trim().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret configuration ('jwt.secret' / JWT_SECRET) is too weak. A minimum of 32 bytes (256 bits) is required.");
        }
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String generateToken(User user) {
        String jti = UUID.randomUUID().toString();
        int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 1;
        Long restaurantId = user.getRestaurant() != null ? user.getRestaurant().getId() : null;
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("restaurantId", restaurantId);
        claims.put("role", user.getRole() != null ? user.getRole().getName() : null);
        claims.put("tokenVersion", tokenVersion);
        if (restaurantId != null) {
            int tenantVersion = tenantTokenVersionManager != null
                    ? tenantTokenVersionManager.getTenantVersion(restaurantId)
                    : 1;
            claims.put("tenantVersion", tenantVersion);
        }
        return createToken(claims, user.getEmail(), jti);
    }

    private String createToken(Map<String, Object> claims, String subject, String jti) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .id(jti)
                .subject(subject)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> {
            Object ver = claims.get("tokenVersion");
            if (ver instanceof Number num) {
                return num.intValue();
            }
            return null;
        });
    }

    public Integer extractTenantVersion(String token) {
        return extractClaim(token, claims -> {
            Object ver = claims.get("tenantVersion");
            if (ver instanceof Number num) {
                return num.intValue();
            }
            return null;
        });
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        if (username == null || !username.equalsIgnoreCase(userDetails.getUsername()) || isTokenExpired(token)) {
            return false;
        }

        try {
            Claims claims = extractAllClaims(token);

            // 1. Validate jti claim presence (fail-closed after cutover)
            String jti = claims.getId();
            if (jti == null || jti.trim().isEmpty()) {
                return false;
            }

            // 2. Validate tokenVersion claim presence (fail-closed after cutover)
            Object tokenVerObj = claims.get("tokenVersion");
            if (!(tokenVerObj instanceof Number tokenVerNum)) {
                return false;
            }
            int tokenVersion = tokenVerNum.intValue();

            if (userDetails instanceof CustomUserDetails cud) {
                // 3. Validate tokenVersion against persisted user state
                if (cud.getTokenVersion() == null || !cud.getTokenVersion().equals(tokenVersion)) {
                    return false;
                }

                // 4. Validate tenantVersion for tenant users (tokens issued before restaurant deactivation cannot be reused)
                if (cud.getRestaurantId() != null) {
                    Object tenantVerObj = claims.get("tenantVersion");
                    int tokenTenantVersion = (tenantVerObj instanceof Number num) ? num.intValue() : 1;
                    int currentTenantVersion = tenantTokenVersionManager != null
                            ? tenantTokenVersionManager.getTenantVersion(cud.getRestaurantId())
                            : 1;
                    if (tokenTenantVersion != currentTenantVersion) {
                        return false;
                    }
                }

                Object tokenUserIdObj = claims.get("userId");
                if (tokenUserIdObj instanceof Number num && cud.getUserId() != null) {
                    if (num.longValue() != cud.getUserId().longValue()) {
                        return false;
                    }
                }

                String tokenRole = claims.get("role", String.class);
                if (tokenRole != null && cud.getRoleName() != null) {
                    if (!tokenRole.equalsIgnoreCase(cud.getRoleName())) {
                        return false;
                    }
                }

                Object tokenRestIdObj = claims.get("restaurantId");
                if ("SUPER_ADMIN".equalsIgnoreCase(cud.getRoleName())) {
                    if (tokenRestIdObj != null || cud.getRestaurantId() != null) {
                        return false;
                    }
                } else {
                    if (tokenRestIdObj instanceof Number num && cud.getRestaurantId() != null) {
                        if (num.longValue() != cud.getRestaurantId().longValue()) {
                            return false;
                        }
                    } else if (cud.getRestaurantId() == null) {
                        return false;
                    }
                }
            }
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private SecretKey getSigningKey() {
        validateSecret();
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
