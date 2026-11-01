package com.drivesphere.repository;

import com.drivesphere.entity.Payment;
import com.drivesphere.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBookingId(Long bookingId);
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);
    Optional<Payment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(Long bookingId, PaymentStatus status);
    List<Payment> findByStatus(PaymentStatus status);
    long countByStatus(PaymentStatus status);
}
