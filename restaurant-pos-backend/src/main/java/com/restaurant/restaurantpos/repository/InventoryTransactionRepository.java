package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    List<InventoryTransaction> findByInventoryItemRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<InventoryTransaction> findByInventoryItemIdAndInventoryItemRestaurantIdOrderByCreatedAtDesc(Long itemId, Long restaurantId);

    Optional<InventoryTransaction> findByIdAndRestaurantId(Long id, Long restaurantId);
    List<InventoryTransaction> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<InventoryTransaction> findByInventoryItemIdAndRestaurantIdOrderByCreatedAtDesc(Long itemId, Long restaurantId);
}
