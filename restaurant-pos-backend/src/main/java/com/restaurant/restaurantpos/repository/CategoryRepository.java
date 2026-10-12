package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByRestaurantId(Long restaurantId);

    Optional<Category> findByIdAndRestaurantId(Long id, Long restaurantId);
}
