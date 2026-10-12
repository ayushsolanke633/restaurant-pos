package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.RestaurantTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RestaurantTable> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RestaurantTable t WHERE t.id = :id")
    Optional<RestaurantTable> findByIdForUpdate(@Param("id") Long id);

    List<RestaurantTable> findByRestaurantId(Long restaurantId);

    List<RestaurantTable> findByRestaurantIdOrderByTableNumberAsc(Long restaurantId);

    Optional<RestaurantTable> findByRestaurantIdAndTableNumber(Long restaurantId, Integer tableNumber);

    Optional<RestaurantTable> findByIdAndRestaurantId(Long id, Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RestaurantTable t WHERE t.id = :id AND t.restaurant.id = :restaurantId")
    Optional<RestaurantTable> findByIdAndRestaurantIdForUpdate(@Param("id") Long id, @Param("restaurantId") Long restaurantId);

    boolean existsByRestaurantIdAndTableNumber(Long restaurantId, Integer tableNumber);

    boolean existsByRestaurantIdAndTableNumberAndIdNot(Long restaurantId, Integer tableNumber, Long id);

    Long countByRestaurantId(Long restaurantId);

    Long countByRestaurantIdAndStatus(Long restaurantId, String status);
}
