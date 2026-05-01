package com.reservation.event;

import com.reservation.service.payment.dto.PaymentRequest;

import java.util.List;

public record PaymentRequestEvent(
        Long bookingId,
        Long userId,
        Long productId,
        List<PaymentRequest> paymentRequests
) {
}
