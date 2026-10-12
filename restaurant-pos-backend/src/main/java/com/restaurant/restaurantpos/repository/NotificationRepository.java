package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRestaurantIdAndRecipientUserIdOrderByCreatedAtDesc(Long restaurantId, Long recipientUserId);

    List<Notification> findByRestaurantIdAndRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(Long restaurantId, Long recipientUserId);

    long countByRestaurantIdAndRecipientUserIdAndIsReadFalse(Long restaurantId, Long recipientUserId);

    Optional<Notification> findByIdAndRestaurantIdAndRecipientUserId(Long id, Long restaurantId, Long recipientUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.restaurant.id = :restaurantId AND n.recipientUser.id = :recipientUserId AND n.isRead = false")
    int markAllAsRead(@Param("restaurantId") Long restaurantId, @Param("recipientUserId") Long recipientUserId);
}
