package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
    boolean existsByOrderId(Long orderId);
    boolean existsByRestaurantIdAndOrderId(Long restaurantId, Long orderId);
    Optional<Bill> findByOrderId(Long orderId);
    Optional<Bill> findByRestaurantIdAndOrderId(Long restaurantId, Long orderId);
    Optional<Bill> findByIdAndRestaurantId(Long id, Long restaurantId);
    List<Bill> findByRestaurantId(Long restaurantId);
    List<Bill> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    Optional<Bill> findTopByRestaurantIdOrderByIdDesc(Long restaurantId);
    long countByRestaurantId(Long restaurantId);
}
