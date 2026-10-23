package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    boolean existsByJti(String jti);

    long deleteByExpiresAtBefore(Instant now);
}
