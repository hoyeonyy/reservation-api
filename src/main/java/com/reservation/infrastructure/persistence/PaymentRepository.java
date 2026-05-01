package com.reservation.infrastructure.persistence;

import com.reservation.domain.payment.Payment;
import com.reservation.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p WHERE p.booking.id = :bookingId AND p.status = :status")
    List<Payment> findByBookingIdAndStatus(@Param("bookingId") Long bookingId, @Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p JOIN FETCH p.booking b JOIN FETCH b.user JOIN FETCH b.product WHERE p.status = :status")
    List<Payment> findByStatusWithBookingDetails(@Param("status") PaymentStatus status);
}
