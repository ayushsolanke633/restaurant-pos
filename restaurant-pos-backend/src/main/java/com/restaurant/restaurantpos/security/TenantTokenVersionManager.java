package com.restaurant.restaurantpos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages tenant-level token versions to ensure tokens issued before a restaurant's
 * deactivation cannot regain access upon reactivation.
 *
 * Guarantees:
 * 1. Safe, transactionally consistent version increments on deactivation.
 * 2. High performance O(1) concurrent in-memory lookups without unsafe bulk user mutations.
 * 3. Strict tenant isolation: invalidating one restaurant never impacts other tenants.
 * 4. Platform SUPER_ADMIN has no tenant association and is unaffected.
 */
@Component
public class TenantTokenVersionManager {

    private static final Logger log = LoggerFactory.getLogger(TenantTokenVersionManager.class);

    private final ConcurrentHashMap<Long, AtomicInteger> tenantVersions = new ConcurrentHashMap<>();

    public int getTenantVersion(Long restaurantId) {
        if (restaurantId == null) {
            return 1;
        }
        AtomicInteger version = tenantVersions.get(restaurantId);
        return version != null ? version.get() : 1;
    }

    /**
     * Increments the tenant token version for a deactivated restaurant.
     * Transactionally consistent: if a Spring transaction is active, the increment is
     * deferred until afterCommit. If the transaction rolls back, the version is not incremented.
     */
    public void onRestaurantDeactivated(Long restaurantId) {
        if (restaurantId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    int newVersion = doIncrement(restaurantId);
                    log.info("Tenant token version committed: restaurantId={}, newTenantVersion={}", restaurantId, newVersion);
                }
            });
        } else {
            int newVersion = doIncrement(restaurantId);
            log.info("Tenant token version updated (non-tx): restaurantId={}, newTenantVersion={}", restaurantId, newVersion);
        }
    }

    public void onRestaurantReactivated(Long restaurantId) {
        if (restaurantId == null) {
            return;
        }
        int currentVersion = getTenantVersion(restaurantId);
        log.info("Restaurant reactivated: restaurantId={}, activeTenantVersion={}", restaurantId, currentVersion);
    }

    public int incrementTenantVersion(Long restaurantId) {
        return doIncrement(restaurantId);
    }

    private int doIncrement(Long restaurantId) {
        if (restaurantId == null) {
            return 1;
        }
        return tenantVersions.computeIfAbsent(restaurantId, id -> new AtomicInteger(1))
                .incrementAndGet();
    }

    public void setTenantVersion(Long restaurantId, int version) {
        if (restaurantId != null) {
            tenantVersions.computeIfAbsent(restaurantId, id -> new AtomicInteger(version)).set(version);
        }
    }

    public void clear() {
        tenantVersions.clear();
    }
}
