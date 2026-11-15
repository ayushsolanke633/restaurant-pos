package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.MenuItemVariation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemVariationRepository extends JpaRepository<MenuItemVariation, Long> {

    List<MenuItemVariation> findByMenuItemId(Long menuItemId);

    List<MenuItemVariation> findByMenuItemIdAndRestaurantId(Long menuItemId, Long restaurantId);

    List<MenuItemVariation> findByMenuItemIdAndRestaurantIdOrderBySortOrderAscIdAsc(Long menuItemId, Long restaurantId);

    Optional<MenuItemVariation> findByIdAndRestaurantId(Long id, Long restaurantId);

    Optional<MenuItemVariation> findByIdAndMenuItemIdAndRestaurantId(Long id, Long menuItemId, Long restaurantId);

    void deleteByMenuItemId(Long menuItemId);
}
