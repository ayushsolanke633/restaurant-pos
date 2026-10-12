package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurantId(Long restaurantId);

    List<MenuItem> findByRestaurantIdAndCategoryId(Long restaurantId, Long categoryId);

    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);
}
