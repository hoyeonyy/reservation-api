package com.reservation.service.payment.gateway;

import com.reservation.domain.payment.PaymentMethod;

public record PgPaymentRequest(
        Long userId,
        Long bookingId,
        Long amount,
        PaymentMethod method
) {}
