package com.reservation.api.exception;

import com.reservation.domain.payment.PaymentErrorCode;

public class NonRetryablePaymentException extends PaymentFailedException {

    public NonRetryablePaymentException(PaymentErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
