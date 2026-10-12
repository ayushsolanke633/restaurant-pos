package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<OrderStatusHistory> findByOrderIdAndRestaurantIdOrderByCreatedAtAsc(Long orderId, Long restaurantId);

    Optional<OrderStatusHistory> findByIdAndRestaurantId(Long id, Long restaurantId);
}
