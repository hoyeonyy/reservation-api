package com.reservation.implement.booking;

import com.reservation.domain.booking.Booking;
import com.reservation.infrastructure.persistence.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BookingValidator {

    private final BookingRepository bookingRepository;

    public Optional<Booking> checkIdempotency(String idempotencyKey) {
        return bookingRepository.findByIdempotencyKey(idempotencyKey);
    }
}
