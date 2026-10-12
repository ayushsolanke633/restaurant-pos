package com.restaurant.restaurantpos.repository;

import com.restaurant.restaurantpos.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByBillIdAndPaymentStatus(Long billId, String paymentStatus);
    List<Payment> findByBillId(Long billId);
    List<Payment> findByBillIdAndRestaurantId(Long billId, Long restaurantId);
    Optional<Payment> findByIdAndBillRestaurantId(Long id, Long restaurantId);
    List<Payment> findByBillRestaurantId(Long restaurantId);
    List<Payment> findByBillRestaurantIdAndPaymentStatusAndPaidAtBetween(Long restaurantId, String paymentStatus, LocalDateTime start, LocalDateTime end);
    List<Payment> findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(Long restaurantId, String paymentStatus, LocalDateTime start, LocalDateTime end);

    Optional<Payment> findByIdAndRestaurantId(Long id, Long restaurantId);
    List<Payment> findByRestaurantId(Long restaurantId);
    List<Payment> findByRestaurantIdAndPaymentStatusAndPaidAtBetween(Long restaurantId, String paymentStatus, LocalDateTime start, LocalDateTime end);
    List<Payment> findByRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(Long restaurantId, String paymentStatus, LocalDateTime start, LocalDateTime end);
}
