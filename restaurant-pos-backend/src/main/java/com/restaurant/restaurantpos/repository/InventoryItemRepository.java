package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByRestaurantId(Long restaurantId);
    Optional<InventoryItem> findByIdAndRestaurantId(Long id, Long restaurantId);
    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);
    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(Long restaurantId, String name, Long id);

    Long countByRestaurantIdAndIsActiveTrue(Long restaurantId);

    @Query("SELECT COUNT(i) FROM InventoryItem i WHERE i.restaurant.id = :restaurantId AND i.isActive = true AND i.currentQuantity <= i.minimumQuantity")
    Long countLowStockItems(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(i) FROM InventoryItem i WHERE i.restaurant.id = :restaurantId AND i.isActive = true AND i.currentQuantity <= 0")
    Long countOutOfStockItems(@Param("restaurantId") Long restaurantId);

    @Query("SELECT SUM(i.currentQuantity * i.costPerUnit) FROM InventoryItem i WHERE i.restaurant.id = :restaurantId AND i.isActive = true")
    BigDecimal calculateTotalInventoryValue(@Param("restaurantId") Long restaurantId);
}
