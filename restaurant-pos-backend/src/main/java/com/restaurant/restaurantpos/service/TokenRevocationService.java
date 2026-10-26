package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.entity.RevokedToken;
import com.restaurant.restaurantpos.exception.RevocationStorageException;
import com.restaurant.restaurantpos.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtService jwtService;
    private final RevokedTokenPersistenceHelper persistenceHelper;

    public TokenRevocationService(RevokedTokenRepository revokedTokenRepository, JwtService jwtService) {
        this(revokedTokenRepository, jwtService, new RevokedTokenPersistenceHelper(revokedTokenRepository));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TokenRevocationService(
            RevokedTokenRepository revokedTokenRepository,
            JwtService jwtService,
            RevokedTokenPersistenceHelper persistenceHelper) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.jwtService = jwtService;
        this.persistenceHelper = persistenceHelper != null
                ? persistenceHelper
                : new RevokedTokenPersistenceHelper(revokedTokenRepository);
    }

    /**
     * Persistently revokes a JWT token by storing its unique jti and metadata in PostgreSQL.
     * Safe and idempotent: subsequent calls with the same token succeed.
     * Handles concurrent duplicate-key insertion races only after verifying the record exists.
     * Throws RevocationStorageException (HTTP 503) if the record cannot be verified or persisted.
     * Never logs bearer tokens, JWT contents, passwords, or secrets.
     */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        String jti;
        Date expiration;
        Long userId;
        Long restaurantId;

        try {
            jti = jwtService.extractJti(token);
            if (jti == null || jti.isBlank()) {
                log.warn("Cannot revoke token: missing or empty jti claim.");
                return;
            }

            // Fast-path idempotent check
            if (isRevoked(jti)) {
                log.debug("Token jti={} is already recorded as revoked.", jti);
                return;
            }

            expiration = jwtService.extractExpiration(token);
            userId = jwtService.extractUserId(token);
            restaurantId = jwtService.extractRestaurantId(token);

            if (userId == null) {
                log.warn("Cannot revoke token jti={}: missing userId claim.", jti);
                return;
            }
        } catch (Exception ex) {
            log.warn("Cannot revoke malformed or unparseable token: {}", ex.getMessage());
            return;
        }

        Instant expiresAt = expiration != null ? expiration.toInstant() : Instant.now();
        RevokedToken revokedToken = new RevokedToken(
                jti,
                userId,
                restaurantId,
                expiresAt,
                Instant.now()
        );

        try {
            persistenceHelper.saveAndFlushRevocation(revokedToken);
            log.info("Token successfully revoked: jti={}, userId={}, restaurantId={}", jti, userId, restaurantId);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate-key race condition:
            // Verify that the revocation record was actually persisted by the concurrent request
            boolean exists = false;
            try {
                exists = persistenceHelper.existsByJti(jti);
            } catch (Exception checkEx) {
                log.error("Failed to verify existence of revocation record for jti={}: {}", jti, checkEx.getMessage());
            }

            if (exists) {
                log.info("Duplicate revocation for jti={} resolved: record confirmed persisted by concurrent request", jti);
                return;
            }

            log.error("DataIntegrityViolationException for jti={}, but record does not exist in store!", jti, ex);
            throw new RevocationStorageException("Failed to persist token revocation record", ex);
        } catch (DataAccessException ex) {
            log.error("Database error while persisting token revocation for jti={}: {}", jti, ex.getMessage());
            throw new RevocationStorageException("Revocation store unavailable", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while persisting token revocation for jti={}: {}", jti, ex.getMessage());
            throw new RevocationStorageException("Unexpected error during revocation persistence", ex);
        }
    }

    /**
     * Checks whether a token with the specified jti has been revoked.
     * Fails closed: if the database query fails, returns true (treats token as revoked).
     */
    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return true; // Fail closed on missing/blank jti
        }
        try {
            java.util.UUID.fromString(jti.trim());
        } catch (IllegalArgumentException e) {
            return true; // Malformed / non-UUID jti is rejected fail-closed without DB round-trip
        }
        try {
            return revokedTokenRepository.existsByJti(jti);
        } catch (DataAccessException ex) {
            log.error("Database error while checking token revocation for jti={}: {}", jti, ex.getMessage());
            throw ex; // Propagate database/infrastructure failure
        }
    }

    /**
     * Purges expired revocation records whose token expiration has passed.
     */
    @Transactional
    public long purgeExpiredRevocations() {
        try {
            return revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
        } catch (Exception ex) {
            log.error("Failed to purge expired revocation records: {}", ex.getMessage());
            return 0;
        }
    }
}
