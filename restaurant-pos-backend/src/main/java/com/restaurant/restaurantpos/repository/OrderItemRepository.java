package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> findByOrderIdAndRestaurantId(Long orderId, Long restaurantId);

    Optional<OrderItem> findByIdAndRestaurantId(Long id, Long restaurantId);

    void deleteByOrderId(Long orderId);

    @Query("SELECT oi.menuItem.id, oi.menuItem.name, SUM(oi.quantity), SUM(oi.subtotal) " +
           "FROM OrderItem oi " +
           "WHERE oi.order.restaurant.id = :restaurantId " +
           "AND oi.order.status = 'COMPLETED' " +
           "AND oi.order.createdAt >= :start AND oi.order.createdAt < :end " +
           "GROUP BY oi.menuItem.id, oi.menuItem.name " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingItems(@Param("restaurantId") Long restaurantId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    @Query("SELECT oi.menuItem.category.id, oi.menuItem.category.name, SUM(oi.quantity), SUM(oi.subtotal) " +
           "FROM OrderItem oi " +
           "WHERE oi.order.restaurant.id = :restaurantId " +
           "AND oi.order.status = 'COMPLETED' " +
           "AND oi.order.createdAt >= :start AND oi.order.createdAt < :end " +
           "GROUP BY oi.menuItem.category.id, oi.menuItem.category.name " +
           "ORDER BY SUM(oi.subtotal) DESC")
    List<Object[]> findCategorySales(@Param("restaurantId") Long restaurantId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);
}
