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
            int tenantVersion = (user.getRestaurant() != null && user.getRestaurant().getTokenVersion() != null)
                    ? user.getRestaurant().getTokenVersion()
                    : (tenantTokenVersionManager != null ? tenantTokenVersionManager.getTenantVersion(restaurantId) : 1);
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
        return extractClaim(token, claims -> parseStrictPositiveInteger(claims.get("tokenVersion")));
    }

    public Integer extractTenantVersion(String token) {
        return extractClaim(token, claims -> parseStrictPositiveInteger(claims.get("tenantVersion")));
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

            // 1. Validate jti claim presence and format (strictly valid UUID string)
            String jti = claims.getId();
            if (jti == null || jti.trim().isEmpty()) {
                return false;
            }
            try {
                UUID.fromString(jti.trim());
            } catch (IllegalArgumentException ex) {
                return false;
            }

            // 2. Validate tokenVersion claim presence (strictly positive integer without lossy truncation)
            Integer tokenVersion = parseStrictPositiveInteger(claims.get("tokenVersion"));
            if (tokenVersion == null) {
                return false;
            }

            if (!(userDetails instanceof CustomUserDetails cud)) {
                return false;
            }

            // 3. Validate tokenVersion against persisted user state
            if (cud.getTokenVersion() == null || !cud.getTokenVersion().equals(tokenVersion)) {
                return false;
            }

            // 4. Validate tenantVersion for tenant users against persistent restaurant state (never default missing to 1)
            if (cud.getRestaurantId() != null) {
                Integer tokenTenantVersion = parseStrictPositiveInteger(claims.get("tenantVersion"));
                if (tokenTenantVersion == null) {
                    return false; // Fail closed on missing, malformed, decimal, or negative tenantVersion
                }
                int currentTenantVersion = cud.getRestaurantTokenVersion() != null
                        ? cud.getRestaurantTokenVersion()
                        : (tenantTokenVersionManager != null ? tenantTokenVersionManager.getTenantVersion(cud.getRestaurantId()) : 1);
                if (!tokenTenantVersion.equals(currentTenantVersion)) {
                    return false;
                }
            }

            // 5. Validate userId claim strictly (non-null, positive integral value matching user identity)
            Long tokenUserId = parseStrictPositiveLong(claims.get("userId"));
            if (tokenUserId == null || cud.getUserId() == null || !tokenUserId.equals(cud.getUserId())) {
                return false;
            }

            // 6. Validate role claim
            String tokenRole = claims.get("role", String.class);
            if (tokenRole == null || cud.getRoleName() == null || !tokenRole.equalsIgnoreCase(cud.getRoleName())) {
                return false;
            }

            // 7. Validate restaurant binding & SUPER_ADMIN policy
            Object rawRestId = claims.get("restaurantId");
            if ("SUPER_ADMIN".equalsIgnoreCase(cud.getRoleName())) {
                // SUPER_ADMIN must NOT have tenantVersion or restaurantId claim
                if (claims.get("tenantVersion") != null || rawRestId != null || cud.getRestaurantId() != null) {
                    return false;
                }
            } else {
                // Tenant user must have matching non-null restaurantId (strictly positive integral)
                Long tokenRestId = parseStrictPositiveLong(rawRestId);
                if (tokenRestId == null || cud.getRestaurantId() == null || !tokenRestId.equals(cud.getRestaurantId())) {
                    return false;
                }
            }
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException ex) {
            throw ex; // Propagate database/infrastructure failures
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> parseStrictPositiveLong(claims.get("userId")));
    }

    public Long extractRestaurantId(String token) {
        return extractClaim(token, claims -> parseStrictPositiveLong(claims.get("restaurantId")));
    }

    private Integer parseStrictPositiveInteger(Object claimObj) {
        if (claimObj == null) {
            return null;
        }
        if (claimObj instanceof Integer i) {
            return i > 0 ? i : null;
        }
        if (claimObj instanceof Long l) {
            return (l > 0 && l <= Integer.MAX_VALUE) ? l.intValue() : null;
        }
        if (claimObj instanceof Short s) {
            return s > 0 ? s.intValue() : null;
        }
        if (claimObj instanceof Byte b) {
            return b > 0 ? b.intValue() : null;
        }
        if (claimObj instanceof java.math.BigDecimal bd) {
            try {
                int i = bd.intValueExact();
                return i > 0 ? i : null;
            } catch (ArithmeticException e) {
                return null;
            }
        }
        if (claimObj instanceof java.math.BigInteger bi) {
            try {
                int i = bi.intValueExact();
                return i > 0 ? i : null;
            } catch (ArithmeticException e) {
                return null;
            }
        }
        if (claimObj instanceof Number num) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || Math.floor(d) != d) {
                return null; // Reject fractional / decimal numbers
            }
            long l = num.longValue();
            if (l < 1 || l > Integer.MAX_VALUE || (double) l != d) {
                return null; // Reject negative, zero, overflow, or precision loss
            }
            return (int) l;
        }
        return null;
    }

    private Long parseStrictPositiveLong(Object claimObj) {
        if (claimObj == null) {
            return null;
        }
        if (claimObj instanceof Long l) {
            return l > 0 ? l : null;
        }
        if (claimObj instanceof Integer i) {
            return i > 0 ? (long) i : null;
        }
        if (claimObj instanceof Short s) {
            return s > 0 ? (long) s : null;
        }
        if (claimObj instanceof Byte b) {
            return b > 0 ? (long) b : null;
        }
        if (claimObj instanceof java.math.BigDecimal bd) {
            try {
                long l = bd.longValueExact();
                return l > 0 ? l : null;
            } catch (ArithmeticException e) {
                return null;
            }
        }
        if (claimObj instanceof java.math.BigInteger bi) {
            try {
                long l = bi.longValueExact();
                return l > 0 ? l : null;
            } catch (ArithmeticException e) {
                return null;
            }
        }
        if (claimObj instanceof Number num) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || Math.floor(d) != d) {
                return null; // Reject fractional / decimal numbers
            }
            long l = num.longValue();
            if (l < 1L || (double) l != d) {
                return null; // Reject negative, zero, or precision loss
            }
            return l;
        }
        return null;
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
