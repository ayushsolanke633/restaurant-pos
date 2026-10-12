package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByRestaurantId(Long restaurantId);

    Optional<Customer> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndPhone(Long restaurantId, String phone);

    boolean existsByRestaurantIdAndPhoneAndIdNot(Long restaurantId, String phone, Long id);

    @Query("SELECT c FROM Customer c WHERE c.restaurant.id = :restaurantId AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "(c.phone IS NOT NULL AND LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%'))) OR " +
           "(c.email IS NOT NULL AND LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))))")
    List<Customer> searchByRestaurantIdAndQuery(@Param("restaurantId") Long restaurantId, @Param("query") String query);

    Long countByRestaurantId(Long restaurantId);

    Long countByRestaurantIdAndCreatedAtGreaterThanEqual(Long restaurantId, LocalDateTime start);
}
