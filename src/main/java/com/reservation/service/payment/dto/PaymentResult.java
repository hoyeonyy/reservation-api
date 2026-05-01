package com.reservation.service.payment.dto;

import com.reservation.domain.payment.PaymentMethod;

public record PaymentResult(
        PaymentMethod method,
        Long amount,
        String pgTransactionId
) {
}
