package com.reservation.infrastructure.persistence;

import com.reservation.domain.booking.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
