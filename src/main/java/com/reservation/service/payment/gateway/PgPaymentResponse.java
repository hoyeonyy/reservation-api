package com.reservation.service.payment.gateway;

import com.reservation.domain.payment.PaymentErrorCode;

public record PgPaymentResponse(
        boolean success,
        String pgTransactionId,
        PaymentErrorCode errorCode,
        String errorMessage
) {

    public static PgPaymentResponse success(String pgTransactionId) {
        return new PgPaymentResponse(true, pgTransactionId, null, null);
    }

    public static PgPaymentResponse failure(PaymentErrorCode errorCode, String errorMessage) {
        return new PgPaymentResponse(false, null, errorCode, errorMessage);
    }
}
