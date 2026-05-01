package com.reservation.api.dto.response;

import com.reservation.domain.booking.Booking;
import com.reservation.domain.booking.BookingStatus;

import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId,
        BookingStatus status,
        Long totalAmount,
        LocalDateTime createdAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getCreatedAt()
        );
    }
}
