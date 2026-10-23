package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.entity.RevokedToken;
import com.restaurant.restaurantpos.repository.RevokedTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction helper for isolated persistence operations on RevokedToken.
 * Uses Propagation.REQUIRES_NEW to isolate write attempts and duplicate-key verification,
 * preventing rollback poisoning of surrounding service transactions.
 */
@Component
public class RevokedTokenPersistenceHelper {

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenPersistenceHelper(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAndFlushRevocation(RevokedToken record) {
        revokedTokenRepository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean existsByJti(String jti) {
        return revokedTokenRepository.existsByJti(jti);
    }
}
