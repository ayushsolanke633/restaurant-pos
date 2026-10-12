package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByRestaurantId(Long restaurantId);

    List<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    List<Order> findByRestaurantIdAndStatus(Long restaurantId, String status);

    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, String status);

    List<Order> findByRestaurantIdAndOrderType(Long restaurantId, String orderType);

    List<Order> findByRestaurantIdAndOrderTypeOrderByCreatedAtDesc(Long restaurantId, String orderType);

    List<Order> findByRestaurantIdAndStatusAndOrderType(Long restaurantId, String status, String orderType);

    List<Order> findByRestaurantIdAndStatusAndOrderTypeOrderByCreatedAtDesc(Long restaurantId, String status, String orderType);

    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(Long restaurantId, Long tableId, String orderType, Collection<String> statuses);

    Optional<Order> findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(Long restaurantId, Long tableId, String orderType, Collection<String> statuses);

    List<Order> findByRestaurantIdAndCreatedAtBetween(Long restaurantId, LocalDateTime start, LocalDateTime end);

    List<Order> findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long restaurantId, LocalDateTime start, LocalDateTime end);

    List<Order> findByRestaurantIdAndStatusInOrderByCreatedAtAsc(Long restaurantId, Collection<String> statuses);

    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtAsc(Long restaurantId, String status);

    List<Order> findByRestaurantIdAndWaiterIdAndOrderType(Long restaurantId, Long waiterId, String orderType);

    List<Order> findByRestaurantIdAndWaiterIdAndOrderTypeOrderByCreatedAtDesc(Long restaurantId, Long waiterId, String orderType);

    List<Order> findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(Long restaurantId, Long waiterId, String orderType, Collection<String> statuses);

    List<Order> findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusOrderByCreatedAtDesc(Long restaurantId, Long waiterId, String orderType, String status);

    @Query("SELECT o FROM Order o JOIN RestaurantSettings s ON s.restaurant = o.restaurant " +
           "WHERE o.status = 'NEW' AND o.orderType = 'DINE_IN' " +
           "AND o.createdAt <= :cutoffTime AND s.autoAcceptOrders = true")
    List<Order> findEligibleForAutoAccept(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = 'ACCEPTED', o.updatedAt = :now WHERE o.id = :id AND o.status = 'NEW'")
    int updateStatusToAcceptedIfNew(@Param("id") Long id, @Param("now") LocalDateTime now);
}

