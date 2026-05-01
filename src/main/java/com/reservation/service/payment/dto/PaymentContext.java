package com.reservation.service.payment.dto;

public record PaymentContext(Long userId, Long bookingId, Long amount) {
}
