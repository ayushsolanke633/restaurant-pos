package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByRestaurantId(Long restaurantId);

    Optional<User> findByIdAndRestaurantId(Long id, Long restaurantId);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Long countByRestaurantId(Long restaurantId);

    Long countByRestaurantIdAndIsActive(Long restaurantId, Boolean isActive);

    Long countByRestaurantIdAndRoleNameIgnoreCase(Long restaurantId, String roleName);

    List<User> findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(Long restaurantId, String roleName);

    boolean existsByRole_Name(String roleName);

    Optional<User> findFirstByRestaurantIdAndRole_NameIgnoreCase(Long restaurantId, String roleName);
}
