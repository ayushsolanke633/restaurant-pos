package com.restaurant.restaurantpos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active WebSocket sessions per restaurant tenant and coordinates
 * immediate, safe invalidation when a restaurant is deactivated.
 */
@Component
public class RestaurantWebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(RestaurantWebSocketSessionManager.class);

    private final Map<String, Long> sessionToRestaurant = new ConcurrentHashMap<>();
    private final Set<Long> deactivatedRestaurants = ConcurrentHashMap.newKeySet();

    public void registerSession(String sessionId, Long restaurantId) {
        if (sessionId != null && restaurantId != null) {
            sessionToRestaurant.put(sessionId, restaurantId);
        }
    }

    public void unregisterSession(String sessionId) {
        if (sessionId != null) {
            sessionToRestaurant.remove(sessionId);
        }
    }

    public void deactivateRestaurant(Long restaurantId) {
        if (restaurantId == null) {
            return;
        }
        deactivatedRestaurants.add(restaurantId);
        // Invalidate and terminate all active sessions for this restaurant
        sessionToRestaurant.entrySet().removeIf(entry -> {
            boolean matches = entry.getValue().equals(restaurantId);
            if (matches) {
                log.info("Invalidating active WebSocket session {} for deactivated restaurant {}", entry.getKey(), restaurantId);
            }
            return matches;
        });
    }

    public void reactivateRestaurant(Long restaurantId) {
        if (restaurantId == null) {
            return;
        }
        deactivatedRestaurants.remove(restaurantId);
        log.info("Reactivated WebSocket access for restaurant {}", restaurantId);
    }

    public boolean isRestaurantDeactivated(Long restaurantId) {
        if (restaurantId == null) {
            return false;
        }
        return deactivatedRestaurants.contains(restaurantId);
    }

    public void clear() {
        sessionToRestaurant.clear();
        deactivatedRestaurants.clear();
    }
}
