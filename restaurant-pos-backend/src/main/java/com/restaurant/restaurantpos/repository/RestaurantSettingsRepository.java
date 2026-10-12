package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.RestaurantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RestaurantSettingsRepository extends JpaRepository<RestaurantSettings, Long> {

    Optional<RestaurantSettings> findByRestaurantId(Long restaurantId);

    Optional<RestaurantSettings> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantId(Long restaurantId);
}
